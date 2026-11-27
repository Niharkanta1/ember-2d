package dev.ember2d.kit.topdown.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.FloatArray;
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
import dev.ember2d.kit.topdown.TopDownBody;
import dev.ember2d.kit.topdown.TopDownSystem;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

/** Enemy AI hooks (F-016, I-330): pathfinding and steering through gdx-ai. */
class AiTest {

    /** 12 x 10 cells of 1 unit: a wall at x = 6 from y = 0 to 7; the only gap is at the top (y 8 and 9). */
    static TileGrid grid() {
        TileGrid g = new TileGrid(12, 10, 1f, 1f);
        for (int y = 0; y < 8; y++) {
            g.setSolid(6, y, true);
        }
        return g;
    }

    final Input noInput = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getName().equals("axis2") ? ((Vector2) args[1]).setZero()
                    : method.getReturnType() == boolean.class ? false : null);
    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(1f / 60f);
    final TileGrid grid = grid();
    final PathPlanner planner = new PathPlanner(grid);

    int body(float x, float y) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        world.add(e, new Collider()).box.set(-0.3f, -0.3f, 0.6f, 0.6f);
        world.add(e, new Actor());
        world.add(e, new TopDownBody());
        return e;
    }

    void steps(int n) {
        for (int i = 0; i < n; i++) {
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    @Test
    void pathsGoAroundWallsWithoutCuttingCorners() {
        FloatArray path = new FloatArray();
        assertThat(planner.find(1.5f, 1.5f, 10.5f, 1.5f, path)).isTrue();
        float highest = 0f;
        for (int i = 1; i < path.size; i += 2) {
            highest = Math.max(highest, path.get(i));
            assertThat(planner.walkable(path.get(i - 1), path.get(i))).isTrue();
        }
        assertThat(highest).as("through the gap at the top").isGreaterThanOrEqualTo(8.5f);
        assertThat(path.get(path.size - 2)).isEqualTo(10.5f);
        assertThat(path.get(path.size - 1)).isEqualTo(1.5f);
        for (int i = 2; i < path.size; i += 2) {
            float x0 = path.get(i - 2);
            float y0 = path.get(i - 1);
            float x1 = path.get(i);
            float y1 = path.get(i + 1);
            if (x0 != x1 && y0 != y1) {
                assertThat(planner.walkable(x1, y0) && planner.walkable(x0, y1)).as("no corner cut").isTrue();
            }
        }

        for (int y = 8; y < 10; y++) {
            grid.setSolid(6, y, true);
        }
        planner.refresh();
        assertThat(planner.find(1.5f, 1.5f, 10.5f, 1.5f, path)).as("walled off").isFalse();
        assertThat(path.size).isZero();
        assertThat(planner.find(6.5f, 1.5f, 1.5f, 1.5f, path)).as("starts in a wall").isFalse();
        assertThat(planner.find(-3f, 1.5f, 1.5f, 1.5f, path)).as("outside the grid").isFalse();
    }

    @Test
    void anEnemyFollowsItsPathAroundTheWall() {
        Collision collision = new Collision(world, events);
        collision.addTileGrid(grid, 0);
        world.addSystem(new AiSystem(world, planner));
        world.addSystem(new TopDownSystem(world, collision, noInput, events));
        world.addSystem(new CollisionSystem(world, collision));
        int enemy = body(1.5f, 1.5f);
        int player = world.create();
        world.add(player, new Transform()).teleport(10.5f, 1.5f);
        Steering s = world.add(enemy, new Steering());
        s.target = world.ref(player);
        Vector2 at = world.get(enemy, Transform.class).position;
        float highest = 0f;
        for (int i = 0; i < 60 * 15 && at.dst(10.5f, 1.5f) > 0.35f; i++) {
            steps(1);
            highest = Math.max(highest, at.y);
            assertThat(grid.solid(grid.cellX(at.x), grid.cellY(at.y))).as("never inside the wall").isFalse();
        }
        assertThat(at.dst(10.5f, 1.5f)).as("reached the target").isLessThanOrEqualTo(0.35f);
        assertThat(highest).as("went round through the gap").isGreaterThan(7.5f);
        assertThat(s.pathFound).isTrue();
    }

    @Test
    void seekFleeAndArrive() {
        world.addSystem(new AiSystem(world, null));
        int e = body(0f, 0f);
        Steering s = world.add(e, new Steering());
        TopDownBody b = world.get(e, TopDownBody.class);
        s.mode = Steering.Mode.SEEK;
        s.targetX = 10f;
        steps(1);
        assertThat(b.useInput).isFalse();
        assertThat(b.moveX).isCloseTo(1f, within(1e-4f));
        assertThat(b.moveY).isCloseTo(0f, within(1e-4f));

        s.mode = Steering.Mode.FLEE;
        steps(1);
        assertThat(b.moveX).isCloseTo(-1f, within(1e-4f));
        s.fleeRadius = 5f;
        steps(1);
        assertThat(b.moveX).as("the target is farther than fleeRadius").isZero();

        s.mode = Steering.Mode.ARRIVE;
        s.targetX = 1f;   // inside the slow radius of 2: half speed
        steps(1);
        assertThat(b.moveX).isCloseTo(0.5f, within(0.01f));
        s.targetX = 0.2f;   // inside the arrive radius
        steps(1);
        assertThat(b.moveX).isZero();

        s.mode = Steering.Mode.SEEK;
        s.targetX = 10f;
        s.sight = 5f;
        steps(1);
        assertThat(b.moveX).as("out of sight").isZero();
        s.sight = 0f;
        int target = world.create();
        world.add(target, new Transform()).teleport(0f, 4f);
        s.target = world.ref(target);
        steps(1);
        assertThat(b.moveY).as("the entity wins over the point").isCloseTo(1f, within(1e-4f));
        world.destroy(target);
        steps(1);
        assertThat(b.moveX).isZero();
        assertThat(b.moveY).as("a dead target stops the body").isZero();
    }

    @Test
    void codeCanDriveABody() {
        Collision collision = new Collision(world, events);
        world.addSystem(new TopDownSystem(world, collision, noInput, events));
        world.addSystem(new CollisionSystem(world, collision));
        int e = body(0f, 0f);
        TopDownBody b = world.get(e, TopDownBody.class);
        b.useInput = false;
        b.moveX = 3f;   // clamped to length 1
        steps(60);
        assertThat(b.vx).isEqualTo(b.maxSpeed);
        assertThat(world.get(e, Transform.class).position.x).isGreaterThan(4f);
    }
}
