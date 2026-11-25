package dev.ember2d.app;

import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.Log;
import dev.ember2d.debug.DebugOverlay;
import dev.ember2d.render.Renderer;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.core.Timers;
import dev.ember2d.core.Tweens;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.EventBus;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The service container passed to scenes and systems (F-019). Built once at boot by {@link EmberGame}; there
 * is no global static access (logging excepted).
 *
 * <p>Engine services get typed accessors as they arrive in Phase 1 ({@code time()}, {@code events()},
 * {@code assets()}, ...). Games add their own services in {@link EmberGame#configureContext(Builder)}. A
 * registered service that implements {@link AppLifecycleListener} receives lifecycle callbacks; one that
 * implements libGDX {@link Disposable} is disposed at shutdown, in reverse registration order.
 */
@PublicApi
public final class EngineContext {

    private static final Log LOG = Log.get("core");

    private final EngineConfig config;
    private final TimeState time;
    private final Timers timers;
    private final Tweens tweens;
    private final DefaultEventBus events;
    private final Assets assets;
    private final Renderer renderer;
    private final SceneManager scenes;
    private final DebugOverlay debug;
    private final Map<Class<?>, Object> services;
    private final List<Object> instancesInOrder;

    private EngineContext(EngineConfig config, TimeState time, Timers timers, Tweens tweens, DefaultEventBus events,
            Assets assets, Renderer renderer, SceneManager scenes, DebugOverlay debug, Map<Class<?>, Object> services) {
        this.config = config;
        this.time = time;
        this.timers = timers;
        this.tweens = tweens;
        this.events = events;
        this.assets = assets;
        this.renderer = renderer;
        this.scenes = scenes;
        this.debug = debug;
        this.services = Collections.unmodifiableMap(new LinkedHashMap<>(services));
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Object> ordered = new ArrayList<>();
        for (Object service : services.values()) {
            if (seen.add(service)) {
                ordered.add(service);
            }
        }
        this.instancesInOrder = List.copyOf(ordered);
    }

    /** The configuration the engine was started with. */
    public EngineConfig config() {
        return config;
    }

    /** Frame timing, game time, pause and time scale (F-002). */
    public Time time() {
        return time;
    }

    /** Delayed and repeating actions in game time (F-002). */
    public Timers timers() {
        return timers;
    }

    /** Float property animation in game time (F-002). */
    public Tweens tweens() {
        return tweens;
    }

    /** The event bus: immediate and queued events (F-019). */
    public EventBus events() {
        return events;
    }

    /**
     * Assets by logical ID (F-008).
     *
     * @throws IllegalStateException in a context built without an asset service (tests)
     */
    public Assets assets() {
        if (assets == null) {
            throw new IllegalStateException("This EngineContext has no asset service; set one with Builder.assets(...)");
        }
        return assets;
    }

    /**
     * The world renderer and camera (F-004).
     *
     * @throws IllegalStateException without OpenGL (headless backends) or in a context built without one (tests)
     */
    public Renderer renderer() {
        if (renderer == null) {
            throw new IllegalStateException("This EngineContext has no renderer (headless backend or test context); "
                    + "set one with Builder.renderer(...)");
        }
        return renderer;
    }

    /** The scene stack (F-010). */
    public SceneManager scenes() {
        return scenes;
    }

    /** The debug overlay (F-014); always present, does nothing while hidden. */
    public DebugOverlay debug() {
        return debug;
    }

    /**
     * Returns the service registered for a type.
     *
     * @throws IllegalArgumentException if nothing is registered for the type; the message lists what is
     */
    public <T> T service(Class<T> type) {
        return findService(type).orElseThrow(() -> new IllegalArgumentException(
                "No service registered for " + type.getName() + ". Registered: " + registeredNames()));
    }

    /** Returns the service registered for a type, if any. */
    public <T> Optional<T> findService(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    void forEachLifecycleListener(String callback, Consumer<AppLifecycleListener> action) {
        for (Object service : instancesInOrder) {
            if (service instanceof AppLifecycleListener listener) {
                try {
                    action.accept(listener);
                } catch (RuntimeException e) {
                    LOG.error(callback + " failed in " + service.getClass().getName(), e);
                }
            }
        }
    }

    /** Disposes services in reverse registration order; returns the failures (already logged). */
    List<RuntimeException> disposeServices() {
        List<RuntimeException> failures = new ArrayList<>();
        for (int i = instancesInOrder.size() - 1; i >= 0; i--) {
            if (instancesInOrder.get(i) instanceof Disposable disposable) {
                try {
                    disposable.dispose();
                    LOG.debug("Disposed " + disposable.getClass().getName());
                } catch (RuntimeException e) {
                    LOG.error("Disposing " + disposable.getClass().getName() + " failed; continuing shutdown", e);
                    failures.add(e);
                }
            }
        }
        return failures;
    }

    private String registeredNames() {
        return services.isEmpty() ? "none"
                : services.keySet().stream().map(Class::getName).collect(Collectors.joining(", "));
    }

    /** Builds an {@link EngineContext}; used by {@link EmberGame} and by tests that need a context with fakes. */
    public static final class Builder {

        private final EngineConfig config;
        private final TimeState time;
        private final Timers timers;
        private final Tweens tweens;
        private final DefaultEventBus events;
        private Assets assets;
        private Renderer renderer;
        private final SceneManager scenes;
        private final DebugOverlay debug;
        private final Map<Class<?>, Object> services = new LinkedHashMap<>();

        public Builder(EngineConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            this.time = new TimeState(config.fixedStep);
            this.timers = new Timers(time);
            this.tweens = new Tweens(time);
            this.events = new DefaultEventBus(config.devMode);
            this.scenes = new SceneManager(time, events, config.devMode);
            this.debug = new DebugOverlay(config.devMode || config.debugOverlayInRelease, config.debugOverlayVisibleAtStart);
        }

        /** The time the context will expose; the engine drives it from the game loop. */
        TimeState timeState() {
            return time;
        }

        /** Sets the asset service; the engine sets its own, tests may set a fake. */
        public Builder assets(Assets assets) {
            this.assets = Objects.requireNonNull(assets, "assets");
            return this;
        }

        /** Sets the renderer; the engine sets its own (none on headless backends), tests may set a fake. */
        public Builder renderer(Renderer renderer) {
            this.renderer = Objects.requireNonNull(renderer, "renderer");
            return this;
        }

        /** The engine's event bus implementation, flushed by the engine at the flush points. */
        DefaultEventBus eventBus() {
            return events;
        }

        /**
         * Registers a service under a type. Registration order is the lifecycle callback order; disposal runs
         * in reverse.
         *
         * @throws IllegalArgumentException if the type is already registered or the service is not an
         *     instance of it
         */
        public <T> Builder register(Class<T> type, T service) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(service, "service");
            if (!type.isInstance(service)) {
                throw new IllegalArgumentException(service.getClass().getName() + " is not a " + type.getName());
            }
            if (services.containsKey(type)) {
                throw new IllegalArgumentException("A service is already registered for " + type.getName());
            }
            services.put(type, service);
            return this;
        }

        public EngineContext build() {
            EngineContext context = new EngineContext(config, time, timers, tweens, events, assets, renderer, scenes,
                    debug, services);
            scenes.attach(context);
            return context;
        }
    }
}
