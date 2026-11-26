package dev.ember2d.demo.platformer;

import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.MoveResult;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;

/** Fixed-step movement for {@link Patrol}: constant speed with gravity; turns at the limits and at walls. */
public final class PatrolSystem extends EntitySystem {

    private final Collision collision;

    public PatrolSystem(World world, Collision collision) {
        super(Phase.FIXED, world.family(Transform.class, Patrol.class, Actor.class));
        this.collision = collision;
    }

    @Override
    protected void process(int entity, float delta) {
        Patrol patrol = world().get(entity, Patrol.class);
        patrol.vy -= patrol.gravity * delta;
        MoveResult r = collision.moveAndSlide(entity, patrol.direction * patrol.speed * delta, patrol.vy * delta);
        if (r.hitY) {
            patrol.vy = 0f;
        }
        float x = world().get(entity, Transform.class).position.x;
        if (r.hitX || (patrol.direction > 0 && x >= patrol.maxX) || (patrol.direction < 0 && x <= patrol.minX)) {
            patrol.direction = -patrol.direction;
        }
    }
}
