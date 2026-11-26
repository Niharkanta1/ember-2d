package dev.ember2d.demo.platformer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Level 1 player movement and rules (F-015, I-170), headless: the engine's collision on a small tile grid, scripted
 * input, one fixed step per {@link #tick()}.
 */
class PlatformerMovementTest {

    static final float DT = 1f / 60f;

    /** Scripted input: {@code move} x and y, and the jump button. */
    static final class Script {
        float moveX;
        float moveY;
        boolean jump;
        boolean previousJump;
        boolean consumedPress;

        Input input() {
            return (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "justPressed" -> {
                            boolean p = jump && !previousJump && !consumedPress;
                            consumedPress |= p;
                            yield p;
                        }
                        case "pressed" -> jump;
                        case "justReleased" -> !jump && previousJump;
                        case "axis2" -> ((Vector2) args[1]).set(moveX, moveY);
                        case "topContext" -> "gameplay";
                        default -> method.getReturnType() == boolean.class ? false
                                : method.getReturnType() == float.class ? 0f : null;
                    });
        }

        /** After each fixed step: the button state becomes the previous one. */
        void endTick() {
            if (jump != previousJump) {
                consumedPress = false;
            }
            previousJump = jump;
        }
    }

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final Script script = new Script();
    final Collision collision = new Collision(world, events);
    final TileGrid grid = new TileGrid(40, 10, 1f, 1f);
    final List<Object> published = new ArrayList<>();
    int restarts;
    LevelSystem level;
    int player;

    PlatformerMovementTest() {
        for (int x = 0; x < 40; x++) {
            grid.setSolid(x, 0, true);   // the floor: top at y = 1
        }
        collision.addTileGrid(grid, 0);
        events.onGlobal(Jumped.class, published::add);
        events.onGlobal(Landed.class, published::add);
        events.onGlobal(LevelCompleted.class, published::add);
        world.addSystem(new PlayerControlSystem(world, collision, script.input(), events));
        world.addSystem(new CollisionSystem(world, collision));
        level = new LevelSystem(world, collision, events, () -> restarts++);
        world.addSystem(level);
        player = player(3f, 1f);
    }

    int player(float x, float y) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        world.add(e, new Collider()).box.set(-0.4375f, 0f, 0.875f, 1.375f);
        world.add(e, new Actor());
        world.add(e, new PlayerControl());
        return e;
    }

    int trigger(Object marker, float x, float y, float w, float h) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        Collider c = world.add(e, new Collider());
        c.box.set(-w / 2f, 0f, w, h);
        c.trigger = true;
        world.add(e, marker);
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

    PlayerControl body() {
        return world.get(player, PlayerControl.class);
    }

    Vector2 position() {
        return world.get(player, Transform.class).position;
    }

    long jumps() {
        return published.stream().filter(e -> e instanceof Jumped).count();
    }

    // ---- movement ----

    @Test
    void runningAcceleratesToMaxSpeedAndStopsWithoutInput() {
        ticks(2);   // settle on the floor
        script.moveX = 1f;
        tick();
        assertThat(body().vx).isCloseTo(1f, within(1e-4f));   // 60 u/s² for one 1/60 s step
        ticks(10);
        assertThat(body().vx).isEqualTo(8f);

        script.moveX = 0f;
        int stopTicks = 0;
        while (body().vx > 0f) {
            tick();
            stopTicks++;
        }
        assertThat(stopTicks).isEqualTo(7);   // 70 u/s²: 8 / (70 / 60) = 6.9 ticks
    }

    @Test
    void releasingJumpEarlyGivesALowerJump() {
        ticks(2);
        float held = apex(-1);
        script.jump = false;
        ticks(120);
        float early = apex(4);

        assertThat(early).isLessThan(held - 1.5f);
        assertThat(held - 1f).as("full jump about 3.2 units").isCloseTo(3.2f, within(0.3f));
    }

    /** Presses jump and returns the highest point; releases after the given ticks (-1: hold). */
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
        assertThat(jumpAfterLeavingLedge(6)).isTrue();
        assertThat(jumpAfterLeavingLedge(7)).isFalse();
    }

    /** Walks off the right end of a ledge and presses jump k ticks after the first airborne step. */
    boolean jumpAfterLeavingLedge(int k) {
        published.clear();
        for (int x = 10; x < 40; x++) {
            grid.setSolid(x, 0, false);   // the floor ends at x = 10
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
        script.jump = true;
        tick();
        script.jump = false;
        tick();
        return jumps() == 1;
    }

    @Test
    void aJumpBufferedUpToSixTicksBeforeLandingStartsOnLanding() {
        int landing = ticksToLand();
        assertThat(bufferedJump(landing, 6)).isTrue();
        assertThat(bufferedJump(landing, 7)).isFalse();
    }

    /** Drops the player from 4 units up; the tick index at whose start it is grounded. */
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
        return i;   // tick i (0-based) starts grounded
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
        return jumps() == 1;
    }

    @Test
    void sixtyAndHundredFortyFourHertzGiveTheSameMotion() {
        assertThat(simulate(60)).isEqualTo(simulate(144));
    }

    /** One second of running right with a held jump, rendered at the given rate; the position after 60 fixed steps. */
    Vector2 simulate(int hz) {
        World w = new World(false);
        DefaultEventBus bus = new DefaultEventBus(false);
        TimeState t = new TimeState(DT);
        Script s = new Script();
        Collision c = new Collision(w, bus);
        c.addTileGrid(grid, 0);
        w.addSystem(new PlayerControlSystem(w, c, s.input(), bus));
        w.addSystem(new CollisionSystem(w, c));
        int e = w.create();
        w.add(e, Transform.class).teleport(3f, 1f);
        w.add(e, new Collider()).box.set(-0.4375f, 0f, 0.875f, 1.375f);
        w.add(e, new Actor());
        w.add(e, new PlayerControl());
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

    // ---- level rules ----

    @Test
    void landingOnAnEnemyDefeatsItAndBouncesWhileSideContactHurts() {
        int pig = enemy(10f);
        world.get(player, Transform.class).teleport(10f, 3f);
        body().vy = -5f;
        int i = 0;
        while (!world.get(pig, Enemy.class).defeated && i++ < 60) {
            tick();
        }
        assertThat(world.get(pig, Enemy.class).defeated).isTrue();
        assertThat(body().vy).as("bounce").isGreaterThan(8f);
        ticks(30);
        assertThat(world.isAlive(pig)).as("removed after its hit clip").isFalse();
        assertThat(level.deaths()).isZero();

        enemy(20f);
        world.get(player, Transform.class).teleport(18f, 1f);
        script.moveX = 1f;
        ticks(30);
        assertThat(level.deaths()).isEqualTo(1);
        assertThat(body().locked).isTrue();
    }

    int enemy(float x) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, 1f);
        world.add(e, new Collider()).box.set(-0.75f, 0f, 1.5f, 1.5f);
        world.add(e, new Actor());
        world.add(e, new Enemy());
        return e;
    }

    @Test
    void hazardsHurtAndThePlayerRespawnsAtTheLastCheckpoint() {
        ticks(2);
        trigger(new Checkpoint(), 6f, 1f, 1.5f, 3f);
        trigger(new Hazard(), 12f, 1f, 0.8f, 0.4f);
        script.moveX = 1f;
        int i = 0;
        while (level.deaths() == 0 && i++ < 300) {
            tick();
        }
        assertThat(level.deaths()).isEqualTo(1);
        assertThat(level.respawnPoint()).isEqualTo(new Vector2(6f, 1f));
        script.moveX = 0f;
        ticks(Math.round(LevelSystem.HURT_SECONDS / DT) + 2);
        assertThat(position().x).isCloseTo(6f, within(0.01f));
        assertThat(body().locked).isFalse();

        world.get(player, Transform.class).teleport(12f, 1f);   // still invulnerable: no second hurt
        ticks(5);
        assertThat(level.deaths()).isEqualTo(1);
        ticks(Math.round(LevelSystem.INVULNERABLE_SECONDS / DT));   // standing in the spikes when blinking ends
        assertThat(level.deaths()).isEqualTo(2);
    }

    @Test
    void fallingOutOfTheMapCountsAsHurt() {
        world.get(player, Transform.class).teleport(3f, -3f);
        ticks(2);
        assertThat(level.deaths()).isEqualTo(1);
    }

    @Test
    void theGoalShowsTheResultAndRestartsTheLevel() {
        ticks(2);
        Goal goal = new Goal();
        goal.restartSeconds = 0.5f;
        int g = trigger(goal, 6f, 1f, 2f, 2.5f);
        script.moveX = 1f;
        int i = 0;
        while (!level.complete() && i++ < 300) {
            tick();
        }
        assertThat(level.complete()).isTrue();
        assertThat(world.get(g, dev.ember2d.render.Text.class).text).startsWith("Level complete!\n0:0")
                .endsWith(" - 0 deaths");
        assertThat(published).anyMatch(e -> e instanceof LevelCompleted);
        assertThat(body().locked).isTrue();

        ticks(29);
        assertThat(restarts).isZero();
        ticks(2);
        assertThat(restarts).isEqualTo(1);
        ticks(30);
        assertThat(restarts).as("once").isEqualTo(1);
    }

    @Test
    void clockFormat() {
        assertThat(LevelSystem.clock(0f)).isEqualTo("0:00");
        assertThat(LevelSystem.clock(83.9f)).isEqualTo("1:23");
    }
}
