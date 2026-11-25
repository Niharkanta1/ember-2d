package dev.ember2d.ecs;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import java.util.Arrays;
import java.util.Locale;

/**
 * F-003 performance target: 5,000 entities with 3 systems at 60 fps. Simulates 60 Hz frames through the real
 * game loop (one fixed step per frame) and reports the time the ECS needs per frame. Run with
 * {@code gradlew :ember-core:benchmarkEcs}; numbers are recorded in F-003.
 */
public final class EcsBenchmark {

    static final class Velocity {
        final Vector2 value = new Vector2();
    }

    static final class Lifetime {
        float seconds;
    }

    private static final int ENTITIES = 5_000;
    private static final int WARMUP_FRAMES = 2_000;
    private static final int MEASURED_FRAMES = 6_000;
    private static final float BOUNDS = 1_000f;

    private static float checksum;

    private EcsBenchmark() {
    }

    public static void main(String[] args) {
        World world = new World(false);
        world.registerComponent(Velocity.class, Velocity::new);
        world.registerComponent(Lifetime.class, Lifetime::new);
        Family moving = world.family(Transform.class, Velocity.class);

        world.addSystem(new EntitySystem(Phase.FIXED, moving) {
            @Override
            protected void process(int entity, float delta) {
                world().get(entity, Transform.class).position.mulAdd(world().get(entity, Velocity.class).value, delta);
            }
        });
        world.addSystem(new EntitySystem(Phase.UPDATE, moving) {
            @Override
            protected void process(int entity, float delta) {
                Transform t = world().get(entity, Transform.class);
                Velocity v = world().get(entity, Velocity.class);
                if (t.position.x < 0 || t.position.x > BOUNDS) {
                    v.value.x = -v.value.x;
                }
                if (t.position.y < 0 || t.position.y > BOUNDS) {
                    v.value.y = -v.value.y;
                }
            }
        });
        Vector2 scratch = new Vector2();
        world.addSystem(new EntitySystem(Phase.LATE, world.family(Transform.class, Lifetime.class)) {
            @Override
            protected void process(int entity, float delta) {
                Lifetime life = world().get(entity, Lifetime.class);
                life.seconds -= delta;
                checksum += world().get(entity, Transform.class).interpolated(0.5f, scratch).x;
                if (life.seconds <= 0f) {
                    world().destroy(entity);   // churn: about 1% of entities are replaced per second
                }
            }
        });

        for (int i = 0; i < ENTITIES; i++) {
            spawn(world, i);
        }

        TimeState time = new TimeState(1f / 60f);
        int[] spawned = {ENTITIES};
        GameLoop loop = new GameLoop(() -> 16_666_668L, time, phase -> {
            world.runPhase(phase, time);
            if (phase == Phase.RENDER) {
                while (world.entityCount() < ENTITIES) {
                    spawn(world, spawned[0]++);
                }
            }
        }, 5);

        for (int i = 0; i < WARMUP_FRAMES; i++) {
            loop.frame();
        }
        long[] frameNanos = new long[MEASURED_FRAMES];
        for (int i = 0; i < MEASURED_FRAMES; i++) {
            long start = System.nanoTime();
            loop.frame();
            frameNanos[i] = System.nanoTime() - start;
        }

        Arrays.sort(frameNanos);
        double mean = Arrays.stream(frameNanos).average().orElse(0) / 1e6;
        System.out.printf(Locale.ROOT, "ECS benchmark: %d entities, 3 systems, %d measured frames (after %d warm-up)%n",
                ENTITIES, MEASURED_FRAMES, WARMUP_FRAMES);
        System.out.printf(Locale.ROOT, "  mean %.3f ms | median %.3f ms | p99 %.3f ms | max %.3f ms per frame%n",
                mean, frameNanos[MEASURED_FRAMES / 2] / 1e6, frameNanos[(int) (MEASURED_FRAMES * 0.99)] / 1e6,
                frameNanos[MEASURED_FRAMES - 1] / 1e6);
        System.out.printf(Locale.ROOT, "  budget at 60 fps: 16.667 ms; entities replaced: %d; checksum %.1f%n",
                spawned[0] - ENTITIES, checksum);
        System.out.printf(Locale.ROOT, "  JVM: %s %s, %s (%s)%n", System.getProperty("java.vendor"),
                System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    private static void spawn(World world, int seed) {
        int e = world.create();
        Transform t = world.add(e, Transform.class);
        t.teleport((seed * 37) % BOUNDS, (seed * 91) % BOUNDS);
        world.add(e, Velocity.class).value.set((seed % 13) - 6, (seed % 7) - 3).scl(20f);
        world.add(e, Lifetime.class).seconds = 10f + (seed % 900) / 10f;
    }
}
