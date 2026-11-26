package dev.ember2d.collision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Kinematic collision (F-006), with the S-02 layout: 1 tile = 1 unit, floor = row 0 (top at y = 1), wall = column 30
 * from y = 1 to 6, one-way platform = columns 5..9 at row 4 (top at y = 5), ceiling = columns 14..16 at row 7 (bottom
 * at y = 7). Actors are 0.75 x 1.5 boxes standing on their position.
 */
class CollisionTest {

    static final float DT = 1f / 60f;
    static final float GRAVITY = 40f;

    final World world = new World(false);
    final DefaultEventBus bus = new DefaultEventBus(false);
    final Collision collision = new Collision(world, bus);
    final TileGrid grid = new TileGrid(40, 12, 1f, 1f);
    final TimeState time = new TimeState(DT);
    final List<Object> events = new ArrayList<>();
    final float[] vy = new float[1 << 20];

    CollisionTest() {
        for (int x = 0; x < 40; x++) {
            grid.setSolid(x, 0, true);
        }
        for (int y = 1; y <= 5; y++) {
            grid.setSolid(30, y, true);
        }
        for (int x = 5; x <= 9; x++) {
            grid.setOneWay(x, 4, true);
        }
        for (int x = 14; x <= 16; x++) {
            grid.setSolid(x, 7, true);
        }
        collision.addTileGrid(grid, 0);
        world.addSystem(new CollisionSystem(world, collision));
        bus.onGlobal(TriggerEntered.class, events::add);
        bus.onGlobal(TriggerExited.class, events::add);
        bus.onGlobal(ActorSquished.class, events::add);
    }

    int actor(float x, float y) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        world.add(e, new Collider()).box.set(0f, 0f, 0.75f, 1.5f);
        world.add(e, new Actor());
        return e;
    }

    int solid(float x, float y, float w, float h, boolean oneWay) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        world.add(e, new Collider()).box.set(0f, 0f, w, h);
        world.add(e, new Solid()).oneWay = oneWay;
        return e;
    }

    int trigger(float x, float y, float w, float h) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        Collider c = world.add(e, new Collider());
        c.box.set(0f, 0f, w, h);
        c.trigger = true;
        return e;
    }

    Transform t(int e) {
        return world.get(e, Transform.class);
    }

    /** One fixed step of a simple platformer body: gravity, then moveAndSlide. */
    MoveResult fall(int a, float vx) {
        int i = a & 0xFFFFF;
        vy[i] -= GRAVITY * DT;
        MoveResult r = collision.moveAndSlide(a, vx * DT, vy[i] * DT);
        if (r.hitY) {
            vy[i] = 0f;
        }
        return r;
    }

    void fixedStep() {
        world.runPhase(Phase.FIXED, time);
        bus.flush();
    }

    // ---- the S-02 scenarios ----

    @Test
    void runningAcrossTwentyFloorSeamsNeverSnags() {
        int a = actor(1f, 1f);
        float lastX = 1f;
        for (int i = 0; i < 180; i++) {
            MoveResult r = fall(a, 8f);
            if (t(a).position.x + 0.75f < 30f - Collision.EPS) {
                assertThat(r.hitX).as("snag at x=%s", t(a).position.x).isFalse();
                assertThat(t(a).position.x).isGreaterThan(lastX);
            }
            assertThat(t(a).position.y).as("step %d", i).isEqualTo(1f);
            assertThat(collision.isTouching(a, Direction.DOWN)).isTrue();
            lastX = t(a).position.x;
        }
        assertThat(t(a).position.x).isCloseTo(25f, within(1e-3f));
    }

    @Test
    void stopsExactlyAtTheWallAndSlidesDownItWithoutStalling() {
        int a = actor(27f, 5.5f);
        float lastY = 5.5f;
        boolean touched = false;
        for (int i = 0; i < 120 && !collision.isTouching(a, Direction.DOWN); i++) {
            MoveResult r = fall(a, 8f);
            if (r.hitX) {
                touched = true;
                assertThat(t(a).position.x).as("exact contact").isEqualTo(30f - 0.75f);
                assertThat(r.normalX).isEqualTo(-1f);
                assertThat(r.entityX).as("a tile").isEqualTo(World.NONE);
                assertThat(collision.isTouching(a, Direction.RIGHT)).isTrue();
            }
            if (touched && !collision.isTouching(a, Direction.DOWN)) {
                assertThat(t(a).position.y).as("keeps sliding (step %d)", i).isLessThan(lastY);
            }
            lastY = t(a).position.y;
        }
        assertThat(touched).isTrue();
        assertThat(t(a).position.y).isEqualTo(1f);
    }

    @Test
    void landsExactlyAndHitsTheCeilingExactly() {
        int a = actor(3.3f, 6.37f);
        for (int i = 0; i < 120 && !collision.isTouching(a, Direction.DOWN); i++) {
            fall(a, 0f);
        }
        assertThat(t(a).position.y).isEqualTo(1f);
        assertThat(world.get(a, Actor.class).hitY).isTrue();

        int b = actor(15f, 4f);
        MoveResult r = collision.moveAndSlide(b, 0f, 5f);
        assertThat(t(b).position.y + 1.5f).isEqualTo(7f);
        assertThat(r.hitY).isTrue();
        assertThat(r.normalY).isEqualTo(-1f);
        assertThat(collision.isTouching(b, Direction.UP)).isTrue();
    }

    @Test
    void doesNotTunnelAtTenCellsPerMove() {
        int a = actor(25f, 1f);
        collision.moveAndSlide(a, 10f, 0f);
        assertThat(t(a).position.x).isEqualTo(29.25f);

        int b = actor(18f, 3f);
        collision.moveAndSlide(b, 0f, -50f);
        assertThat(t(b).position.y).as("falls 50 units, lands on the floor").isEqualTo(1f);
    }

    @Test
    void oneWayFromBelowFromAboveDropThroughAndSideways() {
        int a = actor(6f, 1f);
        int i = a & 0xFFFFF;
        vy[i] = 20f;
        float peak = 1f;
        for (int s = 0; s < 120; s++) {
            fall(a, 0f);
            peak = Math.max(peak, t(a).position.y);
            if (vy[i] == 0f && t(a).position.y > 1f) {
                break;
            }
        }
        assertThat(peak).as("passed through from below").isGreaterThan(5f);
        assertThat(t(a).position.y).as("landed on top").isEqualTo(5f);
        assertThat(collision.isTouching(a, Direction.DOWN)).isTrue();

        world.get(a, Actor.class).dropThroughTicks = 10;
        assertThat(collision.isTouching(a, Direction.DOWN)).as("dropping: one-way ignored").isFalse();
        for (int s = 0; s < 120 && t(a).position.y > 1f; s++) {
            fall(a, 0f);
        }
        assertThat(t(a).position.y).isEqualTo(1f);

        int b = actor(3f, 4.2f);
        assertThat(collision.moveAndSlide(b, 4f, 0f).hitX).as("one-way never blocks sideways").isFalse();
        assertThat(t(b).position.x).isCloseTo(7f, within(1e-4f));
    }

    @Test
    void movingPlatformCarriesARiderAndPushesABlocker() {
        int p = solid(20f, 3f, 3f, 0.5f, false);
        int rider = actor(21f, 3.5f);
        int blocker = actor(24f, 1f);
        fall(rider, 0f);
        assertThat(collision.isTouching(rider, Direction.DOWN)).isTrue();

        for (int i = 0; i < 60; i++) {
            collision.moveSolid(p, 2f * DT, 0f);
            fall(rider, 0f);
        }
        assertThat(t(p).position.x).isCloseTo(22f, within(1e-3f));
        assertThat(t(rider).position.x).as("carried").isCloseTo(23f, within(1e-3f));
        assertThat(t(rider).position.y).isEqualTo(3.5f);

        int low = solid(26f, 1f, 1f, 1f, false);
        for (int i = 0; i < 30; i++) {
            collision.moveSolid(low, -4f * DT, 0f);
        }
        assertThat(t(blocker).position.x + 0.75f).as("pushed ahead").isCloseTo(t(low).position.x, within(1e-3f));

        for (int i = 0; i < 60; i++) {
            collision.moveSolid(p, 0f, 1f * DT);
            fall(rider, 0f);
        }
        assertThat(t(rider).position.y).as("lifted").isCloseTo(t(p).position.y + 0.5f, within(1e-3f));
    }

    @Test
    void oneWayPlatformCarriesButNeverPushes() {
        int p = solid(10f, 2f, 3f, 0.25f, true);
        int rider = actor(11f, 2.25f);
        int beside = actor(13.5f, 1f);   // in the way, below the top
        collision.moveSolid(p, 0f, 0.5f);
        assertThat(t(rider).position.y).isCloseTo(2.75f, within(1e-4f));
        collision.moveSolid(p, 1f, 0f);
        assertThat(t(beside).position.x).as("not pushed").isEqualTo(13.5f);
        assertThat(t(rider).position.x).isCloseTo(12f, within(1e-4f));
    }

    @Test
    void anActorPushedIntoAWallIsSquished() {
        int a = actor(29.25f, 1f);   // touching the wall
        int p = solid(27f, 1f, 1f, 1f, false);

        collision.moveSolid(p, 1.5f, 0f);
        bus.flush();

        assertThat(t(a).position.x).as("cannot move into the wall").isEqualTo(29.25f);
        assertThat(events).containsExactly(new ActorSquished(a, p));
    }

    // ---- more moves ----

    @Test
    void moveAndCollideStopsBothAxesButMoveAndSlideContinues() {
        int a = actor(5f, 2f);
        MoveResult r = collision.moveAndCollide(a, 3f, -2f);
        assertThat(r.hitY).isTrue();
        assertThat(t(a).position.y).isEqualTo(1f);
        assertThat(t(a).position.x).as("stopped part way").isBetween(6.4f, 7.1f);

        int b = actor(5f, 2f);
        collision.moveAndSlide(b, 3f, -2f);
        assertThat(t(b).position.x).isEqualTo(8f);
        assertThat(t(b).position.y).isEqualTo(1f);
    }

    @Test
    void solidBoxesBlockLikeTilesAndReportTheEntity() {
        int box = solid(12f, 1f, 1f, 2f, false);
        int a = actor(9f, 1f);
        MoveResult r = collision.moveAndSlide(a, 5f, 0f);
        assertThat(t(a).position.x).isEqualTo(11.25f);
        assertThat(r.entityX).isEqualTo(box);
        assertThat(world.get(a, Actor.class).hitEntityX).isEqualTo(box);
        assertThat(collision.isTouching(a, Direction.RIGHT)).isTrue();
        assertThat(collision.isTouching(a, Direction.LEFT)).isFalse();

        int b = actor(12.1f, 6f);
        collision.moveAndSlide(b, 0f, -10f);
        assertThat(t(b).position.y).as("lands on the box").isEqualTo(3f);
        assertThat(world.get(b, Actor.class).hitEntityY).isEqualTo(box);
    }

    @Test
    void layersAndMasksFilterWhatBlocks() {
        int ghostWall = solid(12f, 1f, 1f, 2f, false);
        world.get(ghostWall, Collider.class).layer = 2;
        int a = actor(9f, 1f);
        world.get(a, Collider.class).mask = ~(1 << 2);
        collision.moveAndSlide(a, 5f, 0f);
        assertThat(t(a).position.x).as("layer 2 not in the mask").isEqualTo(14f);

        Collision other = new Collision(world, bus);
        other.addTileGrid(grid, 1);
        int b = actor(20f, 3f);
        world.get(b, Collider.class).mask = 1;   // only layer 0
        other.moveAndSlide(b, 0f, -5f);
        assertThat(t(b).position.y).as("the grid is on layer 1").isEqualTo(-2f);
        assertThatThrownBy(() -> other.addTileGrid(grid, 32)).hasMessageContaining("0-31");
    }

    @Test
    void misuseIsReported() {
        int s = solid(0f, 5f, 1f, 1f, false);
        assertThatThrownBy(() -> collision.moveAndSlide(s, 1f, 0f)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an actor");
        int a = actor(0f, 5f);
        assertThatThrownBy(() -> collision.moveSolid(a, 1f, 0f)).hasMessageContaining("is not a solid");
        collision.moveAndSlide(a, 0f, 0f);
        assertThatThrownBy(() -> collision.setCellSize(4f)).isInstanceOf(IllegalStateException.class);
    }

    // ---- triggers ----

    @Test
    void triggersReportEnterAndExitOncePerChange() {
        int zone = trigger(10f, 1f, 2f, 2f);
        int a = actor(7f, 1f);
        fixedStep();
        assertThat(events).isEmpty();

        collision.moveAndSlide(a, 3f, 0f);   // into the zone
        fixedStep();
        fixedStep();
        assertThat(events).containsExactly(new TriggerEntered(zone, a));
        assertThat(collision.inside(zone, new IntArray())).isEqualTo(1);

        collision.moveAndSlide(a, 5f, 0f);   // out
        fixedStep();
        assertThat(events).containsExactly(new TriggerEntered(zone, a), new TriggerExited(zone, a));
    }

    @Test
    void destroyingAnActorOrATriggerInsideGivesExit() {
        int zone = trigger(10f, 1f, 2f, 2f);
        int a = actor(10.5f, 1f);
        int b = actor(11f, 1f);
        fixedStep();
        assertThat(events).containsExactly(new TriggerEntered(zone, a), new TriggerEntered(zone, b));
        events.clear();

        world.destroy(a);
        fixedStep();
        assertThat(events).containsExactly(new TriggerExited(zone, a));
        events.clear();

        world.destroy(zone);
        fixedStep();
        assertThat(events).containsExactly(new TriggerExited(zone, b));
    }

    @Test
    void triggerMaskFiltersActorLayers() {
        int zone = trigger(10f, 1f, 2f, 2f);
        world.get(zone, Collider.class).mask = 1 << 3;
        int a = actor(10.5f, 1f);
        fixedStep();
        assertThat(events).isEmpty();
        world.get(a, Collider.class).layer = 3;
        fixedStep();
        assertThat(events).containsExactly(new TriggerEntered(zone, a));
    }

    // ---- queries ----

    @Test
    void overlapAndPointAndNearestQueries() {
        int s = solid(12f, 1f, 1f, 2f, false);
        int a = actor(10f, 1f);
        int z = trigger(10f, 1f, 4f, 4f);
        IntArray out = new IntArray();

        assertThat(collision.overlapBox(new Rectangle(9f, 0f, 5f, 3f), ~0, out)).isEqualTo(3);
        assertThat(out.toArray()).as("sorted by entity").containsExactly(s, a, z);
        assertThat(collision.overlapBox(new Rectangle(9f, 0f, 5f, 3f), 1, out)).isEqualTo(3);
        assertThat(collision.overlapBox(new Rectangle(9f, 0f, 5f, 3f), 1 << 4, out)).isZero();
        assertThat(collision.overlapPoint(12.5f, 2f, ~0, out)).isEqualTo(2);
        assertThat(out.toArray()).containsExactly(s, z);
        assertThat(collision.nearest(10.9f, 4f, 2f, ~0)).as("inside the trigger: distance 0").isEqualTo(z);
        assertThat(collision.nearest(9.5f, 2f, 1f, 1)).as("tie at 0.5: lowest entity").isEqualTo(a);
        assertThat(collision.nearest(30f, 10f, 1f, ~0)).isEqualTo(World.NONE);
    }

    @Test
    void raysHitBoxesAndTilesNearestFirst() {
        int s = solid(12f, 1f, 1f, 2f, false);
        int a = actor(20f, 1f);
        trigger(8f, 1f, 1f, 2f);   // rays ignore triggers
        RayHit hit = new RayHit();

        assertThat(collision.raycast(5f, 2f, 40f, 2f, ~0, hit)).isTrue();
        assertThat(hit.entity).isEqualTo(s);
        assertThat(hit.tile).isFalse();
        assertThat(hit.x).isCloseTo(12f, within(1e-4f));
        assertThat(hit.distance).isCloseTo(7f, within(1e-4f));
        assertThat(hit.normalX).isEqualTo(-1f);

        assertThat(collision.raycast(25f, 2f, 40f, 2f, ~0, hit)).isTrue();
        assertThat(hit.tile).as("the wall at column 30").isTrue();
        assertThat(hit.entity).isEqualTo(World.NONE);
        assertThat(hit.x).isCloseTo(30f, within(1e-4f));
        assertThat(hit.normalX).isEqualTo(-1f);

        assertThat(collision.raycast(3f, 8f, 3f, -5f, ~0, hit)).isTrue();
        assertThat(hit.y).as("the floor's top").isCloseTo(1f, within(1e-4f));
        assertThat(hit.normalY).isEqualTo(1f);
        assertThat(collision.raycast(7f, 8f, 7f, 5.5f, ~0, hit)).as("one-way tiles do not stop rays").isFalse();

        IntArray out = new IntArray();
        assertThat(collision.raycastAll(25f, 2f, 5f, 2f, ~0, out)).isEqualTo(2);
        assertThat(out.toArray()).as("nearest first").containsExactly(a, s);
    }

    @Test
    void entitiesCreatedLaterJoinAtTheNextStep() {
        int a = actor(9f, 1f);
        collision.moveAndSlide(a, 0f, 0f);   // hash built
        solid(12f, 1f, 1f, 2f, false);
        fixedStep();
        collision.moveAndSlide(a, 5f, 0f);
        assertThat(t(a).position.x).isEqualTo(11.25f);
    }

    @Test
    void fiveHundredActorsOnATileGridWithTwentyBoxes() {
        Random random = new Random(42);
        for (int i = 0; i < 20; i++) {
            solid(2f + i * 1.4f, 1f + (i % 3), 1f, 0.5f, i % 2 == 0);
        }
        int[] actors = new int[500];
        float[] vx = new float[500];
        for (int i = 0; i < 500; i++) {
            actors[i] = actor(1f + random.nextFloat() * 27f, 1f + random.nextFloat() * 8f);
            vx[i] = (random.nextFloat() - 0.5f) * 16f;
        }
        for (int warm = 0; warm < 300; warm++) {
            stepAll(actors, vx);
        }
        long start = System.nanoTime();
        for (int s = 0; s < 600; s++) {
            stepAll(actors, vx);
        }
        double msPerStep = (System.nanoTime() - start) / 600 / 1e6;
        System.out.printf("I120-PERF 500 actors, 20 boxes, 40x12 grid: %.3f ms per fixed step%n", msPerStep);
        assertThat(msPerStep).as("ms per fixed step").isLessThan(2.0);
    }

    private void stepAll(int[] actors, float[] vx) {
        for (int i = 0; i < actors.length; i++) {
            if (fall(actors[i], vx[i]).hitX) {
                vx[i] = -vx[i];
            }
        }
        world.runPhase(Phase.FIXED, time);
    }
}
