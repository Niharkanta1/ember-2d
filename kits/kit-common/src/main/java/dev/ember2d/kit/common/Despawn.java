package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.World;

/**
 * Removes an entity from play (F-021): a pooled entity goes back to its {@link EntityPool}, any other entity is
 * destroyed. Kit systems use this for deaths, {@code destroyOnHit} and {@link Lifetime}; games should too, so pooled
 * entities return automatically.
 */
@PublicApi
public final class Despawn {

    private Despawn() {
    }

    /** Despawns the entity; dead or already freed entities are ignored. */
    public static void despawn(World world, int entity) {
        if (!world.isAlive(entity)) {
            return;
        }
        Pooled pooled = world.get(entity, Pooled.class);
        if (pooled == null) {
            world.destroy(entity);
        } else if (pooled.inUse) {
            pooled.pool.free(entity);
        }
    }
}
