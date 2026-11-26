package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/**
 * Walks back and forth under gravity (F-015), moved by {@link PatrollerSystem}: between {@code minX} and {@code maxX},
 * or {@code range} units each side of where it was placed, turning at walls and optionally at ledges.
 */
@PublicApi
public final class Patroller {
    /** World units per second. */
    public float speed = 6f;
    public float minX = -12f;
    public float maxX = 12f;
    /** Walks this far to each side of where it was placed; 0 uses {@code minX} and {@code maxX}. */
    public float range;
    /** +1 right, -1 left. */
    public float direction = 1f;
    public float gravity = 40f;
    /** The sprite art faces left (flip it to walk right). */
    public boolean artFacesLeft;
    /** Turn around instead of walking off a ledge. */
    public boolean turnAtLedges;
    /** Current vertical speed (runtime). */
    public float vy;

    boolean limitsSet;
}
