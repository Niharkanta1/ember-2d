package dev.ember2d.kit.topdown;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.Solid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.common.Damaged;
import dev.ember2d.render.Sprite;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Top-down movement (F-016, I-310). */
class TopDownSystemTest {

    static final float DT = 1f / 60f;

    float moveX;
    float moveY;
    final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> {
                if (method.getName().equals("axis2")) {
                    return ((Vector2) args[1]).set(moveX, moveY);
                }
                Class<?> r = method.getReturnType();
                return r == boolean.class ? false : r == float.class ? 0f : r == int.class ? 0 : null;
            });
    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final Collision collision = new Collision(world, events);
    final TopDownSystem system = new TopDownSystem(world, collision, input, events);
    final List<Object> seen = new ArrayList<>();

    TopDownSystemTest() {
        world.addSystem(system);
        world.addSystem(new CollisionSystem(world, collision));
        events.onGlobal(StepFinished.class, seen::add);
    }

    int body(float x, float y) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        world.add(e, new Collider()).box.set(-0.4f, 0f, 0.8f, 0.8f);
        world.add(e, new Actor());
        world.add(e, new TopDownBody());
        return e;
    }

    void wall(float x, float y, float w, float h) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        world.add(e, new Collider()).box.set(0f, 0f, w, h);
        world.add(e, new Solid());
    }

    void steps(int n) {
        for (int i = 0; i < n; i++) {
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    Vector2 pos(int e) {
        return world.get(e, Transform.class).position;
    }

    @Test
    void diagonalsAreNotFasterAndSpeedRampsUpAndDown() {
        int straight = body(0f, 0f);
        moveX = 1f;
        steps(60);
        float straightDistance = pos(straight).x;
        TopDownBody b = world.get(straight, TopDownBody.class);
        assertThat(b.vx).as("reaches max speed").isEqualTo(6f);

        World w2 = world;
        int diagonal = body(0f, 50f);
        world.get(straight, TopDownBody.class).locked = true;
        moveX = 1f;
        moveY = 1f;
        steps(60);
        Vector2 d = pos(diagonal).cpy().sub(0f, 50f);
        assertThat(d.len()).as("the same distance as straight, within a step").isCloseTo(straightDistance,
                org.assertj.core.data.Offset.offset(0.15f));
        TopDownBody db = w2.get(diagonal, TopDownBody.class);
        assertThat(db.facingX).isCloseTo(0.7071f, org.assertj.core.data.Offset.offset(1e-3f));

        moveX = 0f;
        moveY = 0f;
        steps(30);
        assertThat(db.vx).isZero();
        assertThat(db.vy).isZero();
        assertThat(db.moving).isFalse();
        assertThat(b.vx).as("locked: friction only").isZero();
    }

    @Test
    void itSlidesAlongWalls() {
        wall(2f, -5f, 1f, 10f);
        int e = body(0f, 0f);
        moveX = 1f;
        moveY = 1f;
        steps(60);
        assertThat(pos(e).x).as("stopped by the wall").isLessThanOrEqualTo(2f - 0.4f + 1e-4f);
        assertThat(pos(e).y).as("still sliding up").isGreaterThan(3f);
        assertThat(world.get(e, TopDownBody.class).vy).isGreaterThan(4f);
    }

    @Test
    void knockbackFromDamageFadesAndLockStopsInput() {
        int e = body(0f, 0f);
        events.publish(new Damaged(e, World.NONE, 1, 2, 10f, 0f));
        assertThat(world.get(e, TopDownBody.class).knockbackX).isEqualTo(10f);
        steps(60);
        TopDownBody b = world.get(e, TopDownBody.class);
        assertThat(b.knockbackX).as("faded").isZero();
        assertThat(pos(e).x).isGreaterThan(1f);
        float x = pos(e).x;
        b.locked = true;
        moveX = 1f;
        steps(30);
        assertThat(pos(e).x).isEqualTo(x);
        b.knockbackScale = 0f;
        events.publish(new Damaged(e, World.NONE, 1, 2, 10f, 0f));
        assertThat(b.knockbackX).isZero();
    }

    @Test
    void gridStepsMoveOneTileChainWhileHeldAndStopAtWalls() {
        int e = body(0.5f, 0.1f);
        TopDownBody b = world.get(e, TopDownBody.class);
        b.gridStep = true;
        moveX = 1f;
        moveY = 0.3f;   // the dominant axis wins
        steps(5);
        moveX = 0f;
        moveY = 0f;
        steps(20);
        assertThat(pos(e).x).as("exactly one tile").isCloseTo(1.5f, org.assertj.core.data.Offset.offset(1e-4f));
        assertThat(pos(e).y).isEqualTo(0.1f);
        assertThat(seen).containsExactly(new StepFinished(e, 1, 0));

        moveY = 1f;
        steps(25);   // held: more than two steps
        moveY = 0f;
        steps(20);
        assertThat(pos(e).y).isCloseTo(3.1f, org.assertj.core.data.Offset.offset(1e-4f));
        assertThat(b.facingY).isEqualTo(1f);

        wall(2f, 2.5f, 1f, 2f);
        moveX = 1f;
        steps(10);
        moveX = 0f;
        steps(10);
        assertThat(pos(e).x).as("blocked by the wall").isLessThan(2f);
        assertThat(b.stepLeft).isZero();
    }

    @Test
    void facingFlipsTheSprite() {
        int e = body(0f, 0f);
        Sprite sprite = world.add(e, new Sprite());
        moveX = -1f;
        steps(2);
        assertThat(sprite.flipX).isTrue();
        moveX = 1f;
        steps(2);
        assertThat(sprite.flipX).isFalse();
        world.get(e, TopDownBody.class).flipSprite = false;
        moveX = -1f;
        steps(2);
        assertThat(sprite.flipX).isFalse();
    }
}
