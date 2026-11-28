package dev.ember2d.kit.topdown.vision;

import com.badlogic.gdx.math.MathUtils;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.RayHit;
import dev.ember2d.core.PublicApi;
import java.util.Objects;

/**
 * The viewer's field of view (F-030): a circle of full sight plus a longer cone in the aim direction, both cut off by
 * whatever blocks sight. {@link #update} casts {@link VisionSettings#rayCount} rays through {@link Collision} against
 * the colliders and tile grids on the {@code sightMask} layers (non-trigger colliders and solid tiles) and keeps how
 * far each ray sees. Low props that block movement but not sight simply live on another collision layer.
 */
@PublicApi
public final class VisionField {

    private final Collision collision;
    private final int sightMask;
    private final VisionSettings settings;
    private final float[] distances;
    private final float[] dirX;
    private final float[] dirY;
    private final RayHit hit = new RayHit();
    private float originX;
    private float originY;
    private float facing;
    /** False shows everything (vision switched off); the field still updates. */
    public boolean enabled = true;

    /**
     * @param sightMask collision layers that block sight, as a bit mask ({@code 1 << layer})
     */
    public VisionField(Collision collision, int sightMask, VisionSettings settings) {
        this.collision = Objects.requireNonNull(collision, "collision");
        this.sightMask = sightMask;
        this.settings = Objects.requireNonNull(settings, "settings");
        settings.validate();
        int rays = settings.rayCount;
        distances = new float[rays];
        dirX = new float[rays];
        dirY = new float[rays];
        for (int i = 0; i < rays; i++) {
            float angle = MathUtils.PI2 * i / rays;
            dirX[i] = MathUtils.cos(angle);
            dirY[i] = MathUtils.sin(angle);
        }
    }

    public VisionSettings settings() {
        return settings;
    }

    /** Recomputes the field from the eye position and the aim direction (degrees: 0 right, 90 up). */
    public void update(float x, float y, float aimDegrees) {
        originX = x;
        originY = y;
        facing = aimDegrees * MathUtils.degreesToRadians;
        float peek = settings.wallPeek;
        for (int i = 0; i < distances.length; i++) {
            float range = rangeAt(MathUtils.PI2 * i / distances.length);
            float reach = range + peek;
            float seen = reach;
            if (collision.raycast(x, y, x + dirX[i] * reach, y + dirY[i] * reach, sightMask, hit)) {
                seen = hit.distance + peek;
            }
            distances[i] = Math.min(range, seen);
        }
    }

    /** How far the viewer would see in a direction (radians) with nothing in the way. */
    public float rangeAt(float angleRadians) {
        float delta = Math.abs(angleDifference(angleRadians, facing)) * MathUtils.radiansToDegrees;
        float half = settings.coneHalfAngle;
        if (delta <= half) {
            return settings.coneRange;
        }
        float softness = settings.coneSoftness;
        if (softness <= 0f || delta >= half + softness) {
            return settings.nearRadius;
        }
        float t = (delta - half) / softness;
        float eased = t * t * (3f - 2f * t);
        return MathUtils.lerp(settings.coneRange, settings.nearRadius, eased);
    }

    /** True if the point is inside the field and not behind something that blocks sight; always true when disabled. */
    public boolean isVisible(float x, float y) {
        return isVisible(x, y, 0f);
    }

    /**
     * True if any part of a circle of {@code radius} is within range and its centre is not behind something that
     * blocks sight (the original game's rule). Always true when {@link #enabled} is false.
     */
    public boolean isVisible(float x, float y, float radius) {
        if (!enabled) {
            return true;
        }
        float dx = x - originX;
        float dy = y - originY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= radius) {
            return true;
        }
        if (distance - radius > rangeAt(MathUtils.atan2(dy, dx))) {
            return false;
        }
        return !collision.raycast(originX, originY, x, y, sightMask, hit) || hit.distance >= distance - 1e-3f;
    }

    /**
     * How visible a point is, 0..1: 1 well inside the field, fading to 0 over {@link VisionSettings#edgeSoftness} at
     * its edge (like the fog), 0 outside it.
     */
    public float visibility(float x, float y) {
        if (!enabled) {
            return 1f;
        }
        float dx = x - originX;
        float dy = y - originY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = MathUtils.atan2(dy, dx);
        if (angle < 0f) {
            angle += MathUtils.PI2;
        }
        int ray = Math.round(angle / MathUtils.PI2 * distances.length) % distances.length;
        float edge = distances[ray];
        if (distance >= edge) {
            return 0f;
        }
        float soft = settings.edgeSoftness;
        return soft <= 0f ? 1f : Math.min(1f, (edge - distance) / soft);
    }

    public float originX() {
        return originX;
    }

    public float originY() {
        return originY;
    }

    public int rayCount() {
        return distances.length;
    }

    /** How far ray {@code index} sees after occlusion; rays go counter-clockwise from 0 degrees (right). */
    public float distance(int index) {
        return distances[index];
    }

    public float directionX(int index) {
        return dirX[index];
    }

    public float directionY(int index) {
        return dirY[index];
    }

    private static float angleDifference(float a, float b) {
        float d = (a - b) % MathUtils.PI2;
        if (d > MathUtils.PI) {
            d -= MathUtils.PI2;
        } else if (d < -MathUtils.PI) {
            d += MathUtils.PI2;
        }
        return d;
    }
}
