package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/**
 * A platformer character's movement tuning and state (F-015), moved by {@link PlatformerSystem}. Distances are world
 * units, times seconds or fixed ticks. Every public field can be set in a prefab or Tiled, hot-reloaded and edited live
 * in the dev tools. The optional mechanics (air jumps, dash, wall slide, wall jump, ladders, platform momentum) are
 * independent and off by default.
 */
@PublicApi
public final class PlatformerBody {

    /** "Never" for the tick counters. */
    static final int NEVER = 1_000_000;

    /** Input names: a 2D axis and two actions. An action missing from the bindings is never pressed. */
    public String moveAxis = "move";
    public String jumpAction = "jump";
    public String dashAction = "dash";

    /** Top running speed. */
    public float maxSpeed = 8f;
    public float groundAccel = 60f;
    /** Slowing down without input on the ground. */
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
    /** A jump pressed up to this many fixed ticks before it can happen still happens. */
    public int bufferTicks = 6;

    /** Jumps allowed in the air after coyote time; refilled on landing. 0 = none. */
    public int airJumps;
    public float airJumpSpeed = 14f;

    /** Dash: a horizontal burst without gravity. */
    public boolean dash;
    public float dashSpeed = 20f;
    public int dashTicks = 10;
    public int dashCooldownTicks = 30;
    /** Dashes allowed per time in the air. */
    public int airDashes = 1;

    /** Wall slide: falling while pushing into a wall is capped at {@code wallSlideSpeed}. */
    public boolean wallSlide;
    public float wallSlideSpeed = 3f;

    /** Wall jump: jumping in the air while touching a wall pushes away from it. */
    public boolean wallJump;
    public float wallJumpX = 9f;
    public float wallJumpY = 15f;
    /** Input towards the wall is ignored this many ticks after a wall jump. */
    public int wallJumpLockTicks = 8;

    /** Ladders: up or down on a {@link Ladder} climbs it without gravity. */
    public boolean ladders;
    public float climbSpeed = 5f;

    /** Jumping off a {@link MovingPlatform} adds its velocity. */
    public boolean inheritPlatformVelocity;

    /** Horizontal speed (runtime). */
    public float vx;
    /** Vertical speed (runtime). */
    public float vy;
    /** Input is ignored, for game rules such as being hurt or a cutscene (runtime). */
    public boolean locked;

    boolean grounded = true;
    boolean wasGrounded = true;
    boolean wallSliding;
    boolean climbing;
    int facing = 1;
    int ticksSinceGrounded;
    int ticksSinceJumpPressed = NEVER;
    boolean jumpCutAvailable;
    int airJumpsLeft;
    int airDashesLeft;
    int dashTicksLeft;
    int dashDirection;
    int dashCooldown;
    int wallLockTicks;
    int wallLockDirection;
    java.util.Map<String, String> asmParameters = java.util.Map.of();
    int asmParameterTicks;

    /** Stood on something after the last step. */
    public boolean grounded() {
        return grounded;
    }

    public boolean dashing() {
        return dashTicksLeft > 0;
    }

    public boolean wallSliding() {
        return wallSliding;
    }

    public boolean climbing() {
        return climbing;
    }

    /** +1 facing right, -1 facing left. */
    public int facing() {
        return facing;
    }
}
