package dev.ember2d.box2d;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.collision.TriggerEntered;
import dev.ember2d.collision.TriggerExited;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Box2D inside the ECS (F-006B, I-500), stepped headless with the desktop natives. */
class Box2dSystemTest {

    static final float DT = 1f / 60f;

    static {
        // A launcher's backend loads libGDX's own natives; a bare test has none, and Box2D bodies need them.
        com.badlogic.gdx.utils.GdxNativesLoader.load();
    }

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(DT);
    final Box2dSystem physics = new Box2dSystem(world, events, time, 0f, -10f);
    final List<Object> seen = new ArrayList<>();

    Box2dSystemTest() {
        world.addSystem(physics);
        for (Class<?> type : List.of(ContactBegan.class, ContactEnded.class, TriggerEntered.class,
                TriggerExited.class)) {
            events.onGlobal(type, seen::add);
        }
    }

    @AfterEach
    void tearDown() {
        physics.dispose();
    }

    void steps(int n) {
        for (int i = 0; i < n; i++) {
            world.runPhase(Phase.FIXED, time);
            events.flush();
        }
    }

    int body(float x, float y, BodyType type) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        RigidBody rb = world.add(e, new RigidBody());
        rb.type = type;
        return e;
    }

    RigidBody rb(int e) {
        return world.get(e, RigidBody.class);
    }

    Transform t(int e) {
        return world.get(e, Transform.class);
    }

    @Test
    void bodiesAreMadeOnTheNextStepAndFallWithGravity() {
        int crate = body(3f, 10f, BodyType.DYNAMIC);
        assertThat(rb(crate).hasBody()).isFalse();
        steps(1);
        assertThat(rb(crate).hasBody()).isTrue();
        assertThat(physics.bodyCount()).isEqualTo(1);
        steps(59);
        assertThat(t(crate).position.y).as("fell for a second").isLessThan(6f);
        assertThat(t(crate).position.x).isCloseTo(3f, within(1e-4f));
        assertThat(t(crate).previous.y).as("previous is one step behind").isGreaterThan(t(crate).position.y);
    }

    @Test
    void staticBodiesStayAndStopFallingBodies() {
        int floor = body(0f, 0f, BodyType.STATIC);
        rb(floor).width = 20f;
        rb(floor).height = 1f;
        int crate = body(0f, 3f, BodyType.DYNAMIC);
        steps(180);
        assertThat(t(floor).position).isEqualTo(new Vector2(0f, 0f));
        assertThat(t(crate).position.y).as("resting on the floor").isCloseTo(1f, within(0.05f));
        assertThat(rb(crate).isAwake()).as("asleep at rest").isFalse();
    }

    @Test
    void bodiesGoWithTheirEntityOrComponentAndDispose() {
        int a = body(0f, 0f, BodyType.DYNAMIC);
        int b = body(5f, 0f, BodyType.DYNAMIC);
        int c = body(9f, 0f, BodyType.DYNAMIC);
        steps(1);
        assertThat(physics.bodyCount()).isEqualTo(3);
        world.destroy(a);
        world.remove(b, RigidBody.class);
        steps(1);
        assertThat(physics.bodyCount()).isEqualTo(1);
        RigidBody removed = world.get(c, RigidBody.class);
        physics.dispose();
        assertThat(physics.bodyCount()).isZero();
        assertThat(removed.hasBody()).isFalse();
        steps(1);   // a disposed system does nothing
        assertThat(physics.bodyCount()).isZero();
    }

    @Test
    void solidContactsAreReportedAfterTheStep() {
        int floor = body(0f, 0f, BodyType.STATIC);
        rb(floor).width = 10f;
        int ball = body(0f, 2f, BodyType.DYNAMIC);
        rb(ball).shape = BodyShape.CIRCLE;
        rb(ball).restitution = 0.8f;
        steps(60);
        assertThat(seen).anySatisfy(e -> {
            assertThat(e).isInstanceOf(ContactBegan.class);
            assertThat(((ContactBegan) e).involves(ball)).isTrue();
            assertThat(((ContactBegan) e).other(ball)).isEqualTo(floor);
        });
        assertThat(seen).as("it bounced off again").anyMatch(e -> e instanceof ContactEnded);
    }

    @Test
    void sensorsReportTriggerEventsAndDoNotBlock() {
        int zone = body(0f, 0f, BodyType.STATIC);
        rb(zone).sensor = true;
        rb(zone).width = 4f;
        int crate = body(0f, 3f, BodyType.DYNAMIC);
        steps(120);
        assertThat(seen).contains(new TriggerEntered(zone, crate), new TriggerExited(zone, crate));
        assertThat(t(crate).position.y).as("fell through the sensor").isLessThan(-3f);
    }

    @Test
    void filtersSkipMaskedCategories() {
        int floor = body(0f, 0f, BodyType.STATIC);
        rb(floor).width = 10f;
        rb(floor).category = 0b10;
        int ghost = body(0f, 3f, BodyType.DYNAMIC);
        rb(ghost).mask = 0b01;   // does not collide with category 2
        steps(120);
        assertThat(t(ghost).position.y).isLessThan(-3f);
        assertThat(seen).noneMatch(e -> e instanceof ContactBegan);
    }

    @Test
    void impulsesVelocitiesAndTeleports() {
        int puck = body(0f, 0f, BodyType.DYNAMIC);
        rb(puck).gravityScale = 0f;
        rb(puck).setVelocity(2f, 0f);   // before the body exists: the start velocity
        steps(60);
        assertThat(t(puck).position.x).isCloseTo(2f, within(0.05f));
        rb(puck).setVelocity(0f, 0f);
        rb(puck).applyImpulse(0f, 1f);   // a 1x1 box of density 1 has mass 1
        steps(60);
        assertThat(rb(puck).velocity(new Vector2()).y).isCloseTo(1f, within(1e-3f));
        rb(puck).teleport(-5f, -5f, 90f);
        assertThat(t(puck).previous).isEqualTo(new Vector2(-5f, -5f));
        rb(puck).setVelocity(0f, 0f);
        steps(1);
        assertThat(t(puck).position.x).isCloseTo(-5f, within(1e-3f));
        assertThat(t(puck).rotation).isCloseTo(90f, within(1e-2f));
    }

    @Test
    void aStackOfTenCratesComesToRest() {
        TileGrid grid = new TileGrid(20, 4, 1f, 1f);
        for (int x = 0; x < 20; x++) {
            grid.setSolid(x, 0, true);
        }
        physics.addTileGrid(grid);
        List<Integer> crates = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int crate = body(10f, 1.5f + i * 1.01f, BodyType.DYNAMIC);
            rb(crate).width = 0.9f;
            rb(crate).height = 1f;
            crates.add(crate);
        }
        steps(300);
        for (int i = 0; i < crates.size(); i++) {
            int crate = crates.get(i);
            assertThat(rb(crate).isAwake()).as("crate %d asleep", i).isFalse();
            assertThat(t(crate).position.x).as("crate %d in its column", i).isCloseTo(10f, within(0.05f));
        }
        assertThat(t(crates.get(9)).position.y).as("the stack stands").isCloseTo(10.5f, within(0.1f));
        assertThat(physics.bodyCount()).as("tile bodies are not counted").isEqualTo(10);
    }

    @Test
    void theKitRegistersRigidBody() {
        ComponentRegistry registry = new ComponentRegistry();
        Box2dKit.register(registry);
        assertThat(registry.names()).contains("RigidBody");
    }
}
