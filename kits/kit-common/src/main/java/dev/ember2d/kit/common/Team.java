package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** A team or faction (F-021): hit boxes never hurt their own team. Entities without one are team 0. */
@PublicApi
public final class Team {
    public int id;

    /** The team of an entity: its {@link Team}, or 0. */
    static int of(dev.ember2d.ecs.World world, int entity) {
        return world.has(entity, Team.class) ? world.get(entity, Team.class).id : 0;
    }
}
