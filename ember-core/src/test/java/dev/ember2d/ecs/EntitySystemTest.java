package dev.ember2d.ecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.Test;

class EntitySystemTest {

    static final class Velocity {
        final Vector2 value = new Vector2();
    }

    static final class Health {
        int value = 3;
    }

    private final World world = new World(false);
    private final TimeState time = new TimeState(1f / 60f);
    private final List<String> trace = new ArrayList<>();

    /** A recording system; each use is an anonymous subclass ({@code new Recording(...) {}}), so classes stay distinct. */
    private abstract class Recording extends EntitySystem {
        private final String name;
        private final IntConsumer body;

        Recording(String name, Phase phase, Family family, IntConsumer body) {
            super(phase, family);
            this.name = name;
            this.body = body;
        }

        @Override
        protected void begin() {
            trace.add(name + ".begin");
        }

        @Override
        protected void process(int entity, float delta) {
            trace.add(name + " " + world().name(entity) + " " + delta);
            body.accept(entity);
        }

        @Override
        protected void end() {
            trace.add(name + ".end");
        }
    }

    private int entity(String name) {
        int e = world.create();
        world.setName(e, name);
        world.add(e, new Health());
        return e;
    }

    /** Advances time by one 60 Hz frame (as the game loop would) without running phases. */
    private void tick() {
        // Use a tiny loop: FIXED delta is constant; the frame delta matters for UPDATE/LATE/RENDER.
        new dev.ember2d.core.internal.GameLoop(() -> 16_666_668L, time, phase -> { }, 5).frame();
    }

    @Test
    void systemsRunByPhaseThenRegistrationOrder() {
        Family all = world.family(Health.class);
        entity("a");
        world.addSystem(new Recording("late", Phase.LATE, all, e -> { }) {});
        world.addSystem(new EntitySystem(Phase.UPDATE, all) {
            @Override
            protected void process(int entity, float delta) {
                trace.add("update-1");
            }
        });
        world.addSystem(new EntitySystem(Phase.UPDATE, all) {
            @Override
            protected void process(int entity, float delta) {
                trace.add("update-2");
            }
        });
        tick();

        for (Phase phase : Phase.values()) {
            world.runPhase(phase, time);
        }

        assertThat(trace).containsExactly("update-1", "update-2", "late.begin", "late a " + time.delta(), "late.end");
    }

    @Test
    void destroyDuringIterationIsDeferredUntilTheSystemEnds() {
        Family all = world.family(Health.class);
        int a = entity("a");
        int b = entity("b");
        entity("c");
        world.addSystem(new Recording("killer", Phase.UPDATE, all, e -> {
            if (e == a) {
                world.destroy(b);   // b is later in this iteration
                assertThat(world.isAlive(b)).as("still alive during the system").isTrue();
                assertThat(world.get(b, Health.class)).as("never half-destroyed").isNotNull();
            }
        }) {});
        world.addSystem(new Recording("next", Phase.UPDATE, all, e -> { }) {});

        world.runPhase(Phase.UPDATE, time);

        assertThat(trace).containsExactly(
                "killer.begin", "killer a 0.0", "killer b 0.0", "killer c 0.0", "killer.end",
                "next.begin", "next a 0.0", "next c 0.0", "next.end");
        assertThat(world.isAlive(b)).isFalse();
    }

    @Test
    void addAndRemoveDuringIterationApplyAfterTheSystem() {
        Family all = world.family(Health.class);
        Family moving = world.family(Health.class, Velocity.class);
        int a = entity("a");
        world.addSystem(new Recording("adder", Phase.UPDATE, all, e -> {
            world.add(e, new Velocity());
            assertThat(world.has(e, Velocity.class)).as("queued").isFalse();
            world.remove(e, Health.class);
            assertThat(world.has(e, Health.class)).as("queued").isTrue();
        }) {});

        world.runPhase(Phase.UPDATE, time);

        assertThat(world.has(a, Velocity.class)).isTrue();
        assertThat(world.has(a, Health.class)).isFalse();
        assertThat(moving.size()).isZero();
        assertThat(all.size()).isZero();
    }

    @Test
    void entityCreatedDuringIterationJoinsAfterTheSystem() {
        Family all = world.family(Health.class);
        entity("a");
        world.addSystem(new Recording("spawner", Phase.UPDATE, all, e -> {
            int spawned = world.create();
            world.setName(spawned, "spawned");
            world.add(spawned, new Health());
        }) {});

        world.runPhase(Phase.UPDATE, time);

        assertThat(trace).containsExactly("spawner.begin", "spawner a 0.0", "spawner.end");
        assertThat(all.size()).isEqualTo(2);
    }

    @Test
    void fixedPhaseStoresPreviousTransformAndUsesFixedDelta() {
        int e = world.create();
        Transform t = world.add(e, Transform.class);
        t.position.set(1, 2);
        t.rotation = 30f;
        world.add(e, new Velocity()).value.set(60, 0);
        world.addSystem(new EntitySystem(Phase.FIXED, world.family(Transform.class, Velocity.class)) {
            @Override
            protected void process(int entity, float delta) {
                Transform transform = world().get(entity, Transform.class);
                transform.position.mulAdd(world().get(entity, Velocity.class).value, delta);
                transform.rotation += 10f;
            }
        });

        world.runPhase(Phase.FIXED, time);

        assertThat(t.previous).isEqualTo(new Vector2(1, 2));
        assertThat(t.previousRotation).isEqualTo(30f);
        assertThat(t.position.x).isCloseTo(2f, within(1e-5f));
        assertThat(t.rotation).isEqualTo(40f);
    }

    @Test
    void interpolationFollowsTheFormula() {
        Transform t = new Transform();
        t.previous.set(0, 10);
        t.position.set(8, 20);
        t.previousRotation = 0f;
        t.rotation = 90f;

        Vector2 out = new Vector2();
        t.interpolated(0.25f, out);

        assertThat(out.x).isEqualTo(0 + (8 - 0) * 0.25f);
        assertThat(out.y).isEqualTo(10 + (20 - 10) * 0.25f);
        assertThat(t.interpolatedRotation(0.5f)).isEqualTo(45f);

        t.teleport(100, 100);
        assertThat(t.interpolated(0.5f, out)).isEqualTo(new Vector2(100, 100));
    }

    @Test
    void pauseSkipsUpdateAndLateUnlessRunsWhenPausedWhichGetsUnscaledDelta() {
        Family all = world.family(Health.class);
        entity("a");
        world.addSystem(new Recording("input", Phase.INPUT, all, e -> { }) {});
        world.addSystem(new Recording("update", Phase.UPDATE, all, e -> { }) {});
        world.addSystem(new EntitySystem(Phase.UPDATE, all) {
            @Override
            protected void process(int entity, float delta) {
                trace.add("menu " + delta);
            }

            @Override
            public boolean runsWhenPaused() {
                return true;
            }
        });
        world.addSystem(new Recording("render", Phase.RENDER, all, e -> { }) {});
        time.setPaused(true);
        tick();

        for (Phase phase : Phase.values()) {
            world.runPhase(phase, time);
        }

        float unscaled = time.unscaledDelta();
        assertThat(unscaled).isGreaterThan(0f);
        assertThat(trace).containsExactly(
                "input.begin", "input a 0.0", "input.end",
                "menu " + unscaled,
                "render.begin", "render a " + unscaled, "render.end");
    }

    @Test
    void disabledSystemsAreSkipped() {
        Family all = world.family(Health.class);
        entity("a");
        EntitySystem system = new Recording("s", Phase.UPDATE, all, e -> { }) {};
        world.addSystem(system);
        system.enabled = false;

        world.runPhase(Phase.UPDATE, time);

        assertThat(trace).isEmpty();
    }

    @Test
    void sameSystemClassTwiceOrForeignFamilyIsRejected() {
        Family all = world.family(Health.class);
        class Mover extends EntitySystem {
            Mover(Family family) {
                super(Phase.FIXED, family);
            }

            @Override
            protected void process(int entity, float delta) {
            }
        }
        world.addSystem(new Mover(all));

        assertThatThrownBy(() -> world.addSystem(new Mover(all)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already registered");
        World other = new World(false);
        assertThatThrownBy(() -> other.addSystem(new Mover(world.family(Velocity.class))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("another World");
        assertThatThrownBy(() -> new Mover(all).world()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clearIsRejectedInsideASystem() {
        Family all = world.family(Health.class);
        entity("a");
        world.addSystem(new Recording("clearer", Phase.UPDATE, all, e -> world.clear()) {});

        assertThatThrownBy(() -> world.runPhase(Phase.UPDATE, time)).isInstanceOf(IllegalStateException.class);
    }
}
