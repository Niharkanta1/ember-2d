package dev.ember2d.demo.gold;

import com.badlogic.gdx.math.MathUtils;
import dev.ember2d.animation.Animator;
import dev.ember2d.audio.Audio;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.RayHit;
import dev.ember2d.core.Phase;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.kit.bullets.BulletTarget;
import dev.ember2d.kit.bullets.BulletWorld;
import dev.ember2d.kit.common.Damaged;
import dev.ember2d.kit.common.Died;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.kit.common.Hurtbox;
import dev.ember2d.kit.topdown.TopDownBody;
import dev.ember2d.kit.topdown.TopDownSystem;
import dev.ember2d.kit.topdown.ai.PathPlanner;
import dev.ember2d.kit.topdown.ai.Steering;
import dev.ember2d.kit.topdown.shooter.ShotEvents;
import dev.ember2d.render.Sprite;


/**
 * The creatures' minds (F-029), ported from the original's {@code Enemy}, its behaviours and attacks, and
 * {@code EnemySystem}. In {@code FIXED}, before kit-topdown's {@code AiSystem} (which paths to the point chosen here)
 * and {@code TopDownSystem} (which moves against the walls):
 * <ul>
 *   <li><b>States:</b> idle, active (has the player as target), attacking (windup, then recovery), stunned, dead.</li>
 *   <li><b>Perception:</b> spots the player within {@code detect} with line of sight, loses it beyond {@code lose};
 *       shows "!" for 0.9 s and alerts creatures within 90 px. Being hurt always makes it aware.</li>
 *   <li><b>Noise:</b> a shot within 110 px sends creatures without a target to look ("?"), giving up after 8 s or
 *       2.5 s after arriving.</li>
 *   <li><b>Behaviours:</b> chase (stop at a distance), keep distance and strafe, wander around home, patrol the
 *       level's loop, stand still.</li>
 *   <li><b>Attacks:</b> melee bite with a lunge, ranged spit or bolt fan through kit-bullets, a dash that hits on
 *       contact; each telegraphed (squash, glow or shake) during the windup.</li>
 *   <li><b>Hurt and death:</b> stun (unless attacking and not interruptible), knockback, a 2.5 s health bar; dead
 *       creatures stop, fade out over 0.25 s and drop a gold pouch.</li>
 * </ul>
 */
final class CreatureSystem extends EntitySystem {

    static final float PX = 1f / 16f;
    static final float ALERT_SECONDS = 0.9f;
    static final float ALLY_ALERT_RADIUS = 90f * PX;
    static final float HEARING_RADIUS = 110f * PX;
    static final float INVESTIGATE_GIVE_UP = 8f;
    static final float INVESTIGATE_LINGER = 2.5f;
    static final float HEALTH_BAR_SECONDS = 2.5f;
    static final float DEATH_FADE_SECONDS = 0.25f;
    static final float PATROL_PAUSE = 1.2f;
    static final float ARRIVED = 4f * PX;
    static final float PLAYER_RADIUS = 6f * PX;
    static final float PLAYER_KNOCKBACK = 110f * PX;
    static final float ATTACK_RANGE_FRACTION = 0.8f;
    static final float AIM_JITTER = 0.04f;
    static final int CREATURE_TEAM = 2;

    private final CreatureCatalog catalog;
    private final Collision collision;
    private final HealthSystem health;
    private final BulletWorld bullets;
    private final TopDownSystem movement;
    private final PrefabLibrary prefabs;
    private final PathPlanner planner;
    private final Audio audio;
    private final RayHit hit = new RayHit();
    private int player = World.NONE;
    private float px;
    private float py;
    private boolean playerAlive;

    CreatureSystem(World world, CreatureCatalog catalog, Collision collision, HealthSystem health, BulletWorld bullets,
            TopDownSystem movement, PrefabLibrary prefabs, PathPlanner planner, EventBus events, Audio audio) {
        super(Phase.FIXED, world.family(Creature.class, Transform.class, TopDownBody.class, Steering.class));
        this.catalog = catalog;
        this.collision = collision;
        this.health = health;
        this.bullets = bullets;
        this.movement = movement;
        this.prefabs = prefabs;
        this.planner = planner;
        this.audio = audio;
        events.on(Damaged.class, e -> {
            Creature c = world.get(e.target(), Creature.class);
            if (c != null) {
                hurt(e.target(), c, e.healthLeft());
            }
        });
        events.on(Died.class, e -> {
            Creature c = world.get(e.entity(), Creature.class);
            if (c != null) {
                die(e.entity(), c);
            }
        });
        events.on(ShotEvents.Fired.class, e -> hear(e.x, e.y));
    }

    // ------------------------------------------------------------------ frame

    @Override
    protected void begin() {
        player = world().findByName("player");
        Transform t = player == World.NONE ? null : world().get(player, Transform.class);
        Health h = player == World.NONE ? null : world().get(player, Health.class);
        playerAlive = t != null && h != null && !h.dead;
        if (t != null) {
            px = t.position.x;
            py = t.position.y;
        }
    }

    @Override
    protected void process(int e, float delta) {
        Creature c = world().get(e, Creature.class);
        CreatureCatalog.Def d = catalog.get(c.kind);
        Transform t = world().get(e, Transform.class);
        TopDownBody body = world().get(e, TopDownBody.class);
        Steering steering = world().get(e, Steering.class);
        Sprite sprite = world().get(e, Sprite.class);
        if (c.state == Creature.State.DEAD) {
            steering.mode = Steering.Mode.NONE;
            body.locked = true;
            c.deadFor += delta;
            if (sprite != null) {
                sprite.tint.a = Math.max(0f, 1f - c.deadFor / DEATH_FADE_SECONDS);
            }
            if (c.deadFor >= DEATH_FADE_SECONDS) {
                world().destroy(e);
            }
            return;
        }
        if (Float.isNaN(c.homeX)) {
            start(c, t);
        }
        c.cooldown = Math.max(0f, c.cooldown - delta);
        c.alertLeft = Math.max(0f, c.alertLeft - delta);
        c.healthBarLeft = Math.max(0f, c.healthBarLeft - delta);
        updateTarget(e, c, d, t);
        float dist = distance(t);
        boolean sight = playerAlive && clear(t.position.x, t.position.y, px, py);
        float speed = d.speed;
        switch (c.state) {
            case STUNNED -> {
                stop(steering);
                c.stunLeft -= delta;
                if (c.stunLeft <= 0f) {
                    c.state = c.hasTarget ? Creature.State.ACTIVE : Creature.State.IDLE;
                }
            }
            case ATTACKING -> {
                stop(steering);
                attackStep(e, c, d, t, delta);
            }
            default -> {
                c.state = c.hasTarget ? Creature.State.ACTIVE : Creature.State.IDLE;
                speed *= move(c, d, t, steering, dist, sight, delta);
                if (c.hasTarget && c.cooldown <= 0f && dist <= d.range && (!d.attackSight || sight)) {
                    c.state = Creature.State.ATTACKING;
                    c.winding = true;
                    c.attackLeft = d.windup;
                    c.attackHit = false;
                    stop(steering);
                }
            }
        }
        body.maxSpeed = speed;
        body.acceleration = Math.max(1f, d.speed * d.acceleration);
        body.friction = body.acceleration;
        look(e, c, d, t, body, sprite, delta);
    }

    private void start(Creature c, Transform t) {
        c.homeX = t.position.x;
        c.homeY = t.position.y;
        c.pointX = c.homeX;
        c.pointY = c.homeY;
        c.wait = MathUtils.random(0.5f, 2f);
        c.route = parseRoute(c.patrol);
    }

    static float[] parseRoute(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] points = text.split(";");
        float[] out = new float[points.length * 2];
        for (int i = 0; i < points.length; i++) {
            String[] xy = points[i].split(",");
            out[i * 2] = Float.parseFloat(xy[0].trim());
            out[i * 2 + 1] = Float.parseFloat(xy[1].trim());
        }
        return out;
    }

    // ------------------------------------------------------------------ perception

    private void updateTarget(int e, Creature c, CreatureCatalog.Def d, Transform t) {
        if (c.hasTarget) {
            if (!playerAlive || distance(t) > d.lose) {
                c.hasTarget = false;
                c.alertLeft = 0f;
            }
            return;
        }
        if (!playerAlive || distance(t) > d.detect) {
            return;
        }
        if (d.lineOfSight && !clear(t.position.x, t.position.y, px, py)) {
            return;
        }
        acquire(e, c, true);
    }

    private void acquire(int e, Creature c, boolean alertOthers) {
        if (c.hasTarget || c.state == Creature.State.DEAD || !playerAlive) {
            return;
        }
        c.hasTarget = true;
        c.investigating = false;
        c.alertLeft = ALERT_SECONDS;
        audio.playVaried("sfx.alert", 0.08f);
        if (!alertOthers) {
            return;
        }
        Transform me = world().get(e, Transform.class);
        for (int other : family().entities().toArray()) {
            if (other == e) {
                continue;
            }
            Transform o = world().get(other, Transform.class);
            if (o != null && o.position.dst(me.position) <= ALLY_ALERT_RADIUS) {
                acquire(other, world().get(other, Creature.class), false);
            }
        }
    }

    private void hear(float x, float y) {
        for (int e : family().entities().toArray()) {
            Creature c = world().get(e, Creature.class);
            Transform t = world().get(e, Transform.class);
            if (c.state == Creature.State.DEAD || c.hasTarget || t.position.dst(x, y) > HEARING_RADIUS) {
                continue;
            }
            c.investigating = true;
            c.investigateX = x;
            c.investigateY = y;
            c.investigateLeft = INVESTIGATE_GIVE_UP;
        }
    }

    private boolean clear(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        return !collision.raycast(x0, y0, x1, y1, Walls.SIGHT_MASK, hit) || hit.distance >= dist - 1e-3f;
    }

    private float distance(Transform t) {
        return playerAlive ? t.position.dst(px, py) : Float.MAX_VALUE;
    }

    // ------------------------------------------------------------------ movement

    /** Chooses where to go this step; returns the speed factor. */
    private float move(Creature c, CreatureCatalog.Def d, Transform t, Steering s, float dist, boolean sight,
            float delta) {
        float x = t.position.x;
        float y = t.position.y;
        if (!c.hasTarget && c.investigating) {
            if (t.position.dst(c.investigateX, c.investigateY) < ARRIVED * 2f) {
                c.investigateLeft = Math.min(c.investigateLeft, INVESTIGATE_LINGER);
            }
            c.investigateLeft -= delta;
            if (c.investigateLeft <= 0f) {
                c.investigating = false;
            }
            go(s, c.investigateX, c.investigateY);
            return 1f;
        }
        if (c.hasTarget) {
            if (d.behavior.equals("keepDistance")) {
                if (d.chaseWithoutSight && !sight) {
                    go(s, px, py);
                    return d.behaviorSpeed;
                }
                float ox = x - px;
                float oy = y - py;
                float len = (float) Math.sqrt(ox * ox + oy * oy);
                if (len < 0.01f) {
                    ox = 1f;
                    oy = 0f;
                    len = 1f;
                }
                float wanted = MathUtils.clamp(len, d.preferred - d.tolerance, d.preferred + d.tolerance);
                float angle = MathUtils.atan2(oy, ox);
                if (d.strafe) {
                    c.strafeLeft -= delta;
                    if (c.strafeLeft <= 0f) {
                        c.strafeLeft = MathUtils.random(d.minSwitch, d.maxSwitch);
                        if (MathUtils.randomBoolean()) {
                            c.strafeDirection = -c.strafeDirection;
                        }
                    }
                    angle += d.strafeAngle * MathUtils.degreesToRadians * c.strafeDirection;
                }
                float gx = px + MathUtils.cos(angle) * wanted;
                float gy = py + MathUtils.sin(angle) * wanted;
                if (planner != null && !planner.walkable(gx, gy)) {
                    c.strafeDirection = -c.strafeDirection;   // slid into a wall: turn the other way
                    gx = x;
                    gy = y;
                }
                go(s, gx, gy);
                return d.behaviorSpeed;
            }
            float stopAt = d.stop > 0f ? d.stop : d.range * ATTACK_RANGE_FRACTION;
            if (dist <= stopAt && sight) {
                stop(s);
            } else {
                go(s, px, py);
            }
            return d.behaviorSpeed;
        }
        if (c.route != null) {
            int n = c.route.length / 2;
            float wx = c.route[c.patrolIndex * 2];
            float wy = c.route[c.patrolIndex * 2 + 1];
            if (t.position.dst(wx, wy) < ARRIVED) {
                c.patrolPause += delta;
                if (c.patrolPause >= PATROL_PAUSE) {
                    c.patrolPause = 0f;
                    c.patrolIndex = (c.patrolIndex + 1) % n;
                }
            }
            go(s, c.route[c.patrolIndex * 2], c.route[c.patrolIndex * 2 + 1]);
            return d.patrolSpeed;
        }
        if (d.idle.equals("wander")) {
            if (t.position.dst(c.pointX, c.pointY) < ARRIVED) {
                c.wait -= delta;
                if (c.wait <= 0f) {
                    c.wait = MathUtils.random(d.minWait, d.maxWait);
                    float angle = MathUtils.random(MathUtils.PI2);
                    float r = MathUtils.random(0.3f, 1f) * d.wanderRadius;
                    float gx = c.homeX + MathUtils.cos(angle) * r;
                    float gy = c.homeY + MathUtils.sin(angle) * r;
                    if (planner == null || planner.walkable(gx, gy)) {
                        c.pointX = gx;
                        c.pointY = gy;
                    }
                }
            }
            go(s, c.pointX, c.pointY);
            return d.idleSpeed;
        }
        stop(s);   // stand still (the ghoul, waiting in the dark)
        return 1f;
    }

    private static void go(Steering s, float x, float y) {
        s.mode = Steering.Mode.FOLLOW_PATH;
        s.target = null;
        s.targetX = x;
        s.targetY = y;
        s.sight = 0f;
        s.arriveRadius = ARRIVED;
    }

    private static void stop(Steering s) {
        s.mode = Steering.Mode.NONE;
    }

    // ------------------------------------------------------------------ attacks

    private void attackStep(int e, Creature c, CreatureCatalog.Def d, Transform t, float delta) {
        if (!c.winding && d.attack.equals("dash") && c.hasTarget) {
            contact(e, c, d, t);
        }
        c.attackLeft -= delta;
        if (c.attackLeft > 0f) {
            return;
        }
        if (c.winding) {
            if (c.hasTarget && playerAlive) {
                execute(e, c, d, t);
            }
            c.winding = false;
            c.attackLeft = d.recovery;
        } else {
            c.cooldown = d.cooldown;
            c.state = c.hasTarget ? Creature.State.ACTIVE : Creature.State.IDLE;
        }
    }

    private void execute(int e, Creature c, CreatureCatalog.Def d, Transform t) {
        float dx = px - t.position.x;
        float dy = py - t.position.y;
        float len = Math.max(1e-4f, (float) Math.sqrt(dx * dx + dy * dy));
        float nx = dx / len;
        float ny = dy / len;
        switch (d.attack) {
            case "melee" -> {
                if (d.lunge > 0f) {
                    movement.knockback(e, nx * d.lunge, ny * d.lunge);
                }
                audio.playVaried("sfx.bite", 0.1f);
                if (len <= d.range + d.leeway) {
                    damagePlayer(e, d.damage, nx, ny);
                }
            }
            case "ranged" -> {
                float base = MathUtils.atan2(dy, dx);
                float spread = d.spread * MathUtils.degreesToRadians;
                int type = bullets.types().index(d.bullet);
                for (int i = 0; i < d.count; i++) {
                    float k = d.count == 1 ? 0.5f : (float) i / (d.count - 1);
                    float angle = base + MathUtils.lerp(-spread, spread, k) * 0.5f
                            + MathUtils.random(-AIM_JITTER, AIM_JITTER);
                    float x = t.position.x + MathUtils.cos(angle) * d.offset;
                    float y = t.position.y + MathUtils.sin(angle) * d.offset;
                    bullets.spawn(type, x, y, angle * MathUtils.radiansToDegrees, d.bulletSpeed, CREATURE_TEAM);
                }
                audio.playVaried(d.bullet.equals("spit") ? "sfx.spit" : "sfx.bolt", 0.08f);
            }
            case "dash" -> {
                movement.knockback(e, nx * d.dashSpeed, ny * d.dashSpeed);
                audio.playVaried("sfx.dash", 0.08f);
                contact(e, c, d, t);
            }
            default -> throw new IllegalStateException("Unknown attack \"" + d.attack + "\" for " + c.kind);
        }
    }

    private void contact(int e, Creature c, CreatureCatalog.Def d, Transform t) {
        if (c.attackHit || !playerAlive) {
            return;
        }
        float dist = t.position.dst(px, py);
        if (dist <= d.radius + PLAYER_RADIUS + d.padding) {
            c.attackHit = true;
            float len = Math.max(1e-4f, dist);
            damagePlayer(e, d.damage, (px - t.position.x) / len, (py - t.position.y) / len);
        }
    }

    private void damagePlayer(int source, int amount, float nx, float ny) {
        health.damage(player, amount, source, nx * PLAYER_KNOCKBACK, ny * PLAYER_KNOCKBACK);
    }

    // ------------------------------------------------------------------ hurt and death

    private void hurt(int e, Creature c, int healthLeft) {
        if (c.state == Creature.State.DEAD) {
            return;
        }
        CreatureCatalog.Def d = catalog.get(c.kind);
        c.healthBarLeft = HEALTH_BAR_SECONDS;
        audio.playVaried("sfx.enemy_hit", 0.1f);
        acquire(e, c, true);   // being hurt always makes it aware
        Transform t = world().get(e, Transform.class);
        if (t != null && d.knockback > 0f && playerAlive) {
            float dx = t.position.x - px;
            float dy = t.position.y - py;
            float len = Math.max(1e-4f, (float) Math.sqrt(dx * dx + dy * dy));
            movement.knockback(e, dx / len * d.knockback, dy / len * d.knockback);
        }
        if (healthLeft <= 0) {
            return;   // Died follows
        }
        if (c.state == Creature.State.ATTACKING && !d.interruptible) {
            return;
        }
        if (d.stun > 0f) {
            c.state = Creature.State.STUNNED;
            c.stunLeft = d.stun;
        }
    }

    private void die(int e, Creature c) {
        c.state = Creature.State.DEAD;
        c.deadFor = 0f;
        c.alertLeft = 0f;
        c.investigating = false;
        c.healthBarLeft = 0f;
        world().remove(e, BulletTarget.class);   // shots pass through the fading body
        world().remove(e, Hurtbox.class);
        audio.playVaried("sfx.enemy_die", 0.08f);
        Transform t = world().get(e, Transform.class);
        if (t != null && c.gold > 0) {
            PickupSystem.drop(world(), prefabs, "pickup/pouch", t.position.x, t.position.y, c.gold);
        }
    }

    // ------------------------------------------------------------------ look

    private void look(int e, Creature c, CreatureCatalog.Def d, Transform t, TopDownBody body, Sprite sprite,
            float delta) {
        Animator animator = world().get(e, Animator.class);
        boolean moving = body.moving;
        if (animator != null) {
            animator.speed = moving ? 1f : d.idleFrame > 0f ? d.walkFrame / d.idleFrame : 0f;
        }
        if (sprite == null) {
            return;
        }
        boolean dormant = c.state == Creature.State.IDLE && !c.investigating;
        sprite.tint.a = dormant ? d.dormantAlpha : 1f;
        float sx = 1f;
        float sy = 1f;
        float jitter = 0f;
        if (c.state == Creature.State.ATTACKING && c.winding) {
            float k = 1f - Math.max(0f, c.attackLeft) / Math.max(1e-4f, d.windup);   // 0 -> 1 over the windup
            switch (d.telegraph) {
                case "squash" -> {
                    sx = 1f + 0.25f * k;
                    sy = 1f - 0.25f * k;
                }
                case "glow" -> {
                    sprite.flashColor.set(Hud.CREAM);
                    sprite.flash = 0.35f + 0.35f * MathUtils.sin(k * MathUtils.PI * 6f);
                }
                case "shake" -> jitter = MathUtils.random(-1f, 1f) * PX * (0.5f + k);
                default -> { }
            }
        } else if (d.telegraph.equals("glow") && sprite.flash > 0f && sprite.flashColor.equals(Hud.CREAM)) {
            sprite.flash = 0f;
        }
        t.scale.set(sx, sy);
        if (d.wobble && moving) {
            c.wobble += delta;
            t.rotation = MathUtils.sin(c.wobble * 18f) * 6f;
        } else {
            t.rotation = 0f;
        }
        t.previousRotation = t.rotation;
        sprite.origin.x = 12f + jitter * 16f;
    }
}
