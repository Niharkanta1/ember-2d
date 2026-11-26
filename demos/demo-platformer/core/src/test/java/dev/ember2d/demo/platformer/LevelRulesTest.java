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
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.platformer.PlatformerBody;
import dev.ember2d.kit.platformer.PlatformerSystem;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Level 1 rules (F-015, I-170) with the kit's movement (I-200), headless: the engine's collision on a small tile grid,
 * scripted input, one fixed step per {@link #tick()}.
 */
class LevelRulesTest {

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
                        case "hasAction" -> true;
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
    LevelSystem level;
    int player;

    LevelRulesTest() {
        for (int x = 0; x < 40; x++) {
            grid.setSolid(x, 0, true);   // the floor: top at y = 1
        }
        collision.addTileGrid(grid, 0);
        events.onGlobal(LevelCompleted.class, published::add);
        world.addSystem(new PlatformerSystem(world, collision, script.input(), events));
        world.addSystem(new CollisionSystem(world, collision));
        level = new LevelSystem(world, collision, events);
        world.addSystem(level);
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

    PlatformerBody body() {
        return world.get(player, PlatformerBody.class);
    }

    Vector2 position() {
        return world.get(player, Transform.class).position;
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
    void theGoalEndsTheLevelAndStopsThePlayer() {
        ticks(2);
        trigger(new Goal(), 6f, 1f, 2f, 2.5f);
        script.moveX = 1f;
        int i = 0;
        while (!level.complete() && i++ < 300) {
            tick();
        }
        assertThat(level.complete()).isTrue();
        assertThat(published).filteredOn(e -> e instanceof LevelCompleted).singleElement()
                .satisfies(e -> assertThat(((LevelCompleted) e).deaths()).isZero());
        assertThat(body().locked).isTrue();
        float time = level.levelTime();
        float x = position().x;
        ticks(60);
        assertThat(level.levelTime()).as("the clock stops").isEqualTo(time);
        assertThat(position().x).as("the player stays").isCloseTo(x, within(0.2f));
    }

    @Test
    void clockFormat() {
        assertThat(LevelSystem.clock(0f)).isEqualTo("0:00");
        assertThat(LevelSystem.clock(83.9f)).isEqualTo("1:23");
    }
}
