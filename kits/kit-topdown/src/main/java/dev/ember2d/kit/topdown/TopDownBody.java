package dev.ember2d.kit.topdown;

import dev.ember2d.core.PublicApi;

/**
 * Top-down movement (F-016): 8-way with acceleration and friction, or tile by tile with {@link #gridStep}. Moved by
 * {@link TopDownSystem}; the entity also needs an {@code Actor} and a {@code Collider}. Units are world units (tiles)
 * and seconds.
 */
@PublicApi
public final class TopDownBody {
    public float maxSpeed = 6f;
    /** Towards the input direction, units per second squared. */
    public float acceleration = 60f;
    /** Slowing down with no input. */
    public float friction = 50f;
    /** How fast knockback velocity fades. */
    public float knockbackDecay = 30f;
    /** Multiplies the knockback of kit-common {@code Damaged} events; 0 ignores them. */
    public float knockbackScale = 1f;
    /** Face left and right by flipping the {@code Sprite}. */
    public boolean flipSprite = true;
    /** Move tile by tile instead of freely. */
    public boolean gridStep;
    /** Grid mode: tiles per second. */
    public float stepsPerSecond = 6f;
    /** Grid mode: the size of one step. */
    public float tileSize = 1f;

    /** Runtime: velocity from input. */
    public float vx;
    public float vy;
    /** Runtime: velocity from knockback (fades). */
    public float knockbackX;
    public float knockbackY;
    /** Runtime: the last non-zero input direction. */
    public float facingX = 1f;
    public float facingY;
    /** Runtime: moving this step. */
    public boolean moving;
    /** Ignore input (cutscenes, dialogue); knockback still applies. */
    public boolean locked;
    /** Read the {@code move} input axis; false for bodies driven by AI or code through {@link #moveX}, {@link #moveY}. */
    public boolean useInput = true;
    /** The move direction when {@link #useInput} is false (length at most 1), set by AI (I-330) or game code. */
    public float moveX;
    public float moveY;
    /** Runtime, grid mode: distance left in the current step along {@link #stepX}, {@link #stepY}. */
    public float stepLeft;
    public float stepX;
    public float stepY;
}
