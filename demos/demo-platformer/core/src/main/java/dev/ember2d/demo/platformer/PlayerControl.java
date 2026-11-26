package dev.ember2d.demo.platformer;

/** A platformer body steered by input (Phase 2 check): {@code move} walks, {@code jump} jumps, down + jump drops. */
public final class PlayerControl {
    /** Walking speed, world units per second. */
    public float speed = 8f;
    /** Upward speed when a jump starts. */
    public float jumpSpeed = 17f;
    public float gravity = 40f;
    /** Fastest fall speed. */
    public float maxFall = 25f;
    /** Current vertical speed (runtime). */
    public float vy;
}
