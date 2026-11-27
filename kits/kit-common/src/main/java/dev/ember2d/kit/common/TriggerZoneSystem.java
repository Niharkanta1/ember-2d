package dev.ember2d.kit.common;

import dev.ember2d.collision.TriggerEntered;
import dev.ember2d.collision.TriggerExited;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;

/**
 * Turns collision trigger events into {@link ZoneEntered} and {@link ZoneExited} for entities that pass a
 * {@link TriggerZone}'s filters (F-021). It subscribes when created, so create it in a scene's {@code enter}: the
 * subscriptions then belong to the scene. It does no per-entity work.
 */
@PublicApi
public final class TriggerZoneSystem extends EntitySystem {

    private final EventBus events;

    public TriggerZoneSystem(World world, EventBus events) {
        super(Phase.FIXED, world.family(TriggerZone.class));
        this.events = events;
        events.on(TriggerEntered.class, e -> entered(e.trigger(), e.actor()));
        events.on(TriggerExited.class, e -> exited(e.trigger(), e.actor()));
    }

    @Override
    protected void process(int entity, float delta) {
    }

    private void entered(int zone, int entity) {
        TriggerZone z = zone(zone);
        if (z == null || (z.once && z.fired) || !passes(z, entity)) {
            return;
        }
        if (z.once) {
            z.fired = true;
        }
        events.publish(new ZoneEntered(zone, entity, name(zone)));
    }

    private void exited(int zone, int entity) {
        TriggerZone z = zone(zone);
        if (z == null || (z.once && z.fired) || !passes(z, entity)) {
            return;
        }
        events.publish(new ZoneExited(zone, entity, name(zone)));
    }

    private TriggerZone zone(int zone) {
        return world().isAlive(zone) ? world().get(zone, TriggerZone.class) : null;
    }

    private boolean passes(TriggerZone z, int entity) {
        World world = world();
        if (!world.isAlive(entity)) {
            return false;
        }
        if (!z.tag.isEmpty() && !world.hasTag(entity, z.tag)) {
            return false;
        }
        return z.team < 0 || Team.of(world, entity) == z.team;
    }

    private String name(int zone) {
        String name = world().name(zone);
        return name == null ? "" : name;
    }
}
