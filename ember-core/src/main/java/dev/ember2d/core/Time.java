package dev.ember2d.core;

/**
 * Frame timing and game time (F-002), available as {@code context.time()}. Systems never measure time
 * themselves; they read it here.
 *
 * <p>Game pause and time scale are owned by game code (pause menus, slow motion). They are separate from the
 * app being suspended by the platform. While paused, {@link #delta()} is 0 and no {@link Phase#FIXED} steps
 * run; {@link Phase#RENDER} keeps running.
 */
@PublicApi
public interface Time {

    /** Scaled frame delta in seconds; 0 while paused. */
    float delta();

    /** Real frame delta in seconds, clamped to 0.25 s; unaffected by pause and time scale. */
    float unscaledDelta();

    /** The constant fixed step in seconds (for example 1/60). Use it in {@link Phase#FIXED} systems. */
    float fixedDelta();

    /** How far the frame is between the last and the next fixed step, in [0, 1). Use it to interpolate rendering. */
    float alpha();

    /** Scaled game time in seconds since boot; stops while paused. */
    double elapsed();

    /** Real time in seconds since boot (sum of clamped frame deltas). */
    double unscaledElapsed();

    /** Number of fixed steps run since boot. */
    long fixedTick();

    /** Current time scale; 1 is normal speed. */
    float timeScale();

    /**
     * Sets the time scale.
     *
     * @param scale 0 (frozen) to 10
     * @throws IllegalArgumentException outside 0..10 or NaN
     */
    void setTimeScale(float scale);

    boolean paused();

    void setPaused(boolean paused);

    /**
     * While paused, the next frame runs exactly one fixed step, and {@code UPDATE} sees that step's delta (dev tools:
     * step one tick, F-018). Ignored while running.
     */
    void step();

    /** Fixed steps discarded because a frame needed more than the catch-up limit; a debug counter. */
    long droppedFixedSteps();
}
