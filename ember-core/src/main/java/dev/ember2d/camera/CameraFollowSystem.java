package dev.ember2d.camera;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.Objects;

/**
 * Moves the {@link CameraRig} after its {@link CameraFollow} target in {@code LATE} (F-004). The target is followed
 * at its interpolated position, the same one sprites are drawn at, so camera and sprites never disagree (no jitter).
 * Order per frame: dead zone, smoothing, bounds.
 */
@PublicApi
public final class CameraFollowSystem extends EntitySystem {

    private final CameraRig rig;
    private final Time time;
    private final Vector2 target = new Vector2();

    public CameraFollowSystem(World world, CameraRig rig, Time time) {
        super(Phase.LATE, world.family(CameraFollow.class));
        this.rig = Objects.requireNonNull(rig, "rig");
        this.time = Objects.requireNonNull(time, "time");
    }

    @Override
    protected void process(int entity, float delta) {
        CameraFollow follow = world().get(entity, CameraFollow.class);
        int targetEntity = follow.target == null ? World.NONE : follow.target.entity(world());
        Transform transform = targetEntity == World.NONE ? null : world().get(targetEntity, Transform.class);
        if (transform != null) {
            transform.interpolated(time.alpha(), target);
            float desiredX = deadZoneAxis(rig.position.x, target.x, follow.deadZone.x, follow.deadZone.width);
            float desiredY = deadZoneAxis(rig.position.y, target.y, follow.deadZone.y, follow.deadZone.height);
            if (follow.smoothing > 0f) {
                float blend = 1f - (float) Math.exp(-follow.smoothing * delta);
                rig.position.x += (desiredX - rig.position.x) * blend;
                rig.position.y += (desiredY - rig.position.y) * blend;
            } else {
                rig.position.set(desiredX, desiredY);
            }
        }
        if (follow.bounds != null) {
            clampToBounds(follow.bounds);
        }
    }

    /** The camera coordinate that keeps the target inside the dead zone on one axis. */
    static float deadZoneAxis(float camera, float target, float zoneOffset, float zoneSize) {
        float centre = camera + zoneOffset;
        float half = zoneSize / 2f;
        if (target < centre - half) {
            return camera + (target - (centre - half));
        }
        if (target > centre + half) {
            return camera + (target - (centre + half));
        }
        return camera;
    }

    private void clampToBounds(Rectangle bounds) {
        rig.position.x = clampAxis(rig.position.x, bounds.x, bounds.width, rig.viewWidth());
        rig.position.y = clampAxis(rig.position.y, bounds.y, bounds.height, rig.viewHeight());
    }

    private static float clampAxis(float centre, float min, float size, float view) {
        if (view >= size) {
            return min + size / 2f;
        }
        return MathUtils.clamp(centre, min + view / 2f, min + size - view / 2f);
    }
}
