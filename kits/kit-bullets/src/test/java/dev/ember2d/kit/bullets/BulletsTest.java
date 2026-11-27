package dev.ember2d.kit.bullets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.Solid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.kit.common.Team;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The bullet kit (F-024, I-325): types, emitters, collision, clearing, capacity and allocation. */
class BulletsTest {

    static final String TYPES = """
            { "version": 1, "types": {
                "orb":    { "region": "tex.orb", "radius": 0.15, "damage": 2, "lifetime": 20 },
                "pellet": { "region": "tex.pellet", "radius": 0.1, "walls": true },
                "laser":  { "region": "tex.laser", "radius": 0.1, "pierce": true, "graze": false } } }
            """;

    static final class FakeAssets implements Assets {
        final Map<String, JsonValue> files = new HashMap<>();

        @Override
        public <T> T get(String id, Class<T> type) {
            return type.cast(files.get(id));
        }

        @Override
        public TextureRegion region(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncLoad loadGroup(String group) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void unloadGroup(String group) {
        }

        @Override
        public boolean isLoaded(String id) {
            return true;
        }

        @Override
        public boolean contains(String id) {
            return files.containsKey(id);
        }
    }

    final BulletTypes types = BulletTypes.parse(new JsonReader().parse(TYPES), "bullets.json");
    final FakeAssets assets = new FakeAssets();
    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(1f / 60f);
    final BulletWorld bullets = new BulletWorld(4096, types);
    final HealthSystem health = new HealthSystem(world, events);
    final BulletSystem system = new BulletSystem(world, bullets, new BulletPatterns(assets, types), events, health, time);
    final List<Object> seen = new ArrayList<>();

    BulletsTest() {
        world.addSystem(health);
        world.addSystem(system);
        for (Class<?> c : List.of(BulletHit.class, Grazed.class, BulletCleared.class)) {
            events.onGlobal(c, seen::add);
        }
    }

    void pattern(String id, String json) {
        assets.files.put(id, new JsonReader().parse(json));
    }

    void steps(int n) {
        for (int i = 0; i < n; i++) {
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    float angle(int i) {
        float a = (float) Math.toDegrees(Math.atan2(bullets.vy(i), bullets.vx(i)));
        return (a + 360f) % 360f;
    }

    List<Float> angles() {
        List<Float> out = new ArrayList<>();
        for (int i = 0; i < bullets.size(); i++) {
            out.add((float) Math.round(angle(i) * 100f) / 100f);
        }
        out.sort(Float::compare);
        return out;
    }

    int entity(float x, float y) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        return e;
    }

    // ---- emitters ----

    @Test
    void ringArcAndSingleSpreadTheirBullets() {
        pattern("ring", "{ \"version\": 1, \"emit\": { \"type\": \"ring\", \"bullet\": \"orb\", \"count\": 4, "
                + "\"angle\": 0, \"speed\": 2 } }");
        system.runAt("ring", 1f, 2f, World.NONE, 1);
        assertThat(bullets.size()).as("the first volley goes out at once").isEqualTo(4);
        assertThat(angles()).containsExactly(0f, 90f, 180f, 270f);
        assertThat(bullets.x(0)).isEqualTo(1f);
        assertThat(Math.hypot(bullets.vx(1), bullets.vy(1))).isCloseTo(2.0, within(1e-5));
        bullets.clear(-1, null);

        pattern("arc", "{ \"version\": 1, \"emit\": [ { \"type\": \"arc\", \"bullet\": \"orb\", \"count\": 3, "
                + "\"angle\": 90, \"spread\": 90 }, { \"type\": \"single\", \"bullet\": \"orb\", \"angle\": 300 } ] }");
        system.runAt("arc", 0f, 0f, World.NONE, 1);
        assertThat(angles()).containsExactly(45f, 90f, 135f, 300f);
    }

    @Test
    void aimedFollowsTheTargetAndRunnersFollowTheirOrigin() {
        pattern("aim", "{ \"version\": 1, \"emit\": { \"type\": \"aimed\", \"bullet\": \"orb\", \"count\": 1, "
                + "\"repeat\": 2, \"interval\": 0.5 } }");
        int boss = entity(0f, 0f);
        int player = entity(0f, -5f);
        int handle = system.run("aim", boss, player, 2);
        assertThat(angles()).containsExactly(270f);
        world.get(player, Transform.class).teleport(5f, 0f);
        world.get(boss, Transform.class).teleport(0f, 3f);
        steps(30);
        assertThat(bullets.size()).isEqualTo(2);
        int newest = bullets.age(0) < bullets.age(1) ? 0 : 1;
        assertThat(bullets.y(newest)).as("from the moved origin").isCloseTo(3f, within(0.2f));
        assertThat(angle(newest)).isCloseTo(360f - 30.96f, within(0.5f));
        assertThat(system.running(handle)).isFalse();

        int endless = system.run("aim", boss, player, 2);
        world.destroy(boss);
        steps(1);
        assertThat(system.running(endless)).as("stops when its origin dies").isFalse();
    }

    @Test
    void spiralRepeatsWithAngleAndSpeedSteps() {
        pattern("spiral", "{ \"version\": 1, \"emit\": { \"type\": \"spiral\", \"bullet\": \"orb\", \"count\": 2, "
                + "\"angle\": 0, \"speed\": 1, \"repeat\": 3, \"interval\": 0.1, \"angleStep\": 10, \"speedStep\": 1 } }");
        system.runAt("spiral", 0f, 0f, World.NONE, 1);
        steps(13);
        assertThat(angles()).containsExactly(0f, 10f, 20f, 180f, 190f, 200f);
        float fastest = 0f;
        for (int i = 0; i < bullets.size(); i++) {
            fastest = Math.max(fastest, (float) Math.hypot(bullets.vx(i), bullets.vy(i)));
        }
        assertThat(fastest).isCloseTo(3f, within(1e-4f));
    }

    @Test
    void delayRandomAndWave() {
        pattern("late", "{ \"version\": 1, \"emit\": { \"type\": \"random\", \"bullet\": \"orb\", \"count\": 50, "
                + "\"angle\": 90, \"spread\": 40, \"delay\": 0.5 } }");
        system.setSeed(42);
        system.runAt("late", 0f, 0f, World.NONE, 1);
        steps(29);
        assertThat(bullets.size()).isZero();
        steps(2);
        assertThat(bullets.size()).isEqualTo(50);
        assertThat(angles()).allSatisfy(a -> assertThat(a).isBetween(70f, 110f));
        List<Float> first = angles();
        bullets.clear(-1, null);
        system.setSeed(42);
        system.runAt("late", 0f, 0f, World.NONE, 1);
        steps(31);
        assertThat(angles()).as("deterministic for a seed").isEqualTo(first);
        bullets.clear(-1, null);

        pattern("wave", "{ \"version\": 1, \"emit\": { \"type\": \"wave\", \"bullet\": \"orb\", \"angle\": 0, "
                + "\"repeat\": 3, \"interval\": 0.1, \"amplitude\": 20, \"frequency\": 4 } }");
        system.runAt("wave", 0f, 0f, World.NONE, 1);
        steps(13);
        assertThat(angles()).containsExactly(0f, 0f, 20f);   // sin(0), sin(pi), sin(pi/2)
    }

    @Test
    void accelerationTurningAndChildren() {
        int slot = bullets.spawn(types.index("orb"), 0f, 0f, 0f, 2f, 1);
        bullets.setAccel(slot, 1f);
        steps(60);
        assertThat(Math.hypot(bullets.vx(0), bullets.vy(0))).isCloseTo(3.0, within(0.02));
        bullets.setAccel(0, 0f);
        bullets.setTurn(0, 90f);
        steps(60);
        assertThat(angle(0)).isCloseTo(90f, within(0.5f));
        bullets.clear(-1, null);

        pattern("burst", "{ \"version\": 1, \"emit\": { \"type\": \"single\", \"bullet\": \"orb\", \"angle\": 0, "
                + "\"speed\": 6, \"child\": { \"type\": \"ring\", \"bullet\": \"orb\", \"count\": 5 }, "
                + "\"childAfter\": 0.5 } }");
        system.runAt("burst", 0f, 0f, World.NONE, 1);
        steps(29);
        assertThat(bullets.size()).isEqualTo(1);
        steps(2);
        assertThat(bullets.size()).as("the parent is replaced by its child ring").isEqualTo(5);
        assertThat(bullets.x(0)).isCloseTo(3f, within(0.2f));
    }

    // ---- collision ----

    @Test
    void hitsAndGrazesAreExactAtTheEdge() {
        int player = entity(0f, 0f);
        BulletTarget t = world.add(player, new BulletTarget());
        t.radius = 0.25f;
        t.grazeRadius = 1f;
        world.add(player, new Team()).id = 1;
        world.add(player, new Health()).max = 5;
        int orb = types.index("orb");
        bullets.spawn(orb, 0.4f, 0f, 0f, 0f, 2);      // touching: 0.15 + 0.25
        bullets.spawn(orb, 0f, 0.4001f, 0f, 0f, 2);   // just outside: a graze
        bullets.spawn(orb, 0f, -1.15f, 0f, 0f, 2);    // at the graze edge
        bullets.spawn(orb, 0f, -1.16f, 0f, 0f, 2);    // beyond it
        bullets.spawn(orb, -0.3f, 0f, 0f, 0f, 1);     // own team
        steps(1);
        assertThat(seen).filteredOn(BulletHit.class::isInstance).hasSize(1);
        assertThat(seen).filteredOn(Grazed.class::isInstance).hasSize(2);
        assertThat(world.get(player, Health.class).current).as("damage through kit-common").isEqualTo(3);
        assertThat(bullets.size()).isEqualTo(4);
        steps(1);
        assertThat(seen).as("a bullet grazes once").filteredOn(Grazed.class::isInstance).hasSize(2);
    }

    @Test
    void piercingBulletsHitEachTargetOnce() {
        int laser = types.index("laser");
        for (int i = 0; i < 3; i++) {
            int e = entity(i * 1f, 0f);
            world.add(e, new BulletTarget());
        }
        bullets.spawn(laser, -1f, 0f, 0f, 6f, 5);
        steps(60);
        assertThat(seen).filteredOn(BulletHit.class::isInstance).hasSize(3);
        assertThat(bullets.size()).isEqualTo(1);
    }

    @Test
    void wallBulletsStopAtWalls() {
        Collision collision = new Collision(world, events);
        system.setWallMask(1, collision);
        int wall = entity(3f, -2f);
        world.add(wall, new Collider()).box.set(0f, 0f, 1f, 4f);
        world.add(wall, new Solid());
        bullets.spawn(types.index("pellet"), 0f, 0f, 0f, 6f, 1);
        bullets.spawn(types.index("orb"), 0f, 0f, 0f, 6f, 1);
        steps(60);
        assertThat(bullets.size()).isEqualTo(1);
        assertThat(bullets.type(0)).isEqualTo(types.index("orb"));
    }

    // ---- clearing, capacity, culling, errors ----

    @Test
    void clearingCapacityAndCulling() {
        int orb = types.index("orb");
        bullets.spawn(orb, 0f, 0f, 0f, 0f, 2);
        bullets.spawn(orb, 1f, 0f, 0f, 0f, 2);
        bullets.spawn(orb, 2f, 0f, 0f, 0f, 1);
        assertThat(system.clear(2, true)).isEqualTo(2);
        steps(1);
        assertThat(seen).filteredOn(BulletCleared.class::isInstance).hasSize(2);
        assertThat(bullets.size()).isEqualTo(1);

        BulletWorld small = new BulletWorld(3, types);
        small.spawn(orb, 0f, 0f, 0f, 0f, 1);
        small.update(1f);
        small.spawn(orb, 1f, 0f, 0f, 0f, 1);
        small.spawn(orb, 2f, 0f, 0f, 0f, 1);
        small.spawn(orb, 3f, 0f, 0f, 0f, 1);
        assertThat(small.size()).isEqualTo(3);
        assertThat(small.x(0)).as("the oldest was replaced").isEqualTo(3f);

        small.setCullArea(new Rectangle(0f, -1f, 2.5f, 2f));
        small.update(0f);
        assertThat(small.size()).as("the bullet at x 3 is outside").isEqualTo(2);
        assertThat(small.x(0) + small.x(1)).isEqualTo(3f);
    }

    @Test
    void badFilesAreReportedTogether() {
        assertThatThrownBy(() -> BulletTypes.parse(new JsonReader().parse(
                "{ \"version\": 1, \"types\": { \"a\": { \"radius\": -1, \"colour\": 1 } } }"), "b.json"))
                .hasMessageContaining("b.json: bullet \"a\": unknown field \"colour\"")
                .hasMessageContaining("\"region\" is required")
                .hasMessageContaining("must be > 0");
        pattern("bad", "{ \"version\": 1, \"emit\": [ { \"type\": \"zigzag\", \"bullet\": \"orb\" }, "
                + "{ \"type\": \"ring\", \"bullet\": \"nope\", \"count\": 0 } ] }");
        assertThatThrownBy(() -> system.runAt("bad", 0f, 0f, World.NONE, 1))
                .hasMessageContaining("emit[0]: unknown type \"zigzag\"")
                .hasMessageContaining("emit[1]: unknown bullet \"nope\"")
                .hasMessageContaining("count and repeat must be >= 1");
        assertThatThrownBy(() -> system.runAt("missing", 0f, 0f, World.NONE, 1))
                .hasMessageContaining("not in the asset manifest");
        assertThatThrownBy(() -> types.index("x")).hasMessageContaining("known: [orb, pellet, laser]");
    }

    @Test
    void threeThousandBulletsStepWithoutAllocating() {
        pattern("storm", "{ \"version\": 1, \"emit\": { \"type\": \"spiral\", \"bullet\": \"orb\", \"count\": 30, "
                + "\"speed\": 0.5, \"repeat\": 100, \"interval\": 0.01, \"angleStep\": 7, \"turn\": 10 } }");
        int target = entity(500f, 500f);
        world.add(target, new BulletTarget());
        system.runAt("storm", 0f, 0f, World.NONE, 2);
        steps(120);   // warm-up: all 3,000 bullets are out
        assertThat(bullets.size()).isEqualTo(3000);
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        long start = System.nanoTime();
        for (int i = 0; i < 600; i++) {
            world.runPhase(Phase.FIXED, time);
        }
        long nanos = System.nanoTime() - start;
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        assertThat(allocated).as("bytes allocated over 600 steps").isLessThan(16 * 1024);
        assertThat(bullets.size()).isEqualTo(3000);
        System.out.println("3000 bullets: " + nanos / 600 / 1000 + " us per step, " + allocated + " bytes allocated");
    }
}
