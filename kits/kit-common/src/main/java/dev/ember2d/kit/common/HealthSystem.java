package dev.ember2d.kit.common;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;

/**
 * Health and damage (F-021), in {@code FIXED}: starts {@link Health#current} at full and counts down invulnerability.
 * {@link #damage} is the one way to hurt an entity: it respects invulnerability, publishes {@link Damaged}, and
 * publishes {@link Died} exactly once when health reaches 0.
 */
@PublicApi
public final class HealthSystem extends EntitySystem {

    private final EventBus events;

    public HealthSystem(World world, EventBus events) {
        super(Phase.FIXED, world.family(Health.class));
        this.events = events;
    }

    @Override
    protected void process(int entity, float delta) {
        Health h = world().get(entity, Health.class);
        start(h);
        if (h.invulnerableLeft > 0f) {
            h.invulnerableLeft = Math.max(0f, h.invulnerableLeft - delta);
        }
    }

    /**
     * Damages a living, not invulnerable entity with {@link Health}.
     *
     * @param source the entity dealing the damage, or {@code World.NONE}
     * @return whether the damage applied
     */
    public boolean damage(int target, int amount, int source, float knockbackX, float knockbackY) {
        Health h = health(target);
        if (h == null || h.dead || amount <= 0 || h.invulnerableLeft > 0f) {
            return false;
        }
        int taken = amount * Math.max(0, h.damageMultiplier);
        if (taken <= 0) {
            return false;
        }
        h.current = Math.max(0, h.current - taken);
        h.invulnerableLeft = h.invulnerableSeconds;
        events.publish(new Damaged(target, source, taken, h.current, knockbackX, knockbackY));
        if (h.current == 0) {
            die(target, h, source);
        }
        return true;
    }

    /** Restores health up to {@link Health#max}; the dead stay dead. */
    public void heal(int target, int amount) {
        Health h = health(target);
        if (h != null && !h.dead && amount > 0) {
            h.current = Math.min(h.max, h.current + amount);
        }
    }

    /** Sets health to 0 and publishes {@link Died}, ignoring invulnerability. */
    public void kill(int target, int source) {
        Health h = health(target);
        if (h != null && !h.dead) {
            h.current = 0;
            die(target, h, source);
        }
    }

    public boolean isInvulnerable(int target) {
        Health h = health(target);
        return h != null && h.invulnerableLeft > 0f;
    }

    private Health health(int entity) {
        if (!world().isAlive(entity)) {
            return null;
        }
        Health h = world().get(entity, Health.class);
        if (h != null) {
            start(h);
        }
        return h;
    }

    private static void start(Health h) {
        if (h.current < 0) {
            h.current = h.max;
        }
    }

    private void die(int entity, Health h, int killer) {
        h.dead = true;
        events.publish(new Died(entity, killer));
        if (h.destroyOnDeath) {
            Despawn.despawn(world(), entity);
        }
    }
}
