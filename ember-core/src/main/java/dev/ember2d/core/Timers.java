package dev.ember2d.core;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import java.util.Objects;

/**
 * Delayed and repeating actions in game time (F-002), available as {@code context.timers()}.
 *
 * <p>Timers advance at the start of each frame's {@link Phase#UPDATE} phase with scaled time, so they freeze
 * while the game is paused or at time scale 0. Due timers fire in due-time order, then creation order. A
 * repeating timer fires once per elapsed interval, keeping its rhythm (several times in one long frame).
 * Scheduling or cancelling from inside a timer action is safe; a timer scheduled during an update first runs
 * on the next frame. Entries are pooled; per-frame updates do not allocate.
 */
@PublicApi
public final class Timers {

    /** Tolerance (1 microsecond) when comparing a due time with game time; absorbs float rounding of delays. */
    private static final double DUE_EPSILON = 1e-6;

    static final class Entry implements Pool.Poolable {
        Runnable action;
        double due;
        double interval;
        long sequence;
        long createdOnUpdate;
        boolean active;
        int generation;

        @Override
        public void reset() {
            action = null;
            active = false;
            generation++;
        }
    }

    private final Time time;
    private final Array<Entry> entries = new Array<>(false, 16);
    private final Pool<Entry> pool = new Pool<>(16) {
        @Override
        protected Entry newObject() {
            return new Entry();
        }
    };
    private long nextSequence;
    private long updateCount;
    private boolean updating;

    public Timers(Time time) {
        this.time = Objects.requireNonNull(time, "time");
    }

    /**
     * Runs an action once after a delay of game time.
     *
     * @param seconds 0 or more; 0 fires on the next update
     */
    public TimerHandle after(float seconds, Runnable action) {
        requireDelay(seconds, false);
        return schedule(seconds, 0d, action);
    }

    /**
     * Runs an action every interval of game time, first after one interval, until cancelled.
     *
     * @param seconds greater than 0
     */
    public TimerHandle every(float seconds, Runnable action) {
        requireDelay(seconds, true);
        return schedule(seconds, seconds, action);
    }

    /** Cancels a timer; a no-op for a finished, cancelled or foreign handle. */
    public void cancel(TimerHandle handle) {
        if (handle != null && handle.isActive()) {
            handle.entry.active = false;
            if (!updating) {
                compact();
            }
        }
    }

    /** Cancels every scheduled timer. */
    public void cancelAll() {
        for (Entry entry : entries) {
            entry.active = false;
        }
        if (!updating) {
            compact();
        }
    }

    /** Number of scheduled timers (tests and debug). */
    int size() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.active) {
                count++;
            }
        }
        return count;
    }

    /** Fires every due timer. Called by the engine at the start of {@link Phase#UPDATE}. */
    void update() {
        updateCount++;
        updating = true;
        try {
            double now = time.elapsed() + DUE_EPSILON;
            Entry next;
            while ((next = nextDue(now)) != null) {
                if (next.interval > 0d) {
                    next.due += next.interval;
                } else {
                    next.active = false;
                }
                next.action.run();
            }
        } finally {
            updating = false;
            compact();
        }
    }

    int pooledFree() {
        return pool.getFree();
    }

    private Entry nextDue(double now) {
        Entry best = null;
        for (int i = 0; i < entries.size; i++) {
            Entry entry = entries.get(i);
            if (entry.active && entry.createdOnUpdate != updateCount && entry.due <= now
                    && (best == null || entry.due < best.due || (entry.due == best.due && entry.sequence < best.sequence))) {
                best = entry;
            }
        }
        return best;
    }

    private TimerHandle schedule(float seconds, double interval, Runnable action) {
        Objects.requireNonNull(action, "action");
        Entry entry = pool.obtain();
        entry.action = action;
        entry.due = time.elapsed() + seconds;
        entry.interval = interval;
        entry.sequence = nextSequence++;
        entry.createdOnUpdate = updating ? updateCount : -1L;
        entry.active = true;
        entries.add(entry);
        return new TimerHandle(this, entry);
    }

    private void compact() {
        for (int i = entries.size - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (!entry.active) {
                entries.removeIndex(i);
                pool.free(entry);
            }
        }
    }

    private static void requireDelay(float seconds, boolean positive) {
        boolean valid = Float.isFinite(seconds) && (positive ? seconds > 0f : seconds >= 0f);
        if (!valid) {
            throw new IllegalArgumentException("seconds must be " + (positive ? "> 0" : ">= 0")
                    + " and finite (was " + seconds + ")");
        }
    }
}
