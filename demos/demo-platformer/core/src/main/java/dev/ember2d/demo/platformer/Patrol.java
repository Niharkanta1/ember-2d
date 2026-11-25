package dev.ember2d.demo.platformer;

/** Moves an entity back and forth along x between two limits (Phase 1 motion check). */
public final class Patrol {
    /** World units per second. */
    public float speed = 6f;
    public float minX = -12f;
    public float maxX = 12f;
    /** +1 right, -1 left. */
    public float direction = 1f;
}
