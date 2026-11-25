package dev.ember2d.core;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import java.util.Objects;

/**
 * Animates float properties over game time (F-002), available as {@code context.tweens()}.
 *
 * <pre>{@code
 * tweens.to(() -> sprite.alpha, a -> sprite.alpha = a, 0f, 0.5f, Interpolation.fade)
 *       .then(() -> scenes.replace(new TitleScene()));
 * }</pre>
 *
 * <p>Tweens advance at the start of each frame's {@link Phase#UPDATE} phase, after timers, with scaled time,
 * so they freeze while the game is paused. The start value is read when the tween is created; the final frame
 * sets exactly the target, then runs the completion action. A tween created during an update first advances on
 * the next frame. Entries are pooled; per-frame updates do not allocate.
 */
@PublicApi
public final class Tweens {

    /** Tolerance (1 microsecond) when checking the end time; absorbs float rounding of frame deltas. */
    private static final double END_EPSILON = 1e-6;

    static final class Entry implements Pool.Poolable {
        FloatSetter setter;
        Interpolation ease;
        Runnable onComplete;
        float start;
        float target;
        float duration;
        double elapsed;
        long createdOnUpdate;
        boolean active;
        int generation;

        @Override
        public void reset() {
            setter = null;
            ease = null;
            onComplete = null;
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
    private long updateCount;
    private boolean updating;

    public Tweens(Time time) {
        this.time = Objects.requireNonNull(time, "time");
    }

    /**
     * Moves a property from its current value to {@code target} over {@code seconds} of game time.
     *
     * @param seconds 0 or more; 0 sets the target on the next update
     * @param ease easing curve, for example {@code Interpolation.linear} or {@code Interpolation.smooth}
     */
    public TweenHandle to(FloatGetter get, FloatSetter set, float target, float seconds, Interpolation ease) {
        Objects.requireNonNull(get, "get");
        Objects.requireNonNull(set, "set");
        Objects.requireNonNull(ease, "ease");
        if (!(Float.isFinite(seconds) && seconds >= 0f)) {
            throw new IllegalArgumentException("seconds must be >= 0 and finite (was " + seconds + ")");
        }
        Entry entry = pool.obtain();
        entry.setter = set;
        entry.ease = ease;
        entry.start = get.get();
        entry.target = target;
        entry.duration = seconds;
        entry.elapsed = 0d;
        entry.createdOnUpdate = updating ? updateCount : -1L;
        entry.active = true;
        entries.add(entry);
        return new TweenHandle(this, entry);
    }

    /** Stops a tween where it is; its completion action does not run. A no-op if it is no longer active. */
    public void cancel(TweenHandle handle) {
        if (handle != null && handle.isActive()) {
            handle.entry.active = false;
            if (!updating) {
                compact();
            }
        }
    }

    /** Stops every running tween; no completion actions run. */
    public void cancelAll() {
        for (Entry entry : entries) {
            entry.active = false;
        }
        if (!updating) {
            compact();
        }
    }

    /** Number of running tweens (tests and debug). */
    int size() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.active) {
                count++;
            }
        }
        return count;
    }

    int pooledFree() {
        return pool.getFree();
    }

    /**
     * Advances every tween by the frame's scaled delta. The engine calls this for {@code context.tweens()} at
     * the start of {@link Phase#UPDATE}; call it yourself only for a {@code Tweens} instance you created.
     */
    public void update() {
        float delta = time.delta();
        if (delta <= 0f) {
            return; // paused or frozen
        }
        updateCount++;
        updating = true;
        try {
            for (int i = 0; i < entries.size; i++) {
                Entry entry = entries.get(i);
                if (!entry.active || entry.createdOnUpdate == updateCount) {
                    continue;
                }
                entry.elapsed += delta;
                if (entry.elapsed >= entry.duration - END_EPSILON) {
                    entry.setter.set(entry.target);
                    entry.active = false;
                    if (entry.onComplete != null) {
                        entry.onComplete.run();
                    }
                } else {
                    entry.setter.set(entry.ease.apply(entry.start, entry.target, (float) (entry.elapsed / entry.duration)));
                }
            }
        } finally {
            updating = false;
            compact();
        }
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
}
