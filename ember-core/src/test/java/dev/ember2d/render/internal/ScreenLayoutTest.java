package dev.ember2d.render.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ember2d.render.RenderMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScreenLayoutTest {

    @ParameterizedTest(name = "{0}x{1} -> x{2}, at ({3},{4}) size {5}x{6}")
    @CsvSource({
        "1280, 720, 4, 0, 0, 1280, 720",
        "1920, 1080, 6, 0, 0, 1920, 1080",
        "2560, 1440, 8, 0, 0, 2560, 1440",
        "1366, 768, 4, 43, 24, 1280, 720",
        "800, 600, 2, 80, 120, 640, 360",
        "320, 180, 1, 0, 0, 320, 180"
    })
    void pixelPerfectUsesTheLargestWholeScaleAndCentresIt(int sw, int sh, int scale, int x, int y, int w, int h) {
        ScreenLayout layout = ScreenLayout.compute(sw, sh, 320, 180, RenderMode.PIXEL_PERFECT);

        assertThat(layout).isEqualTo(new ScreenLayout(scale, 320, 180, x, y, w, h));
    }

    @Test
    void fillExtendsTheWorldSidewaysOnWideScreens() {
        ScreenLayout phone = ScreenLayout.compute(2400, 1080, 320, 180, RenderMode.PIXEL_PERFECT_FILL);

        assertThat(phone).isEqualTo(new ScreenLayout(6, 400, 180, 0, 0, 2400, 1080));
    }

    @Test
    void fillOnANarrowScreenLetterboxesVertically() {
        ScreenLayout tablet = ScreenLayout.compute(1024, 768, 320, 180, RenderMode.PIXEL_PERFECT_FILL);

        assertThat(tablet.scale()).isEqualTo(3);
        assertThat(tablet.worldWidth()).isEqualTo(341);
        assertThat(tablet.worldHeight()).isEqualTo(180);
        assertThat(tablet.height()).isEqualTo(540);
        assertThat(tablet.y()).isEqualTo(114);
    }

    @Test
    void windowSmallerThanTheVirtualSizeScalesDown() {
        ScreenLayout small = ScreenLayout.compute(160, 160, 320, 180, RenderMode.PIXEL_PERFECT);

        assertThat(small).isEqualTo(new ScreenLayout(1, 320, 180, 0, 35, 160, 90));
    }

    @Test
    void smoothFitsAtAFractionalScale() {
        ScreenLayout smooth = ScreenLayout.compute(1366, 768, 320, 180, RenderMode.SMOOTH);

        assertThat(smooth).isEqualTo(new ScreenLayout(1, 320, 180, 0, 0, 1365, 768));   // 768/180 = 4.267; 320 * 4.267 = 1365.3
    }

    @Test
    void zeroSizedWindowDoesNotFail() {
        assertThat(ScreenLayout.compute(0, 0, 320, 180, RenderMode.PIXEL_PERFECT).width()).isPositive();
    }
}
