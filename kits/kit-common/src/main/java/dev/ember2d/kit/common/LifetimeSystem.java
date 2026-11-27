package dev.ember2d.kit.common;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;

/** Despawns entities whose {@link Lifetime} has run out (F-021), in {@code UPDATE} on scaled time. */
@PublicApi
public final class LifetimeSystem extends EntitySystem {

    public LifetimeSystem(World world) {
        super(Phase.UPDATE, world.family(Lifetime.class));
    }

    @Override
    protected void process(int entity, float delta) {
        Lifetime life = world().get(entity, Lifetime.class);
        if (life.left < 0f) {
            life.left = life.seconds;
        }
        life.left -= delta;
        if (life.left <= 0f) {
            Despawn.despawn(world(), entity);
        }
    }
}
