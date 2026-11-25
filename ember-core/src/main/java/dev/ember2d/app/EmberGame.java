package dev.ember2d.app;

import com.badlogic.gdx.ApplicationListener;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.GdxClock;
import dev.ember2d.event.AppResumed;
import dev.ember2d.event.AppSuspended;
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
            configureContext(builder);
            context = builder.build();
            if (assets.manifest().group(GLOBAL_GROUP) != null) {
                assets.loadGroup(GLOBAL_GROUP).finish();
            }
        } catch (RuntimeException e) {
            assets.dispose();
            assets = null;
            context = null;
            throw e;
        }
        EnginePhaseRunner phases = new EnginePhaseRunner(context.timers(), context.tweens(), builder.eventBus(),
                assets::update, phase -> { });
        loop = new GameLoop(new GdxClock(), builder.timeState(), phases, config.maxCatchUpSteps);
        state = AppState.RUNNING;
        if (config.devMode) {
            LOG.info("Boot took " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
    }

    @Override
    public final void resize(int width, int height) {
        requireStarted("resize");
        context.forEachLifecycleListener("onResize", listener -> listener.onResize(width, height));
    }

    @Override
    public final void render() {
        requireStarted("render");
        if (state != AppState.RUNNING) {
            return;
        }
        try {
            loop.frame();
        } catch (RuntimeException e) {
            // Dev and release both rethrow: in release the platform crash handler writes the crash log
            // (desktop CrashLog). A fatal error screen arrives with the renderer (I-060).
            LOG.error("Uncaught exception in frame", e);
            throw e;
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
        List<RuntimeException> failures = context.disposeServices();
        assets.dispose();   // engine-owned; after the game's services (reverse boot order)
        LOG.info(failures.isEmpty() ? "Shut down" : "Shut down with " + failures.size() + " disposal failure(s)");
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
