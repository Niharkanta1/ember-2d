package dev.ember2d.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.render.internal.GdxRenderer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GdxRendererTest {

    private final RenderTestKit kit = new RenderTestKit();

    @BeforeAll
    static void gdx() {
        HeadlessGdx.install();
    }

    @AfterAll
    static void clear() {
        HeadlessGdx.clear();
    }

    @Test
    void pixelPerfectFrameDrawsTheWorldImageScaledIntoTheLetterbox() {
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
        renderer.resize(1366, 768);
        kit.target.events.clear();
        kit.calls.clear();

        renderer.beginFrame();
        renderer.endFrame();

        assertThat(kit.target.events).containsExactly("begin", "end");
        assertThat(kit.calls).extracting(RenderTestKit.Call::name)
                .containsExactly("setColor", "setProjectionMatrix", "begin", "end",
                        "setColor", "setProjectionMatrix", "begin", "draw", "end");
        RenderTestKit.Call blit = kit.calls("draw").get(0);
        assertThat(blit.args()[0]).isSameAs(kit.target.region);
        assertThat(blit.args()).containsSubsequence(43f, 24f, 1280f, 720f);
        assertThat(renderer.scale()).isEqualTo(4);
    }

    @Test
    void resizeSetsTargetSizeAndVisibleWorld() {
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT_FILL);

        renderer.resize(2400, 1080);

        assertThat(kit.target.width).isEqualTo(400);
        assertThat(kit.target.height).isEqualTo(180);
        assertThat(renderer.camera().viewSize).isEqualTo(new Vector2(400 / 16f, 180 / 16f));
        assertThat(renderer.viewport().getWorldWidth()).isEqualTo(25f);
        assertThat(renderer.viewport().getScreenWidth()).isEqualTo(2400);
    }

    @Test
    void cameraIsSnappedToWholeVirtualPixelsInPixelPerfectModes() {
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
        renderer.camera().position.set(10.03f, 5.47f);

        renderer.beginFrame();
        renderer.endFrame();

        assertThat(renderer.worldCamera().position.x).isEqualTo(160f / 16f);   // 10.03 * 16 = 160.48 -> 160
        assertThat(renderer.worldCamera().position.y).isEqualTo(88f / 16f);    // 5.47 * 16 = 87.52 -> 88
    }

    @Test
    void smoothModeDoesNotSnapAndUsesNoRenderTarget() {
        GdxRenderer renderer = kit.renderer(RenderMode.SMOOTH);
        renderer.camera().position.set(10.03f, 5.47f);
        kit.target.events.clear();

        renderer.beginFrame();
        renderer.endFrame();

        assertThat(renderer.worldCamera().position.x).isEqualTo(10.03f);
        assertThat(kit.target.events).isEmpty();
        assertThat(kit.calls("draw")).isEmpty();
    }

    @Test
    void zoomAndShakeAreApplied() {
        GdxRenderer renderer = kit.renderer(RenderMode.SMOOTH);
        renderer.camera().zoom = 2f;
        renderer.camera().maxShakeOffset = 1f;
        renderer.camera().addTrauma(1f);
        kit.time.setPaused(true);   // delta 0: trauma stays at 1 for this frame

        renderer.beginFrame();
        renderer.endFrame();

        assertThat(renderer.worldCamera().zoom).isEqualTo(2f);
        Vector2 offset = renderer.camera().shakeOffset();
        assertThat(renderer.worldCamera().position.x).isEqualTo(offset.x);
        assertThat(Math.abs(offset.x)).isLessThanOrEqualTo(1f);
        assertThat(offset.len()).isPositive();
    }

    @Test
    void screenToWorldAccountsForBarsCameraAndYFlip() {
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
        renderer.resize(1366, 768);
        renderer.camera().position.set(10f, 5f);
        renderer.beginFrame();
        renderer.endFrame();

        Vector2 centre = renderer.screenToWorld(683f, 384f, new Vector2());
        Vector2 imageTopLeft = renderer.screenToWorld(43f, 24f, new Vector2());

        assertThat(centre.x).isCloseTo(10f, within(1e-4f));
        assertThat(centre.y).isCloseTo(5f, within(1e-4f));
        assertThat(imageTopLeft.x).isCloseTo(10f - 10f, within(1e-4f));        // view is 20 x 11.25 units
        assertThat(imageTopLeft.y).isCloseTo(5f + 5.625f, within(1e-4f));
    }

    @Test
    void layersAreValidatedAndReplaced() {
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
        assertThat(renderer.layers()).containsExactly(new RenderLayer("world", SortMode.Z));

        renderer.setLayers(new RenderLayer("background", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT));

        assertThat(renderer.layers()).extracting(RenderLayer::name).containsExactly("background", "world");
        assertThatThrownBy(renderer::setLayers).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renderer.setLayers(new RenderLayer("a", SortMode.Z), new RenderLayer("a", SortMode.NONE)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("listed twice");
    }

    @Test
    void disposeReleasesBatchAndTarget() {
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);

        renderer.dispose();

        assertThat(kit.calls("dispose")).hasSize(1);
        assertThat(kit.target.events).contains("dispose");
    }
}
