package dev.ember2d.app;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
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
    private AppState state = AppState.CREATED;
    private EngineContext context;
    private GameLoop loop;

    public EmberGame(EngineConfig config) {
        this(config, c -> GdxAssets.load(c.assetManifest));
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
            builder.assets(assets);
            renderer = GdxRenderer.create(config.virtualWidth, config.virtualHeight, config.pixelsPerUnit,
                    config.renderMode, config.backgroundColor, builder.timeState());
            if (renderer != null) {
                builder.renderer(renderer);
            }
            configureContext(builder);
            context = builder.build();
            if (assets.manifest().group(GLOBAL_GROUP) != null) {
                assets.loadGroup(GLOBAL_GROUP).finish();
            }
            loadInputBindings(builder.inputState());
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
        DefaultInput input = builder.inputState();
        if (renderer != null) {
            input.setScreenToWorld(renderer::screenToWorld);
        }
        if (Gdx.graphics != null) {
            input.setDensity(Gdx.graphics.getDensity());   // touch thresholds are in dp
        }
        if (Gdx.app != null) {
            // On Android every finger is reported as mouse button 0: fingers must not drive MOUSE_* bindings.
            input.source().setMouseButtons(Gdx.app.getType() != Application.ApplicationType.Android
                    && Gdx.app.getType() != Application.ApplicationType.iOS);
        }
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(input.source());
            Gdx.input.setCatchKey(Input.Keys.BACK, input.bindings().binds("BACK"));
        }
        EnginePhaseRunner phases = new EnginePhaseRunner(context.timers(), context.tweens(), builder.eventBus(), input,
                assets::update, this::beginRender, this::endRender, context.scenes()::runPhase);
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
        context.forEachLifecycleListener("onSuspend", AppLifecycleListener::onSuspend);
        context.events().publish(new AppSuspended());
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
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(null);
        }
        List<RuntimeException> failures = context.disposeServices();
        if (debugCanvas != null) {
            debugCanvas.dispose();
        }
        if (renderer != null) {
            renderer.dispose();   // engine-owned; after the game's services (reverse boot order)
        }
        assets.dispose();
        LOG.info(failures.isEmpty() ? "Shut down" : "Shut down with " + failures.size() + " disposal failure(s)");
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
        if (debug.isVisible()) {
            debug.renderPanel(debugCanvas, renderer.screenWidth(), renderer.screenHeight());
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
