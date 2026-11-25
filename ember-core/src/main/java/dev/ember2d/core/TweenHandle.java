package dev.ember2d.core;

/**
 * Refers to one running tween. Safe to keep after the tween has finished: {@link #cancel()} then does nothing,
 * even if its internal slot has been reused.
 */
@PublicApi
public final class TweenHandle {

    private final Tweens owner;
    final Tweens.Entry entry;
    final int generation;

    TweenHandle(Tweens owner, Tweens.Entry entry) {
        this.owner = owner;
        this.entry = entry;
        this.generation = entry.generation;
    }

    /** Whether the tween is still running (not finished, not cancelled). */
    public boolean isActive() {
        return entry.generation == generation && entry.active;
    }

    /** Stops the tween where it is; its completion action does not run. A no-op if it is no longer active. */
    public void cancel() {
        owner.cancel(this);
    }

    /**
     * Runs an action when the tween reaches its target (not when cancelled). Replaces a previously set action.
     * Ignored if the tween is no longer active.
     */
    public TweenHandle then(Runnable onComplete) {
        if (isActive()) {
            entry.onComplete = onComplete;
        }
        return this;
    }
}
