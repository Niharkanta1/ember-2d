package dev.ember2d.core;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.GdxClock;
import java.util.List;
import java.util.Objects;

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

    private final EngineConfig config;
    private AppState state = AppState.CREATED;
    private EngineContext context;
    private GameLoop loop;

    public EmberGame(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "config");
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
        configureContext(builder);
        context = builder.build();
        loop = new GameLoop(new GdxClock(), builder.timeState(), this::runPhase, config.maxCatchUpSteps);
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

    private void runPhase(Phase phase) {
        // Systems and scenes plug in here with the ECS (I-040) and scene manager (I-070).
        if (phase == Phase.RENDER && Gdx.gl != null) {
            // Placeholder until the renderer (I-060). No GL on headless backends.
            ScreenUtils.clear(0f, 0f, 0f, 1f);
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
