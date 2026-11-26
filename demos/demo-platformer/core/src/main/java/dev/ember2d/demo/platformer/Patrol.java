package dev.ember2d.demo.platformer;

/** Walks back and forth between two limits under gravity, turning at walls (Phase 1 motion check, Phase 2 actor). */
public final class Patrol {
    /** World units per second. */
    public float speed = 6f;
    public float minX = -12f;
    public float maxX = 12f;
    /** +1 right, -1 left. */
    public float direction = 1f;
    public float gravity = 40f;
    /** Current vertical speed (runtime). */
    public float vy;
}
