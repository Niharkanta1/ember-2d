package dev.ember2d.core;

/**
 * The phases of one frame, in execution order (F-002). {@link #FIXED} runs zero or more times per frame at the
 * fixed step; the others run once. Systems declare the phase they run in (F-003).
 */
@PublicApi
public enum Phase {
    /** Sample input. */
    INPUT,
    /** Simulation at the fixed step; read {@link Time#fixedDelta()}. */
    FIXED,
    /** Per-frame game logic; read {@link Time#delta()}. */
    UPDATE,
    /** After update: camera follow and other work that needs final positions. */
    LATE,
    /** Drawing; interpolate with {@link Time#alpha()}. */
    RENDER
}
