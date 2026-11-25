package dev.ember2d.ecs;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.PublicApi;

/**
 * Position, rotation and scale of an entity in world units (F-003). The world copies {@code position} to
 * {@code previous} and {@code rotation} to {@code previousRotation} before every fixed step, so render systems
 * can interpolate between fixed steps with {@code time.alpha()}. Registered in every {@link World}.
 */
@PublicApi
public final class Transform {

    public final Vector2 position = new Vector2();
    public final Vector2 previous = new Vector2();
    /** Rotation in degrees. */
    public float rotation;
    public float previousRotation;
    public final Vector2 scale = new Vector2(1f, 1f);

    /** Writes {@code previous + (position - previous) * alpha} into {@code out} and returns it. */
    public Vector2 interpolated(float alpha, Vector2 out) {
        return out.set(previous.x + (position.x - previous.x) * alpha,
                previous.y + (position.y - previous.y) * alpha);
    }

    /** {@code previousRotation + (rotation - previousRotation) * alpha}; no wrap-around handling. */
    public float interpolatedRotation(float alpha) {
        return previousRotation + (rotation - previousRotation) * alpha;
    }

    /** Moves to a position without interpolating across the jump (spawn, respawn, level change). */
    public void teleport(float x, float y) {
        position.set(x, y);
        previous.set(x, y);
    }
}
