package dev.ember2d.app;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.audio.internal.GdxAudio;
import dev.ember2d.camera.CameraRig;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.GdxClock;
import dev.ember2d.debug.DebugOverlay;
import dev.ember2d.debug.internal.GdxDebugCanvas;
import dev.ember2d.event.AppResumed;
import dev.ember2d.event.AppSuspended;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.internal.DefaultInput;
import dev.ember2d.render.internal.GdxRenderer;
import dev.ember2d.save.SaveStore;
import dev.ember2d.save.internal.FileSaveStore;
import dev.ember2d.save.internal.SaveFolders;
import dev.ember2d.render.internal.ScreenLayout;
import dev.ember2d.ui.Insets;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The single entry point of an Ember2D game (F-001): a libGDX {@link ApplicationListener} that boots the
 * engine, owns the application lifecycle, builds the {@link EngineContext} and shuts down cleanly.
 *
 * <p>Games configure it rather than override its loop: pass an {@link EngineConfig} and, to add game
 * services, override {@link #configureContext(EngineContext.Builder)}. The lifecycle methods are final.
 *
 * <p>State changes follow {@code CREATED -> RUNNING <-> SUSPENDED -> DISPOSED}. A repeated suspend or resume
 * is ignored (some backends report them twice); any other unexpected call throws
 * {@link IllegalStateException}. {@code dispose()} after shutdown does nothing.
 */
@PublicApi
public class EmberGame implements ApplicationListener {

    private static final Log LOG = Log.get("core");
    /** The asset group loaded at boot (F-008). */
    static final String GLOBAL_GROUP = "global";

    private final EngineConfig config;
    private final Function<EngineConfig, GdxAssets> assetsFactory;
    private GdxAssets assets;
    private GdxRenderer renderer;
    private GdxDebugCanvas debugCanvas;
    private TouchControlsOverlay controlsOverlay;
    private AppState state = AppState.CREATED;
    private EngineContext context;
    private GameLoop loop;
    private HotReload hotReload;
    private GdxAudio audio;
    private int hotReloadResets;

    public EmberGame(EngineConfig config) {
        this(config, c -> c.devMode && c.assetsDir != null ? GdxAssets.load(c.assetManifest, c.assetsDir)
                : GdxAssets.load(c.assetManifest));
    }

    /** For tests: supplies the asset service instead of reading the manifest from the game's assets. */
    EmberGame(EngineConfig config, Function<EngineConfig, GdxAssets> assetsFactory) {
        this.config = Objects.requireNonNull(config, "config");
        this.assetsFactory = Objects.requireNonNull(assetsFactory, "assetsFactory");
    }

    /** Adds game services to the context. Called once during {@code create()}. */
    protected void configureContext(EngineContext.Builder builder) {
    }

    /**
     * The engine context.
     *
     * @throws IllegalStateException before {@code create()} has completed
     */
    public final EngineContext context() {
        if (context == null) {
            throw new IllegalStateException("The EngineContext is available after create(); state is " + state);
        }
        return context;
    }

    public final AppState state() {
        return state;
    }

    @Override
    public final void create() {
        requireState("create", AppState.CREATED);
        long start = System.nanoTime();
        config.validate();
        LOG.info("Starting Ember2D: " + config);
        EngineContext.Builder builder = new EngineContext.Builder(config);
        assets = assetsFactory.apply(config);
        try {
            assets.setTiledProject(config.tiledProject);
            builder.assets(assets);
            renderer = GdxRenderer.create(config.virtualWidth, config.virtualHeight, config.pixelsPerUnit,
                    config.renderMode, config.backgroundColor, builder.timeState());
            if (renderer != null) {
                builder.renderer(renderer);
            }
            builder.saves(createSaveStore());   // a game may replace it in configureContext
            configureContext(builder);
            context = builder.build();
            if (assets.manifest().group(GLOBAL_GROUP) != null) {
                assets.loadGroup(GLOBAL_GROUP).finish();
            }
            loadInputBindings(builder.inputState());
            EngineSettings.apply(context);   // stored volumes, controls and keys, before the first scene (I-230)
        } catch (RuntimeException e) {
            if (renderer != null) {
                renderer.dispose();
                renderer = null;
            }
            assets.dispose();
            assets = null;
            context = null;
            throw e;
        }
        if (renderer != null) {
            DefaultInput uiInput = builder.inputState();
            context.scenes().setUiFactory(() -> {
                Ui ui = new Ui(renderer.batch(), uiInput);
                layoutCurrent(ui);   // sized before the scene builds its widgets (dp sizes need it)
                return ui;
            });
        }
        if (context.audio() instanceof GdxAudio engineAudio) {
            audio = engineAudio;
            for (AssetManifest.Entry entry : assets.manifest().entries()) {
                if (entry.maxInstances() > 0) {
                    audio.setLimit(entry.id(), entry.maxInstances());
                }
            }
            assets.setBeforeRelease(audio::release);
        }
        DefaultInput input = builder.inputState();
        if (renderer != null) {
            input.setScreenToWorld(renderer::screenToWorld);
        }
        if (Gdx.graphics != null) {
            input.setDensity(Gdx.graphics.getDensity());   // touch thresholds are in dp
        }
        boolean touchDevice = Gdx.app != null && (Gdx.app.getType() == Application.ApplicationType.Android
                || Gdx.app.getType() == Application.ApplicationType.iOS);
        input.setAutoHide(config.touchControls == TouchControlsMode.AUTO);
        input.setTouchControlsVisible(config.touchControls == TouchControlsMode.ALWAYS
                || (config.touchControls == TouchControlsMode.AUTO && touchDevice));
        if (Gdx.graphics != null) {
            input.setScreenSize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        }
        if (Gdx.app != null) {
            // On Android every finger is reported as mouse button 0: fingers must not drive MOUSE_* bindings.
            input.source().setMouseButtons(Gdx.app.getType() != Application.ApplicationType.Android
                    && Gdx.app.getType() != Application.ApplicationType.iOS);
        }
        if (Gdx.input != null) {
            // The top scene's UI gets pointer and key events first; what it does not handle goes to the game (F-012).
            Gdx.input.setInputProcessor(new InputMultiplexer(new UiInputRouter(context.scenes()), input.source()));
            Gdx.input.setCatchKey(Input.Keys.BACK, input.bindings().binds("BACK"));
        }
        if (config.devMode && config.assetWatcher != null) {
            hotReload = new HotReload(config.assetWatcher, assets, context.scenes(), builder.eventBus(), input,
                    config.inputBindings);
            LOG.info("Hot reload on: watching " + config.assetsDir);
        }
        EnginePhaseRunner phases = new EnginePhaseRunner(context.timers(), context.tweens(), builder.eventBus(), input,
                this::updateAssets, this::beginRender, this::endRender, context.scenes()::runPhase);
        loop = new GameLoop(new GdxClock(), builder.timeState(), phases, config.maxCatchUpSteps);
        if (config.firstScene != null) {
            context.scenes().push(Objects.requireNonNull(config.firstScene.get(), "EngineConfig.firstScene returned null"));
            context.scenes().applyPending();   // enter now, so a broken first scene fails at boot
        }
        loop.resetAccumulator();   // the first frame is long (boot); do not catch up on it
        state = AppState.RUNNING;
        if (config.devMode) {
            LOG.info("Boot took " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
    }

    @Override
    public final void resize(int width, int height) {
        requireStarted("resize");
        if (renderer != null) {
            // Pixel-perfect layout works in real pixels (the back buffer), which can differ from the logical size.
            renderer.resize(Gdx.graphics != null ? Gdx.graphics.getBackBufferWidth() : width,
                    Gdx.graphics != null ? Gdx.graphics.getBackBufferHeight() : height);
        }
        ((DefaultInput) context.input()).setScreenSize(width, height);   // touch coordinates use the logical size
        updateSafeInsets();
        context.forEachLifecycleListener("onResize", listener -> listener.onResize(width, height));
    }

    @Override
    public final void render() {
        requireStarted("render");
        if (state != AppState.RUNNING) {
            return;
        }
        DebugOverlay debug = context.debug();
        if (debug.isAvailable() && Gdx.input != null && Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debug.toggle();   // a raw key on purpose: works without bindings (F-005 keeps it out of the actions)
        }
        long start = System.nanoTime();
        try {
            loop.frame();
        } catch (RuntimeException e) {
            // Dev and release both rethrow: in release the platform crash handler writes the crash log
            // (desktop CrashLog). A fatal error screen (F-001) is not implemented yet.
            LOG.error("Uncaught exception in frame", e);
            throw e;
        }
        if (debug.isVisible()) {
            Scene scene = context.scenes().current();
            debug.recordFrame(context.time().unscaledDelta(), System.nanoTime() - start, context.time().fixedTick(),
                    context.time().droppedFixedSteps(), scene == null ? 0 : scene.worldInternal().entityCount(),
                    renderer == null ? 0 : renderer.renderCallsLastFrame());
        }
    }

    @Override
    public final void pause() {
        requireStarted("pause");
        if (state == AppState.SUSPENDED) {
            LOG.debug("pause() while already suspended; ignored");
            return;
        }
        state = AppState.SUSPENDED;
        LOG.info("Suspended");
        context.input().releaseAll();   // no stuck keys: key-up events can be lost while suspended
        if (audio != null) {
            audio.suspend();
        }
        context.forEachLifecycleListener("onSuspend", AppLifecycleListener::onSuspend);
        context.events().publish(new AppSuspended());
        try {
            EngineSettings.capture(context);   // Android may kill a suspended app: write settings and saves now
            if (context.saves() instanceof FileSaveStore store) {
                store.onSuspend();
            } else {
                context.saves().flush();
            }
        } catch (RuntimeException e) {
            LOG.error("Saving on suspend failed", e);
        }
    }

    @Override
    public final void resume() {
        requireStarted("resume");
        if (state == AppState.RUNNING) {
            LOG.debug("resume() while already running; ignored");
            return;
        }
        state = AppState.RUNNING;
        loop.resetAccumulator();
        LOG.info("Resumed");
        if (audio != null) {
            audio.resume();
        }
        context.forEachLifecycleListener("onResume", AppLifecycleListener::onResume);
        context.events().publish(new AppResumed());
    }

    @Override
    public final void dispose() {
        if (state == AppState.DISPOSED) {
            return;
        }
        AppState previous = state;
        state = AppState.DISPOSED;
        if (previous == AppState.CREATED) {
            return; // never started: nothing to release
        }
        context.scenes().exitAll();
        try {
            context.saveSettings();
        } catch (RuntimeException e) {
            LOG.error("Saving settings at shutdown failed", e);
        }
        if (audio != null) {
            audio.stopAll();
        }
        if (config.devMode && config.assetWatcher != null) {
            config.assetWatcher.close();
        }
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(null);
        }
        List<RuntimeException> failures = context.disposeServices();
        if (debugCanvas != null) {
            debugCanvas.dispose();
        }
        if (controlsOverlay != null) {
            controlsOverlay.dispose();
        }
        if (renderer != null) {
            renderer.dispose();   // engine-owned; after the game's services (reverse boot order)
        }
        assets.dispose();
        LOG.info(failures.isEmpty() ? "Shut down" : "Shut down with " + failures.size() + " disposal failure(s)");
    }

    /** Saves on disk when {@link EngineConfig#saveFolder} is set and a libGDX backend runs, otherwise in memory. */
    private SaveStore createSaveStore() {
        FileSaveStore store = new FileSaveStore(SaveFolders.forGame(config.saveFolder), config.saveSlots, config.saveVersion, System::currentTimeMillis);
        LOG.info("Saves in " + store.location());
        return store;
    }

    /** Loads and validates {@link EngineConfig#inputBindings} (already loaded with the global group if listed there). */
    private void loadInputBindings(DefaultInput input) {
        String id = config.inputBindings;
        if (id == null) {
            return;
        }
        AssetManifest.Entry entry = assets.manifest().find(id);
        if (entry == null) {
            throw new EngineConfigException(List.of("inputBindings: asset ID \"" + id
                    + "\" is not in the asset manifest " + assets.manifest().source()));
        }
        if (entry.type() != AssetManifest.Type.JSON) {
            throw new EngineConfigException(List.of("inputBindings: asset \"" + id + "\" must be of type json (is "
                    + entry.type() + ")"));
        }
        input.setBindings(InputBindings.parse(assets.get(id, JsonValue.class), entry.path()));
        LOG.debug("Input bindings " + entry.path() + ": " + input.bindings().actions().size() + " action(s), "
                + input.bindings().axes().size() + " axis/axes, contexts " + input.bindings().contexts());
    }

    private void updateAssets() {
        assets.update();
        if (audio != null) {
            audio.update(context.time().unscaledDelta());   // fades run on real time
            if (renderer != null) {
                CameraRig camera = renderer.camera();
                audio.setListener(camera.position.x, camera.position.y, camera.viewWidth() / 2f,
                        camera.viewHeight() / 2f);
            }
        }
        if (hotReload == null) {
            return;
        }
        if (hotReload.step()) {
            hotReloadResets = 2;   // this frame and the next (a scene restart) are long: do not catch up on them
        }
        if (hotReloadResets > 0) {
            hotReloadResets--;
            loop.resetAccumulator();
        }
    }

    private void beginRender() {
        if (renderer != null) {
            renderer.beginFrame();
        }
    }

    private void endRender() {
        if (renderer == null) {
            return;
        }
        DebugOverlay debug = context.debug();
        if (debug.isVisible() && debugCanvas == null) {
            debugCanvas = new GdxDebugCanvas(renderer.batch());   // created on first use: zero cost until shown
        }
        if (debug.isVisible()) {
            renderer.batch().end();
            debug.renderWorld(debugCanvas, renderer.worldCamera().combined);
            renderer.batch().begin();
        }
        renderer.endFrame();
        drawUi();
        if (debug.isVisible()) {
            debug.renderPanel(debugCanvas, renderer.screenWidth(), renderer.screenHeight());
        }
        DefaultInput input = (DefaultInput) context.input();
        if (input.touchControlsActive() && Gdx.graphics != null) {
            if (controlsOverlay == null) {
                Skin custom = config.touchControlsSkin == null ? null
                        : context.assets().get(config.touchControlsSkin, Skin.class);
                controlsOverlay = new TouchControlsOverlay(custom);   // created on first use
            }
            controlsOverlay.draw(input.touchControls(), renderer.batch(), Gdx.graphics.getWidth(),
                    Gdx.graphics.getHeight());
        }
    }

    /** The top scene's UI, if it has one: laid out, advanced on real time and drawn above the world (F-012). */
    private void drawUi() {
        Ui ui = context.scenes().currentUi();
        if (ui == null) {
            return;
        }
        layoutCurrent(ui);
        ui.act(context.time().unscaledDelta());
        renderer.batch().setColor(Color.WHITE);
        ui.draw();
    }

    private void layoutCurrent(Ui ui) {
        Rectangle safe = renderer.safeArea(new Rectangle());
        Insets insets = new Insets(safe.x, renderer.screenHeight() - safe.y - safe.height,
                renderer.screenWidth() - safe.x - safe.width, safe.y);
        layoutUi(ui, config, renderer.layout(), renderer.screenWidth(), renderer.screenHeight(), insets,
                Gdx.graphics != null ? Gdx.graphics.getDensity() : 1f);
    }

    /** Reads the safe-area insets (or the dev simulation) and passes them to the renderer and the controls. */
    private void updateSafeInsets() {
        Insets px = Insets.NONE;
        if (config.devMode && config.simulatedSafeInsets != null) {
            px = config.simulatedSafeInsets;
        } else if (Gdx.graphics != null) {
            px = new Insets(Gdx.graphics.getSafeInsetLeft(), Gdx.graphics.getSafeInsetTop(),
                    Gdx.graphics.getSafeInsetRight(), Gdx.graphics.getSafeInsetBottom());
        }
        if (renderer != null) {
            renderer.setSafeInsets((int) px.left(), (int) px.top(), (int) px.right(), (int) px.bottom());
        }
        ((DefaultInput) context.input()).setSafeInsets(px.left(), px.top(), px.right(), px.bottom());
    }

    /**
     * {@link UiMode#VIRTUAL}: the world's virtual size in the world's screen rectangle (same integer scale, same bars).
     * {@link UiMode#SCREEN}: the whole window in screen pixels divided by {@code uiScale} (0: the density, at least 1).
     * The safe-area insets (window pixels) become UI units; in {@code VIRTUAL} the black bars already count towards
     * them.
     */
    static void layoutUi(Ui ui, EngineConfig config, ScreenLayout layout, int screenWidth, int screenHeight,
            Insets insetsPx, float density) {
        float pixelsPerDp = density > 0f ? density : 1f;
        if (config.uiMode == UiMode.VIRTUAL) {
            float scale = layout.width() / (float) layout.worldWidth();
            ui.layout(layout.worldWidth(), layout.worldHeight(), layout.x(), layout.y(), layout.width(),
                    layout.height());
            int barRight = screenWidth - layout.x() - layout.width();
            int barTop = screenHeight - layout.y() - layout.height();
            Insets insets = new Insets((insetsPx.left() - layout.x()) / scale, (insetsPx.top() - barTop) / scale,
                    (insetsPx.right() - barRight) / scale, (insetsPx.bottom() - layout.y()) / scale);
            ui.setScreenMetrics(insets, pixelsPerDp, scale);
        } else {
            float scale = config.uiScale > 0f ? config.uiScale : Math.max(1f, pixelsPerDp);
            ui.layout(screenWidth / scale, screenHeight / scale, 0, 0, screenWidth, screenHeight);
            ui.setScreenMetrics(new Insets(insetsPx.left() / scale, insetsPx.top() / scale,
                    insetsPx.right() / scale, insetsPx.bottom() / scale), pixelsPerDp, scale);
        }
    }

    private void requireState(String call, AppState expected) {
        if (state != expected) {
            throw new IllegalStateException(call + "() is only allowed in state " + expected + "; state is " + state);
        }
    }

    private void requireStarted(String call) {
        if (state == AppState.CREATED || state == AppState.DISPOSED) {
            throw new IllegalStateException(call + "() is only allowed after create() and before dispose(); state is "
                    + state);
        }
    }
}
