package dev.ember2d.demo.platformer.spike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.maps.tiled.TiledMap;
import dev.ember2d.demo.platformer.spike.SpikeMover.Actor;
import dev.ember2d.demo.platformer.spike.SpikeMover.Platform;
import dev.ember2d.demo.platformer.spike.SpikeMover.TileGrid;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * SPIKE S-02 scenarios on the sample map (world units, 1 tile = 1 unit, y up). Layout: floor = row 0 (top at y = 1),
 * wall = column 30 from y = 1 to 6, one-way platform = columns 5..9 at row 4 (top at y = 5), ceiling = columns 14..16
 * at row 7 (bottom at y = 7).
 */
class CollisionSpikeTest {

    static final float DT = 1f / 60f;
    static final float GRAVITY = 40f;
    static TileGrid grid;

    @BeforeAll
    static void load() {
        SpikeGdx.install();
        TiledMap map = SpikeGdx.tmx();
        grid = TileGrid.from(map, "ground");
    }

    /** One fixed step of a simple platformer body: gravity, then move. */
    static void step(SpikeMover mover, Actor a) {
        a.vy -= GRAVITY * DT;
        mover.move(a, a.vx * DT, a.vy * DT);
    }

    @Test
    void gridMatchesTheMap() {
        assertThat(grid.solid(0, 0)).isTrue();
        assertThat(grid.solid(39, 0)).isTrue();
        assertThat(grid.solid(30, 5)).isTrue();
        assertThat(grid.oneWay(7, 4)).isTrue();
        assertThat(grid.solid(7, 4)).isFalse();
        assertThat(grid.solid(15, 7)).isTrue();
    }

    @Test
    void runningAcrossTwentyFloorSeamsNeverSnags() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(1f, 1f, 0.75f, 1.5f));
        a.vx = 8f;
        float lastX = a.x;
        for (int i = 0; i < 180; i++) {   // 3 s at 8 units/s = 24 units, wall at x = 30
            step(mover, a);
            if (a.x + a.w < 30f - SpikeMover.EPS) {
                assertThat(a.hitX).as("snag at x=%s (step %d)", a.x, i).isFalse();
                assertThat(a.x).as("progress at step %d", i).isGreaterThan(lastX);
            }
            assertThat(a.y).as("stays on the floor at step %d", i).isEqualTo(1f);
            assertThat(a.grounded).isTrue();
            lastX = a.x;
        }
        assertThat(a.x).isCloseTo(1f + 24f, within(1e-3f));
    }

    @Test
    void stopsExactlyAtTheWallAndSlidesDownItWithoutStalling() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(27f, 5.5f, 0.75f, 1.5f));   // in the air, left of the wall
        a.vx = 8f;
        float lastY = a.y;
        boolean touchedWall = false;
        for (int i = 0; i < 120 && !a.grounded; i++) {
            step(mover, a);
            if (a.hitX) {
                touchedWall = true;
                assertThat(a.x).as("exact contact").isEqualTo(30f - 0.75f);
            }
            if (touchedWall && !a.grounded) {
                assertThat(a.y).as("keeps sliding down the stacked wall tiles (step %d)", i).isLessThan(lastY);
            }
            lastY = a.y;
        }
        assertThat(touchedWall).isTrue();
        assertThat(a.grounded).isTrue();
        assertThat(a.y).isEqualTo(1f);
    }

    @Test
    void landsExactlyOnTheFloor() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(3.3f, 6.37f, 0.75f, 1.5f));
        for (int i = 0; i < 120 && !a.grounded; i++) {
            step(mover, a);
        }
        assertThat(a.y).isEqualTo(1f);
        assertThat(a.vy).isZero();
    }

    @Test
    void doesNotTunnelThroughAOneTileWallAtHighSpeed() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(25f, 1f, 0.75f, 1.5f));

        mover.move(a, 60f * DT * 10, 0f);   // 10 units in one call, far more than any demo speed per step

        assertThat(a.x).isEqualTo(30f - 0.75f);
        assertThat(a.hitX).isTrue();
    }

    @Test
    void hitsTheCeilingExactly() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(15f, 4f, 0.75f, 1.5f));

        mover.move(a, 0f, 5f);

        assertThat(a.y + a.h).isEqualTo(7f);
    }

    @Test
    void oneWayPlatformPassFromBelowLandFromAboveDropThrough() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(6f, 1f, 0.75f, 1.5f));
        a.vy = 20f;   // jump through the platform from below (peak = 1 + 20^2 / 80 = 6)
        float peak = a.y;
        for (int i = 0; i < 120; i++) {
            step(mover, a);
            peak = Math.max(peak, a.y);
            if (a.grounded && a.vy == 0f && a.y > 1f) {
                break;
            }
        }
        assertThat(peak).as("passed through from below").isGreaterThan(5f);
        assertThat(a.y).as("landed on top of the one-way platform").isEqualTo(5f);

        a.dropThroughTicks = 10;
        for (int i = 0; i < 120 && !(a.grounded && a.y == 1f); i++) {
            step(mover, a);
        }
        assertThat(a.y).as("dropped through to the floor").isEqualTo(1f);
    }

    @Test
    void oneWayPlatformDoesNotBlockSidewaysMovement() {
        SpikeMover mover = new SpikeMover(grid);
        Actor a = mover.add(new Actor(3f, 4.2f, 0.75f, 1.5f));   // overlapping the platform's row, moving right

        mover.move(a, 4f, 0f);

        assertThat(a.hitX).isFalse();
        assertThat(a.x).isCloseTo(7f, within(1e-4f));
    }

    @Test
    void movingPlatformCarriesARiderAndPushesABlocker() {
        SpikeMover mover = new SpikeMover(grid);
        Platform p = mover.add(new Platform(20f, 3f, 3f, 0.5f));
        Actor rider = mover.add(new Actor(21f, 3.5f, 0.75f, 1.5f));
        Actor blocker = mover.add(new Actor(24f, 1f, 0.75f, 1.5f));
        step(mover, rider);
        assertThat(rider.grounded).isTrue();

        for (int i = 0; i < 60; i++) {   // 1 s at 2 units/s to the right
            mover.movePlatform(p, 2f * DT, 0f);
            step(mover, rider);
        }
        assertThat(p.x).isCloseTo(22f, within(1e-3f));
        assertThat(rider.x).as("carried with the platform").isCloseTo(23f, within(1e-3f));
        assertThat(rider.y).isEqualTo(3.5f);

        Platform low = mover.add(new Platform(26f, 1f, 1f, 1f));
        for (int i = 0; i < 30; i++) {
            mover.movePlatform(low, -4f * DT, 0f);   // into the blocker standing on the floor
        }
        assertThat(blocker.x + blocker.w).as("pushed ahead of the platform").isCloseTo(low.x, within(1e-3f));

        for (int i = 0; i < 60; i++) {
            mover.movePlatform(p, 0f, 1f * DT);   // lift
            step(mover, rider);
        }
        assertThat(rider.y).as("lifted with the platform").isCloseTo(p.y + p.h, within(1e-3f));
    }

    @Test
    void performanceGridVersusPerTileBoxesVersusMergedBoxes() {
        long gridNanos = measure(new SpikeMover(grid));
        long perTile = measure(boxesMover(false));
        long merged = measure(boxesMover(true));
        System.out.printf("S02-PERF 500 actors, 600 fixed steps: tile grid %.3f ms/step, per-tile boxes %.3f ms/step, "
                + "merged boxes %.3f ms/step%n", gridNanos / 600 / 1e6, perTile / 600 / 1e6, merged / 600 / 1e6);
    }

    @Test
    void perTileBoxesAlsoDoNotSnagOnSeams() {
        SpikeMover mover = boxesMover(false);
        Actor a = mover.add(new Actor(1f, 1f, 0.75f, 1.5f));
        a.vx = 8f;
        for (int i = 0; i < 150; i++) {
            step(mover, a);
            assertThat(a.hitX).as("snag on box seam at x=%s", a.x).isFalse();
            assertThat(a.y).isEqualTo(1f);
        }
    }

    /** The same solids as static boxes (one per tile, or merged into runs) on an empty grid. */
    static SpikeMover boxesMover(boolean merge) {
        SpikeMover mover = new SpikeMover(new TileGrid(grid.width, grid.height));
        for (int y = 0; y < grid.height; y++) {
            int x = 0;
            while (x < grid.width) {
                if (!grid.solid(x, y)) {
                    x++;
                    continue;
                }
                int end = x;
                while (merge && end + 1 < grid.width && grid.solid(end + 1, y)) {
                    end++;
                }
                mover.add(new Platform(x, y, end - x + 1, 1f));
                x = end + 1;
            }
        }
        System.out.println("S02-BOXES merge=" + merge + " boxes=" + mover.platforms.size);
        return mover;
    }

    static long measure(SpikeMover mover) {
        Random random = new Random(42);
        for (int i = 0; i < 500; i++) {
            Actor a = mover.add(new Actor(1f + random.nextFloat() * 27f, 1f + random.nextFloat() * 8f, 0.75f, 1.5f));
            a.vx = (random.nextFloat() - 0.5f) * 16f;
        }
        for (int warm = 0; warm < 200; warm++) {
            for (Actor a : mover.actors) {
                step(mover, a);
            }
        }
        long start = System.nanoTime();
        for (int s = 0; s < 600; s++) {
            for (Actor a : mover.actors) {
                if (a.hitX) {
                    a.vx = -a.vx;
                }
                step(mover, a);
            }
        }
        return System.nanoTime() - start;
    }
}
