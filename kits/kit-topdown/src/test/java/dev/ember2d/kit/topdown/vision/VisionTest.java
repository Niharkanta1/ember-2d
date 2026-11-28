package dev.ember2d.kit.topdown.vision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.Solid;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.render.Sprite;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Field of view and hiding in the fog (F-030, I-610), headless. Units: 1 world unit = 16 original pixels. */
class VisionTest {

    static final int WALLS = 3;          // collision layer of walls that block sight
    static final int PROPS = 0;          // low props: block movement, not sight
    static final int SIGHT = 1 << WALLS;

    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(1f / 60f);
    final Collision collision = new Collision(world, events);
    final VisionSettings settings = new VisionSettings();
    final List<Object> seen = new ArrayList<>();

    VisionTest() {
        world.addSystem(new CollisionSystem(world, collision));
        events.onGlobal(CameIntoView.class, seen::add);
        events.onGlobal(WentOutOfView.class, seen::add);
    }

    int box(float x, float y, float w, float h, int layer) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        Collider c = world.add(e, new Collider());
        c.box.set(0f, 0f, w, h);
        c.layer = layer;
        world.add(e, new Solid());
        return e;
    }

    VisionField field() {
        VisionField f = new VisionField(collision, SIGHT, settings);
        world.runPhase(Phase.FIXED, time);   // builds the collision index
        return f;
    }

    @Test
    void anOpenRoomShowsTheCircleAndTheCone() {
        VisionField f = field();
        f.update(0f, 0f, 0f);   // looking right
        assertThat(f.isVisible(2f, 0f)).as("inside the near circle").isTrue();
        assertThat(f.isVisible(-2f, 0f)).as("behind, inside the circle").isTrue();
        assertThat(f.isVisible(-3f, 0f)).as("behind, outside the circle").isFalse();
        assertThat(f.isVisible(7f, 0f)).as("ahead, inside the cone").isTrue();
        assertThat(f.isVisible(8f, 0f)).as("ahead, beyond 120 px").isFalse();
        assertThat(f.isVisible(4f, 4f)).as("45 degrees: outside 38 + 14").isFalse();
        assertThat(f.distance(0)).isCloseTo(settings.coneRange, within(1e-4f));
        assertThat(f.distance(f.rayCount() / 2)).isCloseTo(settings.nearRadius, within(1e-4f));
    }

    @Test
    void theConeSidesNarrowSmoothly() {
        VisionField f = field();
        f.update(0f, 0f, 90f);   // looking up
        float edge = f.rangeAt((float) Math.toRadians(90 + 38 + 7));   // half way through the soft side
        assertThat(edge).isStrictlyBetween(settings.nearRadius, settings.coneRange);
        assertThat(f.rangeAt((float) Math.toRadians(90 + 20))).isEqualTo(settings.coneRange);
        assertThat(f.rangeAt((float) Math.toRadians(270))).isEqualTo(settings.nearRadius);
    }

    @Test
    void wallsCastShadowsButLowPropsDoNot() {
        box(3f, -2f, 1f, 4f, WALLS);   // a wall 3 units ahead
        box(1.5f, 1.5f, 0.5f, 0.5f, PROPS);
        VisionField f = field();
        f.update(0f, 0f, 0f);
        assertThat(f.isVisible(2.5f, 0f)).as("in front of the wall").isTrue();
        assertThat(f.isVisible(6f, 0f)).as("behind the wall").isFalse();
        assertThat(f.isVisible(3f + 0.2f, 0f)).as("the wall's face, through the peek").isFalse();
        assertThat(f.distance(0)).as("the ray stops at the wall plus the peek")
                .isCloseTo(3f + settings.wallPeek, within(1e-3f));
        assertThat(f.isVisible(4f, 4f)).as("an unrelated direction").isFalse();
        f.update(0f, 0f, 180f);
        assertThat(f.isVisible(2.5f, 2.5f)).as("looking away: outside the 2.125 near circle").isFalse();
        f.update(0f, 0f, 45f);
        assertThat(f.isVisible(2.5f, 2.5f)).as("behind a low prop, inside the cone").isTrue();
    }

    @Test
    void solidTileGridsOnTheSightLayerBlockToo() {
        TileGrid grid = new TileGrid(10, 10, 1f, 1f);
        grid.setSolid(5, 0, true);
        collision.addTileGrid(grid, WALLS);
        VisionField f = field();
        f.update(1.5f, 0.5f, 0f);
        assertThat(f.isVisible(4.5f, 0.5f)).isTrue();
        assertThat(f.isVisible(7.5f, 0.5f)).isFalse();
    }

    @Test
    void visibilityFadesAtTheEdge() {
        VisionField f = field();
        f.update(0f, 0f, 0f);
        assertThat(f.visibility(1f, 0f)).isEqualTo(1f);
        float edge = f.visibility(settings.coneRange - settings.edgeSoftness / 2f, 0f);
        assertThat(edge).isCloseTo(0.5f, within(0.05f));
        assertThat(f.visibility(settings.coneRange + 0.1f, 0f)).isZero();
        f.enabled = false;
        assertThat(f.isVisible(100f, 100f)).isTrue();
        assertThat(f.visibility(100f, 100f)).isEqualTo(1f);
    }

    @Test
    void theSystemHidesThingsInTheDarkAndReportsChanges() {
        VisionField f = new VisionField(collision, SIGHT, settings);
        world.addSystem(new VisionSystem(world, f, events));
        int viewer = world.create();
        world.add(viewer, new Transform()).teleport(0f, 0f);
        world.add(viewer, new Viewer()).angle = 0f;
        int rat = world.create();
        Transform ratAt = world.add(rat, new Transform());
        ratAt.teleport(6f, -0.5f);   // in the cone (its centre is 0.5 up)
        Sprite sprite = world.add(rat, new Sprite());
        world.add(rat, new HiddenInFog());
        frame();
        assertThat(sprite.visible).isTrue();
        assertThat(seen).as("the first state sends nothing").isEmpty();

        ratAt.teleport(-6f, -0.5f);   // behind the viewer
        frame();
        assertThat(sprite.visible).isFalse();
        assertThat(seen).containsExactly(new WentOutOfView(rat));

        world.get(viewer, Viewer.class).angle = 180f;   // turn around
        frame();
        assertThat(sprite.visible).isTrue();
        assertThat(seen).containsExactly(new WentOutOfView(rat), new CameIntoView(rat));
    }

    @Test
    void withoutAViewerNothingIsHidden() {
        VisionField f = new VisionField(collision, SIGHT, settings);
        world.addSystem(new VisionSystem(world, f, events));
        int coin = world.create();
        world.add(coin, new Transform()).teleport(50f, 50f);
        Sprite sprite = world.add(coin, new Sprite());
        world.add(coin, new HiddenInFog());
        frame();
        assertThat(sprite.visible).isTrue();
    }

    @Test
    void settingsAreChecked() {
        settings.coneRange = 1f;
        assertThatThrownBy(() -> new VisionField(collision, SIGHT, settings))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nearRadius");
    }

    @Test
    void theKitRegistersItsComponents() {
        ComponentRegistry registry = new ComponentRegistry();
        VisionKit.register(registry);
        assertThat(registry.names()).contains("Viewer", "HiddenInFog");
    }

    void frame() {
        world.runPhase(Phase.FIXED, time);
        world.runPhase(Phase.LATE, time);
        events.flush();
    }
}
