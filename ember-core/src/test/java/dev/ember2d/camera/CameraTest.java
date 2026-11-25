package dev.ember2d.camera;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CameraTest {

    private final World world = new World(false);
    private final TimeState time = new TimeState(1f / 60f);
    private final CameraRig rig = new CameraRig();
    private CameraFollow follow;
    private Transform target;

    @BeforeEach
    void setUp() {
        rig.viewSize.set(20f, 11.25f);
        int player = world.create();
        target = world.add(player, Transform.class);
        int camera = world.create();
        follow = world.add(camera, new CameraFollow());
        follow.target = world.ref(player);
        world.addSystem(new CameraFollowSystem(world, rig, time));
    }

    private void frame() {
        new GameLoop(() -> 16_666_668L, time, phase -> { }, 5).frame();
        world.runPhase(Phase.LATE, time);
    }

    @Test
    void followsTheTargetExactlyWithoutDeadZone() {
        target.teleport(7f, 3f);

        frame();

        assertThat(rig.position.x).isEqualTo(7f);
        assertThat(rig.position.y).isEqualTo(3f);
    }

    @Test
    void followsTheInterpolatedPositionSoSpritesAndCameraAgree() {
        target.previous.set(0f, 0f);
        target.position.set(10f, 0f);
        new GameLoop(() -> 8_333_334L, time, phase -> { }, 5).frame();   // alpha 0.5

        world.runPhase(Phase.LATE, time);

        assertThat(rig.position.x).isCloseTo(5f, within(1e-4f));
    }

    @Test
    void deadZoneMovesTheCameraOnlyWhenTheTargetLeavesIt() {
        follow.deadZone.setSize(4f, 2f);
        target.teleport(1.5f, 0f);
        frame();
        assertThat(rig.position.x).as("inside the zone").isZero();

        target.teleport(5f, -3f);
        frame();
        assertThat(rig.position.x).as("pushed to the right edge").isEqualTo(3f);
        assertThat(rig.position.y).as("pushed to the bottom edge").isEqualTo(-2f);
    }

    @Test
    void boundsKeepTheViewInsideAndCentreSmallLevels() {
        follow.bounds = new Rectangle(0f, 0f, 100f, 50f);
        target.teleport(2f, 49f);

        frame();

        assertThat(rig.position.x).isEqualTo(10f);          // half view width from the left edge
        assertThat(rig.position.y).isEqualTo(50f - 5.625f); // half view height from the top
        follow.bounds = new Rectangle(0f, 0f, 12f, 8f);     // smaller than the view
        frame();
        assertThat(rig.position.x).isEqualTo(6f);
        assertThat(rig.position.y).isEqualTo(4f);
    }

    @Test
    void zoomEnlargesTheViewUsedForBounds() {
        rig.zoom = 2f;
        follow.bounds = new Rectangle(0f, 0f, 100f, 50f);
        target.teleport(0f, 0f);

        frame();

        assertThat(rig.viewWidth()).isEqualTo(40f);
        assertThat(rig.position.x).isEqualTo(20f);
    }

    @Test
    void smoothingEasesTowardsTheTarget() {
        follow.smoothing = 10f;
        target.teleport(10f, 0f);

        frame();

        float expected = 10f * (1f - (float) Math.exp(-10f * (1f / 60f)));
        assertThat(rig.position.x).isCloseTo(expected, within(1e-3f)).isLessThan(10f);
    }

    @Test
    void deadTargetLeavesTheCameraWhereItIs() {
        rig.position.set(4f, 4f);
        world.destroy(follow.target.entity(world));

        frame();

        assertThat(rig.position.x).isEqualTo(4f);
    }

    @Test
    void traumaDecaysAndShakeIsBoundedByTraumaSquared() {
        rig.maxShakeOffset = 2f;
        rig.addTrauma(0.6f);
        rig.addTrauma(0.6f);
        assertThat(rig.trauma()).isEqualTo(1f);

        rig.update(0.2f);   // decay 1.5/s -> 0.7

        assertThat(rig.trauma()).isCloseTo(0.7f, within(1e-5f));
        float limit = 2f * 0.7f * 0.7f;
        assertThat(Math.abs(rig.shakeOffset().x)).isLessThanOrEqualTo(limit + 1e-5f);
        assertThat(Math.abs(rig.shakeOffset().y)).isLessThanOrEqualTo(limit + 1e-5f);
        rig.update(1f);
        assertThat(rig.trauma()).isZero();
        assertThat(rig.shakeOffset().isZero()).isTrue();
    }

    @Test
    void deadZoneAxisMath() {
        assertThat(CameraFollowSystem.deadZoneAxis(0f, 1f, 0f, 4f)).isEqualTo(0f);
        assertThat(CameraFollowSystem.deadZoneAxis(0f, 3f, 0f, 4f)).isEqualTo(1f);
        assertThat(CameraFollowSystem.deadZoneAxis(0f, -3f, 1f, 4f)).isEqualTo(-2f);   // zone centred at +1
    }
}
