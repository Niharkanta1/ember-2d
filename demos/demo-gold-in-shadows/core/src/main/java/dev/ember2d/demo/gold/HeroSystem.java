package dev.ember2d.demo.gold;

import com.badlogic.gdx.math.MathUtils;
import dev.ember2d.animation.Animator;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.kit.common.Damaged;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.topdown.TopDownBody;
import dev.ember2d.kit.topdown.shooter.Aim;
import dev.ember2d.kit.topdown.shooter.ShotEvents;
import dev.ember2d.kit.topdown.shooter.Weapon;
import dev.ember2d.render.Sprite;

/**
 * The player's presentation (F-029), ported from the original's {@code Player}, {@code PlayerAnimations},
 * {@code GunMount} and {@code ReloadIndicator}. In {@code UPDATE}, after movement and aiming:
 * <ul>
 *   <li>faces the aim (the original flips by the pointer's side, not by movement);</li>
 *   <li>plays idle, walk, hit (0.4 s after a hit) and death;</li>
 *   <li>blinks every 0.08 s while invulnerable;</li>
 *   <li>holds the rifle on a mount at the player's centre, rotated to the aim and mirrored when aiming left, with the
 *       two hands at the original's animated mount-space offsets;</li>
 *   <li>spins the reload icon beside the player while reloading;</li>
 *   <li>after death, waits the original's 1.6 s and queues {@link HeroDied}.</li>
 * </ul>
 * Mount-space offsets are the original's pixels with y pointing down; they are mirrored and rotated here into world
 * units with y up.
 */
final class HeroSystem extends EntitySystem {

    static final float HIT_SECONDS = 0.4f;
    static final float DEATH_SECONDS = 1.6f;
    static final float BLINK_SECONDS = 0.08f;
    static final float RELOAD_TURN_SECONDS = 0.8f;
    private static final float PX = 1f / 16f;

    private final EventBus events;
    private float reloadTurn;

    HeroSystem(World world, EventBus events) {
        super(Phase.UPDATE, world.family(Hero.class, Transform.class, Sprite.class));
        this.events = events;
        events.on(Damaged.class, e -> {
            Hero hero = world.get(e.target(), Hero.class);
            if (hero != null && e.healthLeft() > 0) {
                hero.hitLeft = HIT_SECONDS;
            }
        });
        events.on(ShotEvents.Fired.class, e -> {
            Aim aim = world.get(e.entity, Aim.class);
            if (world.get(e.entity, Hero.class) != null && aim != null) {
                Effects.muzzle(world, e.x, e.y, e.angle, aim.aimX < 0f);
            }
        });
    }

    /** Makes the gun, the hands and the reload icon for a freshly spawned player. */
    static void equip(World world, int player) {
        Hero hero = world.get(player, Hero.class);
        hero.gun = part(world, "atlas.gold/gun.rifle", 21f / 2f - 9f, 5f);   // sprite centred 9 px along the mount
        hero.rightHand = part(world, "atlas.gold/hand", 8f, 8f);
        hero.leftHand = part(world, "atlas.gold/hand", 8f, 8f);
        hero.reloadIcon = part(world, "atlas.gold/reload", 8f, 8f);
    }

    private static int part(World world, String region, float originX, float originY) {
        int e = world.create();
        world.add(e, new Transform());
        Sprite sprite = world.add(e, new Sprite());
        sprite.regionId = region;
        sprite.origin.set(originX, originY);
        sprite.layer = Effects.LAYER;
        return e;
    }

    @Override
    protected void begin() {
        reloadTurn = 0f;
    }

    @Override
    protected void process(int entity, float delta) {
        World w = world();
        Hero hero = w.get(entity, Hero.class);
        Transform t = w.get(entity, Transform.class);
        Sprite sprite = w.get(entity, Sprite.class);
        Animator animator = w.get(entity, Animator.class);
        Aim aim = w.get(entity, Aim.class);
        Health health = w.get(entity, Health.class);
        TopDownBody body = w.get(entity, TopDownBody.class);
        Weapon weapon = w.get(entity, Weapon.class);
        boolean left = aim != null && aim.aimX < 0f;
        sprite.flipX = left;

        if (health != null && health.dead) {
            if (hero.deadFor < 0f) {
                hero.deadFor = 0f;
                animator.play("death");
                if (body != null) {
                    body.locked = true;
                }
                if (weapon != null) {
                    weapon.fireAction = "";
                    weapon.reloadAction = "";
                }
            }
            float before = hero.deadFor;
            hero.deadFor += delta;
            if (before < DEATH_SECONDS && hero.deadFor >= DEATH_SECONDS) {
                events.queue(new HeroDied(entity));
            }
            sprite.visible = true;
            show(hero.gun, false);
            show(hero.leftHand, false);
            show(hero.rightHand, false);
            show(hero.reloadIcon, false);
            return;
        }

        String clip;
        if (hero.hitLeft > 0f) {
            hero.hitLeft -= delta;
            clip = "hit";
        } else {
            clip = body != null && body.moving ? "walk" : "idle";
        }
        animator.playIfNotPlaying(clip);
        boolean blinkOff = health != null && health.invulnerableLeft > 0f
                && ((int) (health.invulnerableLeft / BLINK_SECONDS)) % 2 == 1;
        sprite.visible = !blinkOff;

        float angle = aim != null ? aim.angle : 0f;
        mount(hero.gun, t, 0f, 0f, angle, left, !blinkOff);
        float time = animator.time();
        float[] hands = hands(clip, time);
        mount(hero.rightHand, t, hands[0], hands[1], angle, left, !blinkOff);
        mount(hero.leftHand, t, hands[2], hands[3], angle, left, !blinkOff);

        boolean reloading = weapon != null && weapon.reloading;
        show(hero.reloadIcon, reloading);
        if (reloading && hero.reloadIcon >= 0) {
            Transform icon = w.get(hero.reloadIcon, Transform.class);
            icon.teleport(t.position.x + 11f * PX, t.position.y + 15f * PX);
            float spin = (weapon.reloadLeft % RELOAD_TURN_SECONDS) / RELOAD_TURN_SECONDS * 360f;
            icon.rotation = spin;
            icon.previousRotation = spin;
        }
    }

    /** Places a mounted part: mount-space pixels (y down) mirrored when aiming left, rotated by the aim. */
    private void mount(int part, Transform owner, float px, float pyDown, float degrees, boolean left, boolean visible) {
        if (part < 0 || !world().isAlive(part)) {
            return;
        }
        float lx = px * PX;
        float ly = (left ? pyDown : -pyDown) * PX;
        float c = MathUtils.cosDeg(degrees);
        float s = MathUtils.sinDeg(degrees);
        Transform t = world().get(part, Transform.class);
        t.position.set(owner.position.x + lx * c - ly * s, owner.position.y + lx * s + ly * c);
        t.previous.set(owner.previous.x + lx * c - ly * s, owner.previous.y + lx * s + ly * c);
        t.rotation = degrees;
        t.previousRotation = degrees;
        Sprite sprite = world().get(part, Sprite.class);
        sprite.flipY = left;
        sprite.visible = visible;
    }

    private void show(int part, boolean visible) {
        if (part >= 0 && world().isAlive(part)) {
            world().get(part, Sprite.class).visible = visible;
        }
    }

    /**
     * The original's hand tracks: right x, right y, left x, left y (mount-space pixels, y down) for a clip at a time.
     */
    static float[] hands(String clip, float time) {
        float t = time % 0.4f;
        switch (clip) {
            case "walk": {
                float[] rx = {-5f, -4f, -3f, -2f};
                float[] lx = {6f, 5f, 4f, 2f};
                float[] y = {3f, 3.5f, 3.5f, 3f};
                int k = t < 0.1f ? 0 : t < 0.2f ? 1 : t < 0.4f - 1e-4f ? 2 : 3;
                return new float[] {rx[k], y[k], lx[k], y[k]};
            }
            case "hit": {
                float y = time < 0.1f ? 3f + time * 10f : Math.max(3f, 4f - (time - 0.1f) * 5f);
                return new float[] {-4f, y, 5f, y};
            }
            default: {   // idle: hands rise half a pixel and settle
                float y = t < 0.1f ? 3f + t * 5f : t < 0.2f ? 3.5f : 3.5f - (t - 0.2f) * 2.5f;
                return new float[] {-4f, y, 5f, y};
            }
        }
    }
}
