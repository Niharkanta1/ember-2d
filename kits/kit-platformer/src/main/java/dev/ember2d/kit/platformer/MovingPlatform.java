package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/**
 * Moves a {@code Solid} back and forth (F-015), moved by {@link MovingPlatformSystem}: {@code distance} world units from
 * its start, to the right or (with {@code vertical}) upwards. Riders are carried.
 */
@PublicApi
public final class MovingPlatform {
    /** World units per second. */
    public float speed = 2f;
    /** How far it travels from its start. */
    public float distance = 6f;
    /** Moves up and down instead of right and left. */
    public boolean vertical;
    /** +1 away from the start, -1 back (runtime). */
    public float direction = 1f;
    /** Distance travelled from the start (runtime). */
    public float travelled;

    float velocity;

    /** Horizontal velocity of the last step, world units per second. */
    public float velocityX() {
        return vertical ? 0f : velocity;
    }

    /** Vertical velocity of the last step, world units per second. */
    public float velocityY() {
        return vertical ? velocity : 0f;
    }
}
