package dev.ember2d.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.internal.GdxRenderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpriteRenderSystemTest {

    private static Texture texture;

    private final RenderTestKit kit = new RenderTestKit();
    private final World world = new World(false);
    private GdxRenderer renderer;
    private final List<String> warnings = new ArrayList<>();

    @BeforeAll
    static void gdx() {
        HeadlessGdx.install();
        texture = new Texture(new Pixmap(64, 32, Pixmap.Format.RGBA8888));
    }

    @AfterAll
    static void clear() {
        HeadlessGdx.clear();
    }

    @BeforeEach
    void setUp() {
        renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
        world.addSystem(new SpriteRenderSystem(world, renderer, null, kit.time));
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink((level, category, message, error) -> {
            if (level == LogLevel.WARN) {
                warnings.add(message);
            }
        });
    }

    @AfterEach
    void resetLog() {
        LogConfig.resetToDefaults();
    }

    /** A sprite whose region starts at srcX, so draw calls identify the sprite by their source x. */
    private int sprite(int srcX, float x, float y, float z, String layer) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        Sprite sprite = world.add(e, new Sprite());
        sprite.region = new TextureRegion(texture, srcX, 0, 16, 16);
        sprite.z = z;
        sprite.layer = layer;
        return e;
    }

    /** Source x of each drawn sprite, in draw order. */
    private List<Integer> drawOrder() {
        kit.calls.clear();
        world.runPhase(Phase.RENDER, kit.time);
        return kit.calls("draw").stream().map(c -> (Integer) c.args()[10]).toList();
    }

    @Test
    void zLayerDrawsByZWithStableTies() {
        sprite(0, 0, 0, 2f, "world");
        sprite(16, 0, 0, 1f, "world");
        sprite(32, 0, 0, 2f, "world");

        assertThat(drawOrder()).containsExactly(16, 0, 32);
    }

    @Test
    void layersDrawInListOrderAndYSortPutsHigherYBehind() {
        renderer.setLayers(new RenderLayer("ground", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT));
        sprite(0, 0, 5, 0, "world");
        sprite(16, 0, 9, 0, "world");
        sprite(32, 0, 1, 0, "ground");
        sprite(48, 0, 2, 0, "world");

        assertThat(drawOrder()).containsExactly(32, 16, 0, 48);
    }

    @Test
    void unknownLayerDrawsOnWorldAndWarnsOnce() {
        sprite(0, 0, 0, 0, "sky");
        sprite(16, 0, 0, 0, "sky");

        assertThat(drawOrder()).containsExactly(0, 16);
        assertThat(warnings).singleElement().asString().contains("Unknown render layer \"sky\"");
    }

    @Test
    void drawUsesInterpolatedSnappedPositionSizeOriginTintAndFlip() {
        int e = sprite(16, 0, 0, 0, "world");
        Transform t = world.get(e, Transform.class);
        t.previous.set(0f, 0f);
        t.position.set(1f, 2f);
        t.scale.set(2f, 1f);
        Sprite s = world.get(e, Sprite.class);
        s.origin.set(8f, 0f);
        s.tint.set(1f, 0f, 0f, 0.5f);
        s.flipX = true;
        new dev.ember2d.core.internal.GameLoop(() -> 8_333_334L, kit.time, phase -> { }, 5).frame();   // alpha 0.5

        drawOrder();

        RenderTestKit.Call draw = kit.calls("draw").get(0);
        // position 0.5,1 (alpha .5) minus origin 0.5,0 -> 0,1 (already whole virtual pixels)
        assertThat(draw.f(1)).isEqualTo(0f);
        assertThat(draw.f(2)).isEqualTo(1f);
        assertThat(draw.f(3)).isEqualTo(0.5f);                // origin x in world units
        assertThat(draw.f(5)).isEqualTo(1f);                  // 16 px / 16 ppu
        assertThat(draw.f(7)).isEqualTo(2f);                  // scale x
        assertThat(draw.args()[14]).isEqualTo(true);          // flipX
        assertThat(kit.calls("setColor")).extracting(c -> (Color) c.args()[0]).contains(new Color(1f, 0f, 0f, 0.5f));
    }

    @Test
    void positionsAreSnappedToWholeVirtualPixels() {
        sprite(0, 1.03f, 0.47f, 0, "world");

        drawOrder();

        RenderTestKit.Call draw = kit.calls("draw").get(0);
        assertThat(draw.f(1)).isEqualTo(16f / 16f);   // 1.03 * 16 = 16.48 -> 16
        assertThat(draw.f(2)).isEqualTo(8f / 16f);    // 0.47 * 16 = 7.52 -> 8
    }

    @Test
    void spriteWithoutRegionIsSkippedWithOneWarning() {
        int e = world.create();
        world.add(e, Transform.class);
        world.add(e, new Sprite());

        assertThat(drawOrder()).isEmpty();
        drawOrder();
        assertThat(warnings).singleElement().asString().contains("neither region nor regionId");
    }
}
