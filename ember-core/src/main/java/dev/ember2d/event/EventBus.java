package dev.ember2d.event;

import dev.ember2d.core.PublicApi;
import java.util.function.Consumer;

/**
 * The engine's single messaging model (F-019), available as {@code context.events()}.
 *
 * <ul>
 *   <li><b>Immediate</b> ({@link #publish(Object)}): handlers run before {@code publish} returns. For UI and
 *       input.</li>
 *   <li><b>Queued</b> ({@link #queue(Object)}): delivered at the next flush point, in publish order. Flush
 *       points are after each fixed step, after {@code UPDATE} and after {@code LATE}. Events queued during
 *       {@code INPUT} reach the first flush of the frame; events queued during {@code RENDER} reach the next
 *       frame. For gameplay.</li>
 * </ul>
 *
 * <p>Handlers are matched by the event's exact class and run in subscription order. A handler exception is
 * logged with the event type and handler class and does not stop other handlers; in dev mode the first one per
 * frame is rethrown once delivery finishes. Events queued during a flush are delivered in the same flush, up to
 * 8 passes; the rest wait for the next flush point and a warning is logged.
 */
@PublicApi
public interface EventBus {

    /**
     * Subscribes to events of exactly this class. When called while a scene is active, the subscription is
     * cancelled automatically when the scene exits (scene manager, I-070); otherwise it behaves like
     * {@link #onGlobal(Class, Consumer)}.
     */
    <E> Subscription on(Class<E> type, Consumer<E> handler);

    /** Subscribes to events of exactly this class until cancelled. */
    <E> Subscription onGlobal(Class<E> type, Consumer<E> handler);

    /** Delivers an event to its handlers now. */
    void publish(Object event);

    /** Delivers an event at the next flush point. */
    void queue(Object event);
}
