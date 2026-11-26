package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.Vector2;

/** Moves an entity with the {@code move} input axis, or towards a tapped point (Phase 2 input check). */
public final class PlayerControl {
    /** World units per second. */
    public float speed = 8f;
    public float minY = 0f;
    public float maxY = 6f;
    /** Where a tap sent the player; used while {@link #hasTarget}. */
    public final Vector2 target = new Vector2();
    public boolean hasTarget;
}
