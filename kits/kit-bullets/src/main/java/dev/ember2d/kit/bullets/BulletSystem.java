package dev.ember2d.kit.bullets;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.RayHit;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.kit.common.Team;
import java.util.List;

/**
 * Runs bullets for a scene (F-024), in {@code FIXED}: pattern runners emit, bullets move, child emitters fire, wall
 * bullets stop at walls, then bullets are tested against {@link BulletTarget}s of other teams (circle against circle,
 * through a uniform grid rebuilt every step). Hits queue {@link BulletHit} and apply damage through kit-common; near
 * misses queue {@link Grazed}. No objects are created per bullet or per step after warm-up (events excepted).
 */
@PublicApi
public final class BulletSystem extends EntitySystem {

    private static final float CELL = 2f;

    /** One running emitter; pooled. Emitters of one pattern share a handle. */
    private static final class Runner {
        Emitter emitter;
        int handle;
        int origin;
        float originX;
        float originY;
        int target;
        int team;
        float time;
        int volley;
        boolean active;
    }

    private final BulletWorld bullets;
    private final BulletPatterns patterns;
    private final EventBus events;
    private final HealthSystem health;
    private final Time clock;
    private final Array<Runner> runners = new Array<>(false, 16);
    private final IntMap<IntArray> grid = new IntMap<>();
    private final IntArray targets = new IntArray();
    private final RayHit ray = new RayHit();
    private final BulletClearListener toPickups;
    private Collision walls;
    private int wallMask = 1;
    private int nextHandle = 1;
    private long seed = 0x2545F4914F6CDD1DL;
    private float reach;

    /**
     * @param patterns may be null when only {@link #runEmitter} or direct spawns are used
     * @param health applies the damage of hits; null to only publish {@link BulletHit}
     * @param time the engine time ({@code context.time()}); bullets step by its fixed delta
     */
    public BulletSystem(World world, BulletWorld bullets, BulletPatterns patterns, EventBus events, HealthSystem health,
            Time time) {
        super(Phase.FIXED, world.family(BulletTarget.class, Transform.class));
        this.clock = time;
        this.bullets = bullets;
        this.patterns = patterns;
        this.events = events;
        this.health = health;
        this.toPickups = (x, y) -> events.queue(new BulletCleared(x, y));
    }

    public BulletWorld bullets() {
        return bullets;
    }

    /** Told where a wall bullet stopped at a wall (impact effects); called during the step, allocates nothing. */
    @FunctionalInterface
    public interface WallStopListener {
        void stopped(int type, float x, float y);
    }

    private WallStopListener wallStops;

    /** Sets who hears wall stops (null: nobody). */
    public void setWallStopListener(WallStopListener listener) {
        this.wallStops = listener;
    }

    /** Wall bullets ({@code "walls": true}) stop at tiles and colliders on these layers of the collision. */
    public void setWallMask(int mask, Collision collision) {
        this.wallMask = mask;
        this.walls = collision;
    }

    /** Seeds the random generator of {@code random} emitters (runs are deterministic for a seed). */
    public void setSeed(long seed) {
        this.seed = seed == 0 ? 1 : seed;
    }

    // ---- running patterns ----

    /**
     * Runs a pattern from an entity's position (the emitters follow it; they stop when it dies), aiming at
     * {@code target} (or {@code World.NONE}). Returns a handle for {@link #stop}.
     */
    public int run(String pattern, int origin, int target, int team) {
        return start(patterns.get(pattern).emitters(), origin, 0f, 0f, target, team);
    }

    /** Runs a pattern from a fixed point. */
    public int runAt(String pattern, float x, float y, int target, int team) {
        return start(patterns.get(pattern).emitters(), World.NONE, x, y, target, team);
    }

    /** Runs one emitter (for example a weapon's) from an entity's position, or a point when origin is NONE. */
    public int runEmitter(Emitter emitter, int origin, float x, float y, int target, int team) {
        int handle = nextHandle++;
        startOne(emitter, handle, origin, x, y, target, team);
        return handle;
    }

    private int start(List<Emitter> emitters, int origin, float x, float y, int target, int team) {
        int handle = nextHandle++;
        for (int i = 0; i < emitters.size(); i++) {
            startOne(emitters.get(i), handle, origin, x, y, target, team);
        }
        return handle;
    }

    private void startOne(Emitter emitter, int handle, int origin, float x, float y, int target, int team) {
        Runner r = null;
        for (int i = 0; i < runners.size; i++) {
            if (!runners.get(i).active) {
                r = runners.get(i);
                break;
            }
        }
        if (r == null) {
            r = new Runner();
            runners.add(r);
        }
        r.emitter = emitter;
        r.handle = handle;
        r.origin = origin;
        r.originX = x;
        r.originY = y;
        r.target = target;
        r.team = team;
        r.time = 0f;
        r.volley = 0;
        r.active = true;
        if (emitter.delay <= 0f) {
            emit(r, 0f);   // the first volley goes out at once
        }
    }

    /**
     * Fires one volley of an emitter now, with its angle replaced by {@code angleDeg} (weapons, F-016): spread, count,
     * speed and bullet come from the emitter; {@code repeat} and {@code delay} are ignored. No allocation.
     */
    public void fire(Emitter emitter, float x, float y, float angleDeg, int target, int team) {
        volley(emitter, 0, x, y, target, team, angleDeg);
    }

    public void stop(int handle) {
        for (int i = 0; i < runners.size; i++) {
            if (runners.get(i).handle == handle) {
                runners.get(i).active = false;
            }
        }
    }

    /** Stops every runner that follows this entity. */
    public void stopAll(int origin) {
        for (int i = 0; i < runners.size; i++) {
            if (runners.get(i).origin == origin) {
                runners.get(i).active = false;
            }
        }
    }

    public boolean running(int handle) {
        for (int i = 0; i < runners.size; i++) {
            Runner r = runners.get(i);
            if (r.active && r.handle == handle) {
                return true;
            }
        }
        return false;
    }

    /** Removes the bullets of a team (-1 for all); with {@code toPickups}, queues {@link BulletCleared} for each. */
    public int clear(int team, boolean pickups) {
        return bullets.clear(team, pickups ? toPickups : null);
    }

    // ---- the step ----

    @Override
    protected void begin() {
        float dt = time();
        for (int i = 0; i < runners.size; i++) {
            Runner r = runners.get(i);
            if (r.active) {
                r.time += dt;
                emit(r, dt);
            }
        }
        bullets.update(dt);
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Emitter c = bullets.child(i);
            if (c != null && bullets.age(i) >= bullets.childAfter(i)) {
                runEmitter(c, World.NONE, bullets.x(i), bullets.y(i), bullets.target(i), bullets.team(i));
                bullets.remove(i);
            }
        }
        if (walls != null) {
            for (int i = bullets.size() - 1; i >= 0; i--) {
                if (bullets.types().get(bullets.type(i)).walls
                        && walls.raycast(bullets.lastX(i), bullets.lastY(i), bullets.x(i), bullets.y(i), wallMask, ray)) {
                    if (wallStops != null) {
                        wallStops.stopped(bullets.type(i), ray.x, ray.y);
                    }
                    bullets.remove(i);
                }
            }
        }
        for (IntMap.Entry<IntArray> cell : grid) {
            cell.value.clear();
        }
        targets.clear();
        reach = 0f;
    }

    private float time() {
        return clock.fixedDelta();
    }

    @Override
    protected void process(int entity, float delta) {
        BulletTarget t = world().get(entity, BulletTarget.class);
        Transform tr = world().get(entity, Transform.class);
        float cx = tr.position.x + t.offsetX;
        float cy = tr.position.y + t.offsetY;
        int key = key(cell(cx), cell(cy));
        IntArray list = grid.get(key);
        if (list == null) {
            list = new IntArray();
            grid.put(key, list);
        }
        list.add(entity);
        targets.add(entity);
        reach = Math.max(reach, Math.max(t.radius, t.grazeRadius));
    }

    @Override
    protected void end() {
        if (targets.size == 0) {
            return;
        }
        World world = world();
        for (int i = bullets.size() - 1; i >= 0; i--) {
            BulletType type = bullets.types().get(bullets.type(i));
            float bx = bullets.x(i);
            float by = bullets.y(i);
            float br = bullets.radius(i);
            float r = reach + br;
            int team = bullets.team(i);
            boolean removed = false;
            for (int gx = cell(bx - r); gx <= cell(bx + r) && !removed; gx++) {
                for (int gy = cell(by - r); gy <= cell(by + r) && !removed; gy++) {
                    IntArray list = grid.get(key(gx, gy));
                    if (list == null) {
                        continue;
                    }
                    for (int k = 0; k < list.size; k++) {
                        int target = list.get(k);
                        if (teamOf(world, target) == team) {
                            continue;
                        }
                        BulletTarget t = world.get(target, BulletTarget.class);
                        Transform tr = world.get(target, Transform.class);
                        float dx = tr.position.x + t.offsetX - bx;
                        float dy = tr.position.y + t.offsetY - by;
                        float d2 = dx * dx + dy * dy;
                        float hit = br + t.radius;
                        if (d2 <= hit * hit) {
                            if (type.pierce && bullets.pierce(i, target)) {
                                continue;   // this piercing bullet already hit this target
                            }
                            events.queue(new BulletHit(target, bullets.type(i), bullets.damage(i), bx, by, team));
                            if (health != null) {
                                health.damage(target, bullets.damage(i), World.NONE, 0f, 0f);
                            }
                            if (!type.pierce) {
                                bullets.remove(i);
                                removed = true;
                                break;
                            }
                        } else if (t.grazeRadius > 0f && type.graze && !bullets.grazed(i)) {
                            float graze = br + t.grazeRadius;
                            if (d2 <= graze * graze) {
                                bullets.setGrazed(i);
                                events.queue(new Grazed(target, bx, by));
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- emitting ----

    private void emit(Runner r, float dt) {
        Emitter e = r.emitter;
        float x = r.originX;
        float y = r.originY;
        if (r.origin != World.NONE) {
            Transform t = world().isAlive(r.origin) ? world().get(r.origin, Transform.class) : null;
            if (t == null) {
                r.active = false;
                return;
            }
            x = t.position.x;
            y = t.position.y;
        }
        while (r.volley < e.repeat && r.time >= e.delay + r.volley * e.interval) {
            volley(e, r.volley, x, y, r.target, r.team, Float.NaN);
            r.volley++;
        }
        if (r.volley >= e.repeat) {
            r.active = false;
        }
    }

    private void volley(Emitter e, int volley, float x, float y, int target, int team, float angleOverride) {
        float base = (Float.isNaN(angleOverride) ? e.angle : angleOverride) + e.angleStep * volley;
        if (e.type == Emitter.Type.AIMED && Float.isNaN(angleOverride)) {
            Transform t = target != World.NONE && world().isAlive(target) ? world().get(target, Transform.class) : null;
            if (t != null) {
                base = (float) Math.toDegrees(Math.atan2(t.position.y - y, t.position.x - x)) + e.angleStep * volley;
            }
        }
        float speed = e.speed + e.speedStep * volley;
        int n = e.count;
        for (int i = 0; i < n; i++) {
            float angle = switch (e.type) {
                case SINGLE -> base;
                case RING, SPIRAL -> base + i * 360f / n;
                case ARC, AIMED -> n == 1 ? base : base - e.spread / 2f + i * e.spread / (n - 1);
                case RANDOM -> base - e.spread / 2f + random() * e.spread;
                case WAVE -> (n == 1 ? base : base - e.spread / 2f + i * e.spread / (n - 1))
                        + e.amplitude * (float) Math.sin(Math.PI * 2 * volley / e.frequency);
            };
            float sx = e.offset == 0f ? x : x + (float) Math.cos(Math.toRadians(angle)) * e.offset;
            float sy = e.offset == 0f ? y : y + (float) Math.sin(Math.toRadians(angle)) * e.offset;
            int slot = bullets.spawn(e.bullet, sx, sy, angle, speed, team);
            if (e.accel != 0f) {
                bullets.setAccel(slot, e.accel);
            }
            if (e.turn != 0f) {
                bullets.setTurn(slot, e.turn);
            }
            if (e.child != null) {
                bullets.setChild(slot, e.child, e.childAfter, target);
            }
        }
    }

    /** xorshift64*: deterministic, allocation-free; 0 to 1. */
    private float random() {
        seed ^= seed >>> 12;
        seed ^= seed << 25;
        seed ^= seed >>> 27;
        long v = seed * 0x2545F4914F6CDD1DL;
        return (v >>> 40) / (float) (1L << 24);
    }

    private static int teamOf(World world, int entity) {
        Team t = world.get(entity, Team.class);
        return t == null ? 0 : t.id;
    }

    private static int cell(float v) {
        return MathUtils.floor(v / CELL);
    }

    private static int key(int cx, int cy) {
        return (cx & 0xFFFF) << 16 | (cy & 0xFFFF);
    }
}
