package dev.ember2d.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.internal.GdxRenderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** World text (F-004, I-140) with libGDX's built-in 15 px font, through the recording batch. 16 px per unit. */
class TextRendererTest {

    private static BitmapFont font;
    private static Texture texture;

    private final RenderTestKit kit = new RenderTestKit();
    private final World world = new World(false);
    private final GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
    private final SpriteRenderSystem sprites = new SpriteRenderSystem(world, renderer, null, kit.time);

    @BeforeAll
    static void gdx() {
        HeadlessGdx.install();
        font = new BitmapFont();
        texture = new Texture(new Pixmap(16, 16, Pixmap.Format.RGBA8888));
    }

    @AfterAll
    static void clear() {
        HeadlessGdx.clear();
    }

    TextRendererTest() {
        world.addSystem(sprites);
        new TextRenderer(world, renderer, assets(), kit.time).attachTo(sprites);
    }

    int text(String s, float x, float y, TextAlign align) {
        int e = world.create();
        world.add(e, Transform.class).teleport(x, y);
        Text t = world.add(e, new Text());
        t.font = "font";
        t.text = s;
        t.align = align;
        return e;
    }

    /** The x and y of every glyph vertex of the single text drawn this frame. */
    float[][] render() {
        kit.calls.clear();
        world.runPhase(Phase.RENDER, kit.time);
        RenderTestKit.Call draw = kit.calls("draw").stream().filter(c -> c.args().length == 4).findFirst().orElseThrow();
        float[] vertices = (float[]) draw.args()[1];
        int count = (Integer) draw.args()[3];
        float[][] xy = new float[count / 5][2];
        for (int i = 0; i < count / 5; i++) {
            xy[i][0] = vertices[i * 5];
            xy[i][1] = vertices[i * 5 + 1];
        }
        return xy;
    }

    static float minX(float[][] xy) {
        float m = Float.MAX_VALUE;
        for (float[] p : xy) {
            m = Math.min(m, p[0]);
        }
        return m;
    }

    static float maxX(float[][] xy) {
        float m = -Float.MAX_VALUE;
        for (float[] p : xy) {
            m = Math.max(m, p[0]);
        }
        return m;
    }

    static float minY(float[][] xy) {
        float m = Float.MAX_VALUE;
        for (float[] p : xy) {
            m = Math.min(m, p[1]);
        }
        return m;
    }

    @Test
    void leftAlignedTextSitsOnItsBaselineInWorldUnitsOnWholePixels() {
        text("HELLO", 2.03f, 3.01f, TextAlign.LEFT);
        float[][] xy = render();

        assertThat(minX(xy)).as("starts at the anchor").isCloseTo(2f, within(2f / 16f));
        assertThat(minY(xy)).as("capitals stand on the baseline").isCloseTo(3f, within(1.5f / 16f));
        assertThat(maxX(xy) - minX(xy)).as("15 px font: about 2.5 units wide").isBetween(2f, 4f);
        for (float[] p : xy) {
            assertThat(p[0] * 16f).as("x on whole virtual pixels").isCloseTo(Math.round(p[0] * 16f), within(1e-3f));
            assertThat(p[1] * 16f).as("y on whole virtual pixels").isCloseTo(Math.round(p[1] * 16f), within(1e-3f));
        }
    }

    @Test
    void centreAndRightAlignment() {
        int e = text("HELLO", 10f, 3f, TextAlign.CENTER);
        float[][] centred = render();
        assertThat((minX(centred) + maxX(centred)) / 2f).isCloseTo(10f, within(2f / 16f));

        world.get(e, Text.class).align = TextAlign.RIGHT;
        float[][] right = render();
        assertThat(maxX(right)).isCloseTo(10f, within(2f / 16f));
    }

    @Test
    void offsetScaleAndWrapping() {
        int e = text("HELLO", 2f, 3f, TextAlign.LEFT);
        Text t = world.get(e, Text.class);
        t.offset.set(1f, 2f);
        float[][] moved = render();
        assertThat(minX(moved)).isCloseTo(3f, within(2f / 16f));
        assertThat(minY(moved)).isCloseTo(5f, within(1.5f / 16f));

        t.offset.setZero();
        float narrow = maxX(render()) - minX(render());
        t.scale = 2f;
        float wide = maxX(render()) - minX(render());
        assertThat(wide).isCloseTo(narrow * 2f, within(3f / 16f));

        t.scale = 1f;
        t.text = "one two three four five";
        t.wrapWidth = 3f;
        render();
        assertThat(t.layout.runs.size).as("wrapped into several lines").isGreaterThan(1);
        assertThat(t.layout.width).isLessThanOrEqualTo(3f + 1e-3f);
    }

    @Test
    void layoutIsReusedUntilSomethingChanges() {
        int e = text("score 10", 0f, 0f, TextAlign.LEFT);
        Text t = world.get(e, Text.class);
        render();
        render();
        render();
        assertThat(t.layoutCount).isEqualTo(1);
        t.text = "score 20";
        render();
        t.color.set(1f, 0f, 0f, 1f);
        render();
        assertThat(t.layoutCount).isEqualTo(3);
    }

    @Test
    void textDrawsAfterTheSpritesOfItsLayerAndBeforeTheNextLayer() {
        renderer.setLayers(new RenderLayer("world", SortMode.Z), new RenderLayer("foreground", SortMode.NONE));
        int sprite = world.create();
        world.add(sprite, Transform.class).teleport(0f, 0f);
        world.add(sprite, new Sprite()).region = new TextureRegion(texture);
        text("A", 0f, 0f, TextAlign.LEFT);
        int top = text("B", 0f, 0f, TextAlign.LEFT);
        world.get(top, Text.class).layer = "foreground";
        int foregroundSprite = world.create();
        world.add(foregroundSprite, Transform.class).teleport(0f, 0f);
        Sprite fs = world.add(foregroundSprite, new Sprite());
        fs.region = new TextureRegion(texture);
        fs.layer = "foreground";

        kit.calls.clear();
        world.runPhase(Phase.RENDER, kit.time);
        List<String> order = new ArrayList<>();
        for (RenderTestKit.Call c : kit.calls("draw")) {
            order.add(c.args().length == 4 ? "text" : "sprite");
        }
        assertThat(order).containsExactly("sprite", "text", "sprite", "text");
    }

    private static Assets assets() {
        return new Assets() {
            @Override
            public <T> T get(String id, Class<T> type) {
                return type.cast(font);
            }

            @Override
            public TextureRegion region(String id) {
                return null;
            }

            @Override
            public AsyncLoad loadGroup(String group) {
                return null;
            }

            @Override
            public void unloadGroup(String group) {
            }

            @Override
            public boolean isLoaded(String id) {
                return true;
            }

            @Override
            public boolean contains(String id) {
                return true;
            }
        };
    }
}
