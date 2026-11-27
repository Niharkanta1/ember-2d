package dev.ember2d.kit.common;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import dev.ember2d.collision.Collider;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;

/**
 * Hit boxes against hurt boxes (F-021), in {@code FIXED} after movement. An active {@link Hitbox} that overlaps a
 * {@link Hurtbox} of another team damages it through {@link HealthSystem#damage}, once per target per activation,
 * with knockback pointing from the centre of the hitbox to the centre of the hurtbox. A plain loop over pairs: fine
 * for melee, enemies and pickups (bullets use {@code kit-bullets}).
 */
@PublicApi
public final class HitSystem extends EntitySystem {

    /** What this system remembers about one hitbox entity. */
    private static final class State {
        final IntArray targets = new IntArray();
        int activation;
        boolean active;
        float activeFor;
        int lastStep = Integer.MIN_VALUE;
    }

    private final HealthSystem health;
    private final Family hurtboxes;
    private final IntMap<State> states = new IntMap<>();
    private final Rectangle hit = new Rectangle();
    private final Rectangle hurt = new Rectangle();
    private int step;

    public HitSystem(World world, HealthSystem health) {
        super(Phase.FIXED, world.family(Hitbox.class, Transform.class));
        this.health = health;
        this.hurtboxes = world.family(Hurtbox.class, Transform.class, Health.class);
    }

    @Override
    protected void begin() {
        step++;
    }

    @Override
    protected void process(int entity, float delta) {
        World world = world();
        Hitbox hb = world.get(entity, Hitbox.class);
        State s = states.get(entity);
        if (s == null) {
            s = new State();
            states.put(entity, s);
        }
        // A new activation: the hitbox just turned on, restart() was called, or the entity was away (new, or pooled).
        boolean continuing = s.lastStep == step - 1 && s.active && s.activation == hb.activation;
        if (hb.active && !continuing) {
            s.targets.clear();
            s.activeFor = 0f;
        }
        s.lastStep = step;
        s.activation = hb.activation;
        s.active = hb.active;
        if (!hb.active) {
            return;
        }
        if (hb.activeSeconds > 0f) {
            s.activeFor += delta;
            if (s.activeFor > hb.activeSeconds) {
                hb.active = false;
                s.active = false;
                return;
            }
        }
        Transform t = world.get(entity, Transform.class);
        hit.set(t.position.x + hb.box.x, t.position.y + hb.box.y, hb.box.width, hb.box.height);
        int team = Team.of(world, entity);
        IntArray hurtEntities = hurtboxes.entities();
        for (int i = 0; i < hurtEntities.size; i++) {
            int target = hurtEntities.get(i);
            if (target == entity || s.targets.contains(target) || Team.of(world, target) == team
                    || !hurtArea(target, hurt) || !hit.overlaps(hurt)) {
                continue;
            }
            s.targets.add(target);
            float dx = (hurt.x + hurt.width / 2f) - (hit.x + hit.width / 2f);
            float dy = (hurt.y + hurt.height / 2f) - (hit.y + hit.height / 2f);
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            float kx = length > 0f ? dx / length * hb.knockback : hb.knockback;
            float ky = length > 0f ? dy / length * hb.knockback : 0f;
            if (health.damage(target, hb.damage, entity, kx, ky) && hb.destroyOnHit) {
                Despawn.despawn(world, entity);
                return;
            }
        }
    }

    @Override
    protected void end() {
        // Forget hitboxes that are gone, so a reused entity ID starts fresh.
        for (IntMap.Keys keys = states.keys(); keys.hasNext; ) {
            int e = keys.next();
            if (!world().isAlive(e)) {
                keys.remove();
            }
        }
    }

    /** The hurt area in world space: the Hurtbox box, or the Collider box when the Hurtbox box is empty. */
    private boolean hurtArea(int entity, Rectangle out) {
        Hurtbox hb = world().get(entity, Hurtbox.class);
        Transform t = world().get(entity, Transform.class);
        Rectangle box = hb.box;
        if (box.width <= 0f || box.height <= 0f) {
            Collider c = world().get(entity, Collider.class);
            if (c == null) {
                return false;
            }
            box = c.box;
        }
        out.set(t.position.x + box.x, t.position.y + box.y, box.width, box.height);
        return true;
    }
}
