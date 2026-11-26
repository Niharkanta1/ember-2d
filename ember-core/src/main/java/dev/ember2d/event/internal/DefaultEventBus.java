package dev.ember2d.event.internal;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SnapshotArray;
import dev.ember2d.core.Log;
import dev.ember2d.event.EventBus;
import dev.ember2d.event.Subscription;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The engine's {@link EventBus}. The engine calls {@link #beginFrame()} once per frame and {@link #flush()} at
 * the flush points; the scene manager (I-070) uses {@link #openScope()} and {@link #closeScope(Scope)}.
 *
 * <p>Handlers are stored per exact event class in snapshot arrays, so subscribing or cancelling during
 * delivery is safe. The queue is double-buffered. Delivery does not allocate.
 */
public final class DefaultEventBus implements EventBus {

    /** Passes per flush for events queued while flushing; the rest wait for the next flush point. */
    public static final int MAX_FLUSH_PASSES = 8;

    private static final Log LOG = Log.get("events");

    /** A group of subscriptions cancelled together (a scene's subscriptions). */
    public static final class Scope {
        private final Array<Handler> handlers = new Array<>(false, 8);
        private boolean closed;

        public boolean isClosed() {
            return closed;
        }
    }

    /** Handler list key of the monitors (never an event type). */
    private static final class MonitorKey {
    }

    private final class Handler implements Subscription {
        final Class<?> type;
        final Consumer<Object> consumer;
        final Scope scope;
        boolean cancelled;

        Handler(Class<?> type, Consumer<Object> consumer, Scope scope) {
            this.type = type;
            this.consumer = consumer;
            this.scope = scope;
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            SnapshotArray<Handler> list = handlers.get(type);
            if (list != null) {
                list.removeValue(this, true);
            }
            if (scope != null && !scope.closed) {
                scope.handlers.removeValue(this, true);
            }
        }

        @Override
        public boolean isActive() {
            return !cancelled;
        }
    }

    private final boolean devMode;
    private final ObjectMap<Class<?>, SnapshotArray<Handler>> handlers = new ObjectMap<>();
    private final Array<Scope> scopes = new Array<>(false, 4);
    private Array<Object> queued = new Array<>(false, 32);
    private Array<Object> delivering = new Array<>(false, 32);
    private int depth;
    private RuntimeException pendingFailure;
    private boolean failureRethrownThisFrame;

    public DefaultEventBus(boolean devMode) {
        this.devMode = devMode;
    }

    @Override
    public <E> Subscription on(Class<E> type, Consumer<E> handler) {
        return register(type, handler, scopes.isEmpty() ? null : scopes.peek());
    }

    @Override
    public <E> Subscription onGlobal(Class<E> type, Consumer<E> handler) {
        return register(type, handler, null);
    }

    @Override
    public Subscription monitor(Consumer<Object> monitor) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        Class<Object> key = (Class) MonitorKey.class;
        return register(key, monitor, null);
    }

    @Override
    public void publish(Object event) {
        Objects.requireNonNull(event, "event");
        depth++;
        try {
            deliver(event);
        } finally {
            depth--;
        }
        rethrowIfDone();
    }

    @Override
    public void queue(Object event) {
        queued.add(Objects.requireNonNull(event, "event"));
    }

    /** Starts a frame: resets the "first handler failure per frame" rule for dev mode. */
    public void beginFrame() {
        failureRethrownThisFrame = false;
    }

    /** Delivers queued events, including events queued meanwhile, up to {@link #MAX_FLUSH_PASSES} passes. */
    public void flush() {
        depth++;
        try {
            for (int pass = 0; pass < MAX_FLUSH_PASSES && queued.size > 0; pass++) {
                Array<Object> batch = queued;
                queued = delivering;
                delivering = batch;
                for (int i = 0; i < batch.size; i++) {
                    deliver(batch.get(i));
                }
                batch.clear();
            }
            if (queued.size > 0) {
                LOG.warn(queued.size + " queued event(s) deferred to the next flush point: events kept queuing more "
                        + "events for " + MAX_FLUSH_PASSES + " passes (first: " + queued.first().getClass().getName() + ")");
            }
        } finally {
            depth--;
        }
        rethrowIfDone();
    }

    /** Opens a scope; {@link #on(Class, Consumer)} registers into the innermost open scope. */
    public Scope openScope() {
        Scope scope = new Scope();
        scopes.add(scope);
        return scope;
    }

    /** Cancels every subscription made in the scope and closes it. */
    public void closeScope(Scope scope) {
        if (scope.closed) {
            return;
        }
        scope.closed = true;
        scopes.removeValue(scope, true);
        for (Handler handler : scope.handlers) {
            handler.cancel();
        }
        scope.handlers.clear();
    }

    /** Number of queued, undelivered events (tests and debug). */
    public int queuedCount() {
        return queued.size;
    }

    @SuppressWarnings("unchecked")
    private <E> Subscription register(Class<E> type, Consumer<E> consumer, Scope scope) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(consumer, "handler");
        Handler handler = new Handler(type, (Consumer<Object>) consumer, scope);
        SnapshotArray<Handler> list = handlers.get(type);
        if (list == null) {
            list = new SnapshotArray<>(true, 4, Handler[]::new);
            handlers.put(type, list);
        }
        list.add(handler);
        if (scope != null) {
            scope.handlers.add(handler);
        }
        return handler;
    }

    private void deliver(Object event) {
        SnapshotArray<Handler> monitors = handlers.get(MonitorKey.class);
        if (monitors != null && monitors.size > 0) {
            Handler[] items = monitors.begin();
            try {
                for (int i = 0, n = monitors.size; i < n; i++) {
                    if (!items[i].cancelled) {
                        try {
                            items[i].consumer.accept(event);
                        } catch (RuntimeException e) {
                            LOG.error("Event monitor failed for " + event.getClass().getName(), e);
                        }
                    }
                }
            } finally {
                monitors.end();
            }
        }
        SnapshotArray<Handler> list = handlers.get(event.getClass());
        if (list == null) {
            return;
        }
        Handler[] items = list.begin();
        try {
            for (int i = 0, n = list.size; i < n; i++) {
                Handler handler = items[i];
                if (handler.cancelled) {
                    continue;
                }
                try {
                    handler.consumer.accept(event);
                } catch (RuntimeException e) {
                    LOG.error("Event handler " + handler.consumer.getClass().getName() + " failed for "
                            + event.getClass().getName(), e);
                    if (devMode && !failureRethrownThisFrame && pendingFailure == null) {
                        pendingFailure = e;
                    }
                }
            }
        } finally {
            list.end();
        }
    }

    private void rethrowIfDone() {
        if (depth == 0 && pendingFailure != null) {
            RuntimeException failure = pendingFailure;
            pendingFailure = null;
            failureRethrownThisFrame = true;
            throw failure;
        }
    }
}
