package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;

/**
 * One emitter of a {@link Pattern} (F-024), parsed from JSON; read-only. Angles are in degrees (0 right, 90 up),
 * distances in world units, times in seconds.
 */
@PublicApi
public final class Emitter {

    /** How the bullets of one volley are spread. */
    public enum Type { SINGLE, RING, SPIRAL, ARC, AIMED, RANDOM, WAVE }

    public final Type type;
    /** Index into {@link BulletTypes}. */
    public final int bullet;
    public final int count;
    public final float speed;
    public final float angle;
    public final float spread;
    public final int repeat;
    public final float interval;
    public final float delay;
    public final float angleStep;
    public final float speedStep;
    public final float accel;
    public final float turn;
    public final float amplitude;
    public final float frequency;
    public final float offset;
    /** Run by each bullet at its position after {@link #childAfter} seconds (then the bullet is removed); or null. */
    public final Emitter child;
    public final float childAfter;

    Emitter(Type type, int bullet, int count, float speed, float angle, float spread, int repeat, float interval,
            float delay, float angleStep, float speedStep, float accel, float turn, float amplitude, float frequency,
            float offset, Emitter child, float childAfter) {
        this.type = type;
        this.bullet = bullet;
        this.count = count;
        this.speed = speed;
        this.angle = angle;
        this.spread = spread;
        this.repeat = repeat;
        this.interval = interval;
        this.delay = delay;
        this.angleStep = angleStep;
        this.speedStep = speedStep;
        this.accel = accel;
        this.turn = turn;
        this.amplitude = amplitude;
        this.frequency = frequency;
        this.offset = offset;
        this.child = child;
        this.childAfter = childAfter;
    }

    /** The duration of all volleys after the delay. */
    public float duration() {
        return delay + interval * Math.max(0, repeat - 1);
    }
}
