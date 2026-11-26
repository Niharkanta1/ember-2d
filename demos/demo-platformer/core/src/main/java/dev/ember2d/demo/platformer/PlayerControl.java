package dev.ember2d.demo.platformer;

/**
 * Platformer movement tuning and state (F-015, I-170): acceleration, gravity, variable jump height, coyote time and
 * jump buffering. Distances in world units, times in seconds or fixed ticks. Every value can be set in the prefab,
 * hot-reloaded, and changed live in the dev tools inspector.
 */
public final class PlayerControl {

    /** "Never" for the tick counters. */
    static final int NEVER = 1_000_000;

    /** Top running speed. */
    public float maxSpeed = 8f;
    public float groundAccel = 60f;
    /** Slowing down with no input on the ground. */
    public float groundDecel = 70f;
    public float airAccel = 35f;
    public float airDecel = 20f;
    public float gravity = 40f;
    /** Gravity multiplier while falling, for a snappier arc. */
    public float fallMultiplier = 1.6f;
    /** Fastest fall speed. */
    public float maxFall = 25f;
    /** Upward speed when a jump starts. */
    public float jumpSpeed = 16f;
    /** Releasing jump while rising multiplies the vertical speed by this, once per jump. */
    public float jumpCut = 0.45f;
    /** A jump still starts this many fixed ticks after walking off a ledge. */
    public int coyoteTicks = 6;
    /** A jump pressed up to this many fixed ticks before landing starts on landing. */
    public int bufferTicks = 6;
    /** Upward speed after landing on an enemy. */
    public float stompBounce = 12f;

    /** Horizontal speed (runtime). */
    public float vx;
    /** Vertical speed (runtime). */
    public float vy;
    /** Input is ignored: hurt or level complete (runtime). */
    public boolean locked;
    /** Stood on something after the last step (runtime). */
    public boolean wasGrounded = true;

    int ticksSinceGrounded;
    int ticksSinceJumpPressed = NEVER;
    boolean jumpCutAvailable;
}
