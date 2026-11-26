package dev.ember2d.kit.platformer;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.Direction;
import dev.ember2d.collision.MoveResult;
import dev.ember2d.collision.RayHit;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.Sprite;

/**
 * Fixed-step movement for {@link Patroller} (F-015): constant speed with gravity; turns at its limits, at walls and,
 * with {@code turnAtLedges}, before walking off a ledge. Faces the {@code Sprite} its walking direction.
 */
@PublicApi
public final class PatrollerSystem extends EntitySystem {

    /** How far below the front foot the ground must be. */
    private static final float LEDGE_PROBE = 0.5f;

    private final Collision collision;
    private final Rectangle box = new Rectangle();
    private final RayHit ray = new RayHit();

    public PatrollerSystem(World world, Collision collision) {
        super(Phase.FIXED, world.family(Transform.class, Patroller.class, Actor.class));
        this.collision = collision;
    }

    @Override
    protected void process(int entity, float delta) {
        Patroller patrol = world().get(entity, Patroller.class);
        if (patrol.range > 0f && !patrol.limitsSet) {
            float start = world().get(entity, Transform.class).position.x;
            patrol.minX = start - patrol.range;
            patrol.maxX = start + patrol.range;
            patrol.limitsSet = true;
        }
        patrol.vy -= patrol.gravity * delta;
        MoveResult r = collision.moveAndSlide(entity, patrol.direction * patrol.speed * delta, patrol.vy * delta);
        if (r.hitY) {
            patrol.vy = 0f;
        }
        float x = world().get(entity, Transform.class).position.x;
        if (r.hitX || (patrol.direction > 0 && x >= patrol.maxX) || (patrol.direction < 0 && x <= patrol.minX)
                || (patrol.turnAtLedges && atLedge(entity, patrol))) {
            patrol.direction = -patrol.direction;
        }
        Sprite sprite = world().get(entity, Sprite.class);
        if (sprite != null) {
            boolean left = patrol.direction < 0;
            sprite.flipX = patrol.artFacesLeft != left;   // face the walking direction
        }
    }

    /** Standing, with no ground just ahead of the front foot. */
    private boolean atLedge(int entity, Patroller patrol) {
        if (!collision.isTouching(entity, Direction.DOWN)) {
            return false;
        }
        collision.bounds(entity, box);
        float footX = patrol.direction > 0 ? box.x + box.width + 0.05f : box.x - 0.05f;
        return !collision.raycast(footX, box.y + 0.05f, footX, box.y - LEDGE_PROBE, ~0, ray);
    }
}
