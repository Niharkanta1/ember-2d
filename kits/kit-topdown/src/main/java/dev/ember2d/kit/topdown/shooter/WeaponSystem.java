package dev.ember2d.kit.topdown.shooter;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.bullets.BulletSystem;
import dev.ember2d.kit.common.Team;
import dev.ember2d.kit.topdown.TopDownBody;

/**
 * Fires {@link Weapon}s through kit-bullets (F-016), in {@code FIXED}: counts down cooldowns and reloads, reads the
 * trigger from input for weapons with a {@code fireAction}, fires at the muzzle along the {@link Aim} (or the
 * {@code TopDownBody} facing) with the shooter's {@code Team}, and reloads an empty magazine by itself. Publishes the
 * reused {@link ShotEvents} objects, so shooting allocates nothing.
 */
@PublicApi
public final class WeaponSystem extends EntitySystem {

    private final BulletSystem bullets;
    private final WeaponLibrary weapons;
    private final EventBus events;
    private final Input input;
    private final ShotEvents.Fired fired = new ShotEvents.Fired();
    private final ShotEvents.ReloadStarted reloadStarted = new ShotEvents.ReloadStarted();
    private final ShotEvents.Reloaded reloaded = new ShotEvents.Reloaded();

    /** @param input reads {@code fireAction} and {@code reloadAction}; may be null when only code pulls triggers */
    public WeaponSystem(World world, BulletSystem bullets, WeaponLibrary weapons, EventBus events, Input input) {
        super(Phase.FIXED, world.family(Weapon.class, Transform.class));
        this.bullets = bullets;
        this.weapons = weapons;
        this.events = events;
        this.input = input;
    }

    /** Starts a reload when the magazine is not full and no reload runs; returns whether one started. */
    public boolean reload(int entity) {
        Weapon w = world().get(entity, Weapon.class);
        if (w == null) {
            return false;
        }
        WeaponDef def = weapons.get(w.weapon);
        fill(w, def);
        if (def.magazine == 0 || w.reloading || w.ammo >= def.magazine) {
            return false;
        }
        startReload(entity, w, def);
        return true;
    }

    @Override
    protected void process(int entity, float delta) {
        Weapon w = world().get(entity, Weapon.class);
        WeaponDef def = weapons.get(w.weapon);
        fill(w, def);
        if (input != null && !w.fireAction.isEmpty()) {
            w.trigger = input.pressed(w.fireAction);
        }
        if (input != null && !w.reloadAction.isEmpty() && input.justPressed(w.reloadAction)) {
            reload(entity);
        }
        if (w.cooldown > 0f) {
            w.cooldown -= delta;
        }
        if (w.reloading) {
            w.reloadLeft -= delta;
            if (w.reloadLeft <= 0f) {
                w.reloading = false;
                w.reloadLeft = 0f;
                w.ammo = def.magazine;
                reloaded.entity = entity;
                reloaded.weapon = def.id;
                reloaded.ammo = w.ammo;
                events.publish(reloaded);
            }
        }
        boolean pull = w.trigger && (def.auto || !w.wasPulled);
        w.wasPulled = w.trigger;
        if (!pull || w.cooldown > 0f || w.reloading) {
            return;
        }
        if (def.magazine > 0 && w.ammo <= 0) {
            startReload(entity, w, def);
            return;
        }
        shoot(entity, w, def);
        if (def.magazine > 0 && w.ammo == 0) {
            startReload(entity, w, def);
        }
    }

    private void shoot(int entity, Weapon w, WeaponDef def) {
        Transform t = world().get(entity, Transform.class);
        float angle;
        float dx;
        float dy;
        Aim aim = world().get(entity, Aim.class);
        TopDownBody body = world().get(entity, TopDownBody.class);
        if (aim != null && aim.aimed) {
            dx = aim.aimX;
            dy = aim.aimY;
            angle = aim.angle;
        } else if (body != null) {
            dx = body.facingX;
            dy = body.facingY;
            angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        } else {
            dx = 1f;
            dy = 0f;
            angle = 0f;
        }
        float x = t.position.x + dx * def.muzzle;
        float y = t.position.y + dy * def.muzzle;
        Team team = world().get(entity, Team.class);
        bullets.fire(def.emitter, x, y, angle, World.NONE, team == null ? 0 : team.id);
        if (def.magazine > 0) {
            w.ammo--;
        }
        w.cooldown += 1f / def.fireRate;
        if (w.cooldown < 0f) {
            w.cooldown = 1f / def.fireRate;
        }
        fired.entity = entity;
        fired.weapon = def.id;
        fired.x = x;
        fired.y = y;
        fired.angle = angle;
        fired.ammo = w.ammo;
        events.publish(fired);
    }

    private void startReload(int entity, Weapon w, WeaponDef def) {
        w.reloading = true;
        w.reloadLeft = def.reload;
        reloadStarted.entity = entity;
        reloadStarted.weapon = def.id;
        reloadStarted.seconds = def.reload;
        events.publish(reloadStarted);
    }

    private static void fill(Weapon w, WeaponDef def) {
        if (w.ammo < 0) {
            w.ammo = def.magazine;
        }
    }
}
