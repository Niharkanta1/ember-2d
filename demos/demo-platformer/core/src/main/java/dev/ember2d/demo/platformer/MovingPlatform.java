package dev.ember2d.demo.platformer;

/** Moves a solid back and forth horizontally over {@code distance} world units (Phase 2 collision check). */
public final class MovingPlatform {
    /** World units per second. */
    public float speed = 2f;
    /** How far it travels from its start to the right. */
    public float distance = 6f;
    /** +1 right, -1 left (runtime). */
    public float direction = 1f;
    /** Distance travelled from the start (runtime). */
    public float travelled;
}
