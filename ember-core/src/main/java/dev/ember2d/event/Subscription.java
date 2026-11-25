package dev.ember2d.event;

import dev.ember2d.core.PublicApi;

/** A handler registration on the {@link EventBus}. Cancelling is idempotent. */
@PublicApi
public interface Subscription {

    /** Removes the handler; it is never called again, even for an event already being delivered. */
    void cancel();

    /** Whether the handler is still registered. */
    boolean isActive();
}
