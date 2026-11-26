package dev.ember2d.kit.platformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.Solid;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/**
 * The platformer kit (F-015, I-200), headless: the engine's collision on a tile grid, scripted input, one fixed step
 * per {@link #tick()}.
 */
class PlatformerSystemTest {

    static final float DT = 1f / 60f;

    /** Scripted input: the {@code move} axis and the {@code jump} and {@code dash} buttons. */
    static final class Script {
        float moveX;
        float moveY;
        boolean jump;
        boolean dash;
        boolean dashBound = true;
        private boolean previousJump;
        private boolean previousDash;
        private boolean jumpUsed;
        private boolean dashUsed;

        Input input() {
            return (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasAction" -> !args[0].equals("dash") || dashBound;
                        case "justPressed" -> {
                            if (args[0].equals("jump")) {
                                boolean p = jump && !previousJump && !jumpUsed;
                                jumpUsed |= p;
                                yield p;
                            }
                            boolean p = dash && !previousDash && !dashUsed;
                            dashUsed |= p;
                            yield p;
                        }
                        case "pressed" -> args[0].equals("jump") ? jump : dash;
                        case "axis2" -> ((Vector2) args[1]).set(moveX, moveY);
                        default -> method.getReturnType() == boolean.class ? false
                                : method.getReturnType() == float.class ? 0f : null;
                    });
        }

        void endTick() {
            if (jump != previousJump) {
                jumpUsed = false;
            }
            if (dash != previousDash) {
                dashUsed = false;
            }
            previousJump = jump;
            previousDash = dash;
        }
    }

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final Script script = new Script();
    final Collision collision = new Collision(world, events);
    final TileGrid grid = new TileGrid(60, 20, 1f, 1f);
    final List<Object> published = new ArrayList<>();
    int player;

    PlatformerSystemTest() {
        for (int x = 0; x < 60; x++) {
            grid.setSolid(x, 0, true);   // the floor: top at y = 1
        }
        collision.addTileGrid(grid, 0);
        events.onGlobal(Jumped.class, published::add);
        events.onGlobal(Landed.class, published::add);
        events.onGlobal(Dashed.class, published::add);
        world.addSystem(new MovingPlatformSystem(world, collision));
        world.addSystem(new PatrollerSystem(world, collision));
        world.addSystem(new PlatformerSystem(world, collision, script.input(), events));
        world.addSystem(new CollisionSystem(world, collision));
        player = player(3f, 1f);
    }

    int player(float x, float y) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        world.add(e, new Collider()).box.set(-0.4375f, 0f, 0.875f, 1.375f);
        world.add(e, new Actor());
        world.add(e, new PlatformerBody());
        return e;
    }

    void tick() {
        world.runPhase(Phase.FIXED, time);
        events.flush();
        script.endTick();
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Presses jump for one tick (released early: the jump is cut short). */
    void tapJump() {
        script.jump = true;
        tick();
        script.jump = false;
    }

    /** Holds jump for the given ticks, then releases it for one tick. */
    void holdJump(int ticks) {
        script.jump = true;
        ticks(ticks);
        script.jump = false;
        tick();
    }

    PlatformerBody body() {
        return world.get(player, PlatformerBody.class);
    }

    Vector2 position() {
        return world.get(player, Transform.class).position;
    }

    List<JumpKind> jumps() {
        return published.stream().filter(e -> e instanceof Jumped).map(e -> ((Jumped) e).kind()).toList();
    }

    void wall(int x, int fromY, int toY) {
        for (int y = fromY; y <= toY; y++) {
            grid.setSolid(x, y, true);
        }
    }

    // ---- the I-170 movement, unchanged ----

    @Test
    void withEveryOptionOffTheMovementIsExactlyLevelOnes() throws Exception {
        TileGrid g = new TileGrid(60, 12, 1f, 1f);
        for (int x = 0; x < 60; x++) {
            if (x < 20 || x > 22) {
                g.setSolid(x, 0, true);
            }
        }
        for (int x = 30; x <= 33; x++) {
            g.setSolid(x, 1, true);
            g.setSolid(x, 2, true);
        }
        for (int x = 40; x <= 44; x++) {
            g.setOneWay(x, 4, true);
        }
        World w = new World(false);
        DefaultEventBus bus = new DefaultEventBus(false);
        TimeState t = new TimeState(DT);
        Script s = new Script();
        Collision c = new Collision(w, bus);
        c.addTileGrid(g, 0);
        w.addSystem(new PlatformerSystem(w, c, s.input(), bus));
        w.addSystem(new CollisionSystem(w, c));
        int e = w.create();
        w.add(e, Transform.class).teleport(3f, 1f);
        w.add(e, new Collider()).box.set(-0.4375f, 0f, 0.875f, 1.375f);
        w.add(e, new Actor());
        PlatformerBody body = w.add(e, new PlatformerBody());

        List<String> expected = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/i170-trace.csv")), StandardCharsets.UTF_8))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                expected.add(line);
            }
        }
        assertThat(expected).hasSize(420);
        for (int tick = 0; tick < 420; tick++) {
            s.moveX = tick < 60 ? 1f : tick < 80 ? 0f : tick < 261 ? 1f : tick < 301 ? -1f : tick < 331 ? 0f
                    : (tick / 20) % 2 == 0 ? 1f : -0.5f;
            s.moveY = tick >= 301 && tick < 331 ? -1f : 0f;
            s.jump = (tick >= 25 && tick < 45) || (tick >= 100 && tick < 104) || (tick >= 150 && tick < 175)
                    || (tick >= 230 && tick < 255) || (tick >= 310 && tick < 312) || (tick >= 360 && tick < 380);
            w.runPhase(Phase.FIXED, t);
            bus.flush();
            s.endTick();
            Vector2 p = w.get(e, Transform.class).position;
            String actual = tick + "," + Float.floatToIntBits(p.x) + "," + Float.floatToIntBits(p.y) + ","
                    + Float.floatToIntBits(body.vx) + "," + Float.floatToIntBits(body.vy);
            assertThat(actual).as("tick %d", tick).isEqualTo(expected.get(tick));
        }
    }

    @Test
    void runningAcceleratesToMaxSpeedAndStopsWithoutInput() {
        ticks(2);
        script.moveX = 1f;
        tick();
        assertThat(body().vx).isCloseTo(1f, within(1e-4f));
        ticks(10);
        assertThat(body().vx).isEqualTo(8f);
        script.moveX = 0f;
        int stopTicks = 0;
        while (body().vx > 0f) {
            tick();
            stopTicks++;
        }
        assertThat(stopTicks).isEqualTo(7);
    }

    @Test
    void releasingJumpEarlyGivesALowerJump() {
        ticks(2);
        float held = apex(-1);
        ticks(120);
        float early = apex(4);
        assertThat(early).isLessThan(held - 1.5f);
        assertThat(held - 1f).isCloseTo(3.2f, within(0.3f));
    }

    float apex(int releaseAfter) {
        script.jump = true;
        float top = 0f;
        for (int i = 0; i < 90; i++) {
            if (i == releaseAfter) {
                script.jump = false;
            }
            tick();
            top = Math.max(top, position().y);
        }
        script.jump = false;
        return top;
    }

    @Test
    void coyoteTimeAllowsAJumpUpToSixTicksAfterLeavingALedge() {
        assertThat(jumpAfterLeavingLedge(6)).containsExactly(JumpKind.COYOTE);
        assertThat(jumpAfterLeavingLedge(7)).isEmpty();
    }

    List<JumpKind> jumpAfterLeavingLedge(int k) {
        published.clear();
        for (int x = 10; x < 60; x++) {
            grid.setSolid(x, 0, false);
        }
        world.get(player, Transform.class).teleport(8f, 1f);
        body().vx = 0f;
        body().vy = 0f;
        script.moveX = 1f;
        ticks(2);
        while (body().wasGrounded) {
            tick();
        }
        script.moveX = 0f;
        ticks(k - 1);
        tapJump();
        tick();
        return jumps();
    }

    @Test
    void aJumpBufferedUpToSixTicksBeforeLandingStartsOnLanding() {
        int landing = ticksToLand();
        assertThat(bufferedJump(landing, 6)).isTrue();
        assertThat(bufferedJump(landing, 7)).isFalse();
    }

    int ticksToLand() {
        world.get(player, Transform.class).teleport(3f, 5f);
        body().vy = 0f;
        int i = 0;
        while (!body().wasGrounded || i == 0) {
            tick();
            i++;
            if (i == 1) {
                body().wasGrounded = false;
            }
        }
        return i;
    }

    boolean bufferedJump(int landingTick, int before) {
        published.clear();
        world.get(player, Transform.class).teleport(3f, 5f);
        body().vy = 0f;
        body().wasGrounded = false;
        for (int i = 0; i <= landingTick; i++) {
            script.jump = i == landingTick - before;
            tick();
        }
        script.jump = false;
        tick();
        return jumps().size() == 1;
    }

    @Test
    void sixtyAndHundredFortyFourHertzGiveTheSameMotion() {
        assertThat(simulate(60)).isEqualTo(simulate(144));
    }

    Vector2 simulate(int hz) {
        World w = new World(false);
        DefaultEventBus bus = new DefaultEventBus(false);
        TimeState t = new TimeState(DT);
        Script s = new Script();
        Collision c = new Collision(w, bus);
        c.addTileGrid(grid, 0);
        w.addSystem(new PlatformerSystem(w, c, s.input(), bus));
        w.addSystem(new CollisionSystem(w, c));
        int e = w.create();
        w.add(e, Transform.class).teleport(3f, 1f);
        w.add(e, new Collider()).box.set(-0.4375f, 0f, 0.875f, 1.375f);
        w.add(e, new Actor());
        w.add(e, new PlatformerBody());
        s.moveX = 1f;
        s.jump = true;
        long frame = Math.round(1_000_000_000d / hz);
        GameLoop loop = new GameLoop(() -> frame, t, phase -> {
            w.runPhase(phase, t);
            if (phase == Phase.FIXED) {
                bus.flush();
                s.endTick();
            }
        }, 5);
        while (t.fixedTick() < 60) {
            loop.frame();
        }
        return new Vector2(w.get(e, Transform.class).position);
    }

    // ---- optional mechanics ----

    @Test
    void airJumpsAreLimitedAndRefilledOnLanding() {
        ticks(2);
        holdJump(12);   // past coyote time, still rising
        tapJump();
        ticks(3);
        tapJump();
        assertThat(jumps()).as("no air jumps by default").containsExactly(JumpKind.GROUND);

        ticks(120);
        published.clear();
        body().airJumps = 1;
        ticks(2);
        holdJump(12);
        tapJump();
        ticks(3);
        tapJump();
        assertThat(jumps()).containsExactly(JumpKind.GROUND, JumpKind.AIR);

        ticks(120);
        published.clear();
        holdJump(12);
        tapJump();
        assertThat(jumps()).as("refilled").containsExactly(JumpKind.GROUND, JumpKind.AIR);
    }

    @Test
    void dashBurstsWithoutGravityThenCoolsDown() {
        ticks(2);
        script.dash = true;
        tick();
        script.dash = false;
        tick();
        assertThat(body().dash).isFalse();
        assertThat(published).noneMatch(e -> e instanceof Dashed);

        body().dash = true;
        ticks(3);
        float startX = position().x;
        script.dash = true;
        tick();
        script.dash = false;
        assertThat(published).filteredOn(e -> e instanceof Dashed).containsExactly(new Dashed(player, 1));
        assertThat(body().dashing()).isTrue();
        ticks(body().dashTicks - 1);
        assertThat(body().dashing()).isFalse();
        assertThat(position().x - startX).isCloseTo(20f * 10 / 60f, within(0.01f));

        script.dash = false;
        tick();
        script.dash = true;   // in cooldown
        tick();
        script.dash = false;
        assertThat(published).filteredOn(e -> e instanceof Dashed).hasSize(1);
        ticks(body().dashCooldownTicks);
        script.dash = true;
        tick();
        script.dash = false;
        assertThat(published).filteredOn(e -> e instanceof Dashed).hasSize(2);
    }

    @Test
    void anAirDashHoldsHeightAndIsLimited() {
        body().dash = true;
        body().dashCooldownTicks = 0;
        ticks(2);
        holdJump(8);
        float y = position().y;
        script.dash = true;
        tick();
        script.dash = false;
        ticks(5);
        assertThat(published).filteredOn(e -> e instanceof Dashed).hasSize(1);
        assertThat(body().grounded()).isFalse();
        assertThat(position().y).as("no gravity while dashing").isCloseTo(y, within(1e-4f));
        ticks(5);
        script.dash = true;
        tick();
        script.dash = false;
        assertThat(published).as("one air dash").filteredOn(e -> e instanceof Dashed).hasSize(1);
    }

    @Test
    void noDashWithoutItsBinding() {
        script.dashBound = false;
        body().dash = true;
        ticks(2);
        script.dash = true;
        tick();
        assertThat(published).noneMatch(e -> e instanceof Dashed);
    }

    @Test
    void wallSlideCapsTheFallOnlyWhilePushingIntoTheWall() {
        wall(10, 1, 15);
        body().wallSlide = true;
        world.get(player, Transform.class).teleport(9.5f, 10f);
        script.moveX = 1f;
        ticks(40);
        assertThat(body().wallSliding()).isTrue();
        assertThat(body().vy).isEqualTo(-body().wallSlideSpeed);

        script.moveX = 0f;
        ticks(5);
        assertThat(body().wallSliding()).isFalse();
        assertThat(body().vy).isLessThan(-body().wallSlideSpeed);
    }

    @Test
    void wallJumpPushesAwayAndBlocksClimbingOneWall() {
        wall(10, 1, 15);
        body().wallJump = true;
        world.get(player, Transform.class).teleport(9.5f, 8f);
        script.moveX = 1f;
        ticks(10);   // past coyote time
        tapJump();
        assertThat(jumps()).containsExactly(JumpKind.WALL);
        float x = position().x;
        ticks(body().wallJumpLockTicks - 1);
        assertThat(body().vx).as("input towards the wall ignored").isLessThan(0f);
        assertThat(position().x).isLessThan(x);
    }

    @Test
    void laddersAreClimbedWithoutGravity() {
        int ladder = world.create();
        world.add(ladder, Transform.class).teleport(10f, 1f);
        Collider c = world.add(ladder, new Collider());
        c.box.set(-0.5f, 0f, 1f, 5f);
        c.trigger = true;
        world.add(ladder, new Ladder());
        body().ladders = true;
        world.get(player, Transform.class).teleport(10f, 1f);
        script.moveY = 1f;
        ticks(2);
        assertThat(body().climbing()).isTrue();
        float y = position().y;
        ticks(30);
        assertThat(position().y - y).isCloseTo(5f * 30 / 60f, within(0.01f));

        script.moveY = 0f;
        ticks(10);
        assertThat(body().climbing()).as("holds on").isTrue();
        float hold = position().y;
        ticks(10);
        assertThat(position().y).isCloseTo(hold, within(1e-4f));

        tapJump();
        assertThat(body().climbing()).isFalse();
        assertThat(jumps()).containsExactly(JumpKind.GROUND);

        body().ladders = false;
        world.get(player, Transform.class).teleport(10f, 1f);
        ticks(5);
        script.moveY = 1f;
        ticks(5);
        assertThat(body().climbing()).as("off by default").isFalse();
    }

    @Test
    void jumpingOffAMovingPlatformAddsItsVelocityOnlyWithTheFlag() {
        assertThat(jumpOffPlatform(false)).isLessThan(0.5f);
        assertThat(jumpOffPlatform(true)).as("3 u/s minus one tick of ground deceleration").isCloseTo(3f - 70f / 60f,
                within(0.01f));
    }

    float jumpOffPlatform(boolean inherit) {
        int platform = world.create();
        world.add(platform, Transform.class).teleport(20f, 3f);
        world.add(platform, new Collider()).box.set(-2f, 0f, 4f, 0.5f);
        world.add(platform, new Solid());
        MovingPlatform mp = world.add(platform, new MovingPlatform());
        mp.speed = 3f;
        mp.distance = 20f;
        int rider = player(20f, 3.5f);
        world.get(rider, PlatformerBody.class).inheritPlatformVelocity = inherit;
        world.remove(player, PlatformerBody.class);   // only the rider reads input
        ticks(20);
        tapJump();
        float vx = world.get(rider, PlatformerBody.class).vx;
        world.destroy(platform);
        world.destroy(rider);
        tick();
        world.add(player, new PlatformerBody());
        return vx;
    }

    @Test
    void patrollersTurnAtTheirRangeAtWallsAndOptionallyAtLedges() {
        world.remove(player, PlatformerBody.class);
        int walker = walker(20f);
        world.get(walker, Patroller.class).range = 2f;
        float min = 99f;
        float max = -99f;
        for (int i = 0; i < 300; i++) {
            tick();
            float x = world.get(walker, Transform.class).position.x;
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        assertThat(min).isCloseTo(18f, within(0.2f));
        assertThat(max).isCloseTo(22f, within(0.2f));

        wall(30, 1, 3);
        int bumper = walker(28f);
        ticks(60);
        assertThat(world.get(bumper, Patroller.class).direction).as("turned at the wall").isEqualTo(-1f);

        for (int x = 44; x < 60; x++) {
            grid.setSolid(x, 0, false);   // the floor ends at x = 44
        }
        int careful = walker(42f);
        world.get(careful, Patroller.class).turnAtLedges = true;
        int careless = walker(42f);
        ticks(120);
        assertThat(world.get(careful, Transform.class).position.y).as("stayed up").isEqualTo(1f);
        assertThat(world.get(careless, Transform.class).position.y).as("walked off").isLessThan(0f);
    }

    int walker(float x) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, 1f);
        world.add(e, new Collider()).box.set(-0.5f, 0f, 1f, 1f);
        world.add(e, new Actor());
        Patroller p = world.add(e, new Patroller());
        p.speed = 3f;
        p.minX = -100f;
        p.maxX = 100f;
        return e;
    }

    @Test
    void aVerticalPlatformCarriesItsRider() {
        int platform = world.create();
        world.add(platform, Transform.class).teleport(20f, 2f);
        world.add(platform, new Collider()).box.set(-2f, 0f, 4f, 0.5f);
        world.add(platform, new Solid());
        MovingPlatform mp = world.add(platform, new MovingPlatform());
        mp.vertical = true;
        mp.speed = 2f;
        mp.distance = 3f;
        world.get(player, Transform.class).teleport(20f, 2.5f);
        ticks(30);
        assertThat(mp.velocityY()).isEqualTo(2f);
        assertThat(mp.velocityX()).isZero();
        assertThat(position().y).isCloseTo(2.5f + 1f, within(0.05f));
    }
}
