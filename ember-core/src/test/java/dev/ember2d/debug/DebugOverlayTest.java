package dev.ember2d.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebugOverlayTest {

    /** Records drawing as readable strings. */
    private static final class FakeCanvas implements DebugCanvas {
        final List<String> calls = new ArrayList<>();

        @Override
        public void beginLines(Matrix4 projection) {
            calls.add("beginLines");
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, Color color) {
            calls.add("line " + x1 + "," + y1 + " " + x2 + "," + y2 + " " + color);
        }

        @Override
        public void rect(float x, float y, float width, float height, Color color) {
            calls.add("rect " + x + "," + y + " " + width + "x" + height + " " + color);
        }

        @Override
        public void circle(float x, float y, float radius, Color color) {
            calls.add("circle " + x + "," + y + " r" + radius + " " + color);
        }

        @Override
        public void endLines() {
            calls.add("endLines");
        }

        @Override
        public void panel(float x, float y, float width, float height, Color color, Matrix4 projection) {
            calls.add("panel " + x + "," + y + " " + width + "x" + height);
        }

        @Override
        public void beginText(Matrix4 projection) {
            calls.add("beginText");
        }

        @Override
        public void text(CharSequence text, float x, float y, Color color) {
            calls.add("text " + x + "," + y + " " + text);
        }

        @Override
        public void endText() {
            calls.add("endText");
        }

        @Override
        public float lineHeight() {
            return 20f;
        }
    }

    private final FakeCanvas canvas = new FakeCanvas();
    private final Matrix4 world = new Matrix4();

    @Test
    void availabilityAndToggle() {
        DebugOverlay dev = new DebugOverlay(true, false);
        assertThat(dev.isVisible()).isFalse();
        dev.toggle();
        assertThat(dev.isVisible()).isTrue();
        dev.toggle();
        assertThat(dev.isVisible()).isFalse();

        DebugOverlay release = new DebugOverlay(false, true);
        assertThat(release.isAvailable()).isFalse();
        assertThat(release.isVisible()).as("visibleAtStart ignored when unavailable").isFalse();
        release.toggle();
        release.setVisible(true);
        assertThat(release.isVisible()).isFalse();

        assertThat(new DebugOverlay(true, true).isVisible()).isTrue();
    }

    @Test
    void shapesAreDrawnInOrderThenForgotten() {
        DebugOverlay overlay = new DebugOverlay(true, true);
        overlay.rect(1, 2, 3, 4, Color.GREEN);
        overlay.line(0, 0, 5, 5, Color.RED);
        overlay.circle(2, 2, 1.5f, Color.BLUE);

        overlay.renderWorld(canvas, world);
        overlay.renderWorld(canvas, world);

        assertThat(canvas.calls).containsExactly(
                "beginLines",
                "rect 1.0,2.0 3.0x4.0 " + Color.GREEN,
                "line 0.0,0.0 5.0,5.0 " + Color.RED,
                "circle 2.0,2.0 r1.5 " + Color.BLUE,
                "endLines");
    }

    @Test
    void hiddenOverlayIgnoresShapesMetricsAndDrawing() {
        DebugOverlay overlay = new DebugOverlay(true, false);
        overlay.rect(1, 2, 3, 4, Color.GREEN);
        for (int i = 0; i < 120; i++) {
            overlay.recordFrame(1f / 60f, 1_000_000L, i, 0, 5, 2);
        }

        overlay.renderWorld(canvas, world);
        overlay.renderPanel(canvas, 1280, 720);

        assertThat(canvas.calls).isEmpty();
        assertThat(overlay.panelLines()[0]).isEqualTo("FPS 0   frame 0.00 ms (max 0.00)");
    }

    @Test
    void shapesQueuedBeforeHidingAreDropped() {
        DebugOverlay overlay = new DebugOverlay(true, true);
        overlay.rect(1, 2, 3, 4, Color.GREEN);
        overlay.setVisible(false);
        overlay.setVisible(true);

        overlay.renderWorld(canvas, world);

        assertThat(canvas.calls).isEmpty();
    }

    @Test
    void metricsArePublishedForEachOneSecondWindow() {
        DebugOverlay overlay = new DebugOverlay(true, true);
        long tick = 0;
        for (int i = 0; i < 60; i++) {
            float frame = i == 30 ? 0.05f : (1f - 0.05f) / 59f;   // one 50 ms hitch in a one-second window
            tick += i == 30 ? 3 : 1;
            overlay.recordFrame(frame, i == 30 ? 4_000_000L : 1_000_000L, tick, 2, 18, 3);
        }

        String[] lines = overlay.panelLines();

        assertThat(lines[0]).isEqualTo("FPS 60   frame 16.67 ms (max 50.00)");
        assertThat(lines[1]).isEqualTo("engine 1.05 ms (max 4.00)");
        assertThat(lines[2]).isEqualTo("fixed steps 1   dropped 2");
        assertThat(lines[3]).isEqualTo("entities 18   render calls 3");
    }

    @Test
    void stepsLastFrameComeFromTheFixedTickDifference() {
        DebugOverlay overlay = new DebugOverlay(true, true);
        overlay.recordFrame(0.01f, 0, 10, 0, 0, 0);
        assertThat(overlay.panelLines()[2]).startsWith("fixed steps 0");   // first frame: no previous tick

        overlay.recordFrame(0.01f, 0, 13, 0, 0, 0);

        assertThat(overlay.panelLines()[2]).startsWith("fixed steps 3");
    }

    @Test
    void panelIsDrawnTopLeftWithFourLines() {
        DebugOverlay overlay = new DebugOverlay(true, true);

        overlay.renderPanel(canvas, 1280, 720);

        assertThat(canvas.calls).hasSize(7);
        assertThat(canvas.calls.get(0)).isEqualTo("panel 0.0,628.0 300.0x92.0");   // 4 lines x 20 + 2 x 6 padding
        assertThat(canvas.calls.get(1)).isEqualTo("beginText");
        assertThat(canvas.calls.get(2)).startsWith("text 6.0,714.0 FPS");
        assertThat(canvas.calls.get(5)).startsWith("text 6.0,654.0 entities");
        assertThat(canvas.calls.get(6)).isEqualTo("endText");
    }
}
