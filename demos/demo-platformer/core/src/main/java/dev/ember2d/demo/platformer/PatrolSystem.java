package dev.ember2d.demo.platformer;

import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;

/** Fixed-step movement for {@link Patrol}: constant speed, bounce at the limits. */
public final class PatrolSystem extends EntitySystem {

    public PatrolSystem(World world) {
        super(Phase.FIXED, world.family(Transform.class, Patrol.class));
    }

    @Override
    protected void process(int entity, float delta) {
        Transform transform = world().get(entity, Transform.class);
        Patrol patrol = world().get(entity, Patrol.class);
        transform.position.x += patrol.direction * patrol.speed * delta;
        if (transform.position.x > patrol.maxX) {
            transform.position.x = patrol.maxX;
            patrol.direction = -1f;
        } else if (transform.position.x < patrol.minX) {
            transform.position.x = patrol.minX;
            patrol.direction = 1f;
        }
    }
}
