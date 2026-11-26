package dev.ember2d.kit.platformer;

import dev.ember2d.collision.Collision;
import dev.ember2d.collision.Solid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;

/**
 * Fixed-step movement for {@link MovingPlatform} through {@link Collision#moveSolid} (F-015): carries riders, pushes
 * actors. Register it before the systems that move actors, so riders see where the platform is.
 */
@PublicApi
public final class MovingPlatformSystem extends EntitySystem {

    private final Collision collision;

    public MovingPlatformSystem(World world, Collision collision) {
        super(Phase.FIXED, world.family(Transform.class, MovingPlatform.class, Solid.class));
        this.collision = collision;
    }

    @Override
    protected void process(int entity, float delta) {
        MovingPlatform p = world().get(entity, MovingPlatform.class);
        float step = p.direction * p.speed * delta;
        if (p.travelled + step > p.distance || p.travelled + step < 0f) {
            p.direction = -p.direction;
            step = -step;
        }
        p.travelled += step;
        p.velocity = delta > 0f ? step / delta : 0f;
        if (p.vertical) {
            collision.moveSolid(entity, 0f, step);
        } else {
            collision.moveSolid(entity, step, 0f);
        }
    }
}
