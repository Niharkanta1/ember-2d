package dev.ember2d.core;

/**
 * Refers to one scheduled timer. Safe to keep after the timer has finished: {@link #cancel()} on a finished
 * or already cancelled timer does nothing, even if its internal slot has been reused.
 */
@PublicApi
public final class TimerHandle {

    private final Timers owner;
    final Timers.Entry entry;
    final int generation;

    TimerHandle(Timers owner, Timers.Entry entry) {
        this.owner = owner;
        this.entry = entry;
        this.generation = entry.generation;
    }

    /** Whether the timer is still scheduled (not fired for a one-shot, not cancelled). */
    public boolean isActive() {
        return entry.generation == generation && entry.active;
    }

    /** Cancels the timer; a no-op if it is no longer active. */
    public void cancel() {
        owner.cancel(this);
    }
}
