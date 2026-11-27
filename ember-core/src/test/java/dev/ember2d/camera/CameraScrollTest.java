package dev.ember2d.camera;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The scrolling camera (F-004, I-310). */
class CameraScrollTest {

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final CameraRig rig = new CameraRig();
    final TimeState time = new TimeState(1f / 60f);
    final List<Object> seen = new ArrayList<>();

    CameraScrollTest() {
        rig.viewSize.set(20f, 10f);
        rig.position.set(10f, 5f);
        world.addSystem(new CameraScrollSystem(world, rig, events));
        events.onGlobal(ScrollFinished.class, seen::add);
    }

    final GameLoop loop = new GameLoop(() -> 16_666_668L, time, phase -> world.runPhase(phase, time), 5);

    void frames(int n) {
        for (int i = 0; i < n; i++) {
            loop.frame();
        }
    }

    @Test
    void itScrollsStopsAtItsBoundsAndClampsTheTaggedEntities() {
        int camera = world.create();
        CameraScroll scroll = world.add(camera, new CameraScroll());
        scroll.speedY = 3f;
        scroll.bounds = new Rectangle(0f, 0f, 20f, 20f);
        int player = world.create();
        Transform t = world.add(player, new Transform());
        t.teleport(10f, 0.2f);
        world.tag(player, "player");
        int rock = world.create();
        world.add(rock, new Transform()).teleport(10f, 0f);

        frames(1);
        float movedBy = rig.position.y - 5f;
        assertThat(movedBy).isGreaterThan(0f);
        assertThat(t.position.y).as("pushed up into the view").isEqualTo(rig.position.y - 5f + 0.5f);
        assertThat(t.previous.y).as("the previous position moves too").isEqualTo(t.position.y);
        assertThat(world.get(rock, Transform.class).position.y).as("not tagged").isZero();

        frames(600);
        assertThat(rig.position.y).as("the view's top at the bounds' top").isEqualTo(15f);
        assertThat(scroll.finished).isTrue();
        assertThat(seen).containsExactly(new ScrollFinished(camera));
        frames(60);
        assertThat(seen).hasSize(1);

        t.teleport(40f, 40f);
        frames(1);
        assertThat(t.position.x).isEqualTo(19.5f);
        assertThat(t.position.y).isEqualTo(19.5f);
    }

    @Test
    void withoutBoundsItNeverFinishes() {
        CameraScroll scroll = world.add(world.create(), new CameraScroll());
        scroll.speedX = 6f;
        scroll.speedY = 0f;
        frames(120);
        assertThat(rig.position.x).isCloseTo(22f, org.assertj.core.data.Offset.offset(0.05f));
        assertThat(rig.position.y).isEqualTo(5f);
        assertThat(seen).isEmpty();
    }
}
