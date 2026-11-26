package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputCapture;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Touch gestures (F-022) through a real {@link GameLoop}. Default thresholds, density 1: slop 10 px, tap 0.3 s,
 * double tap 0.3 s, long press 0.5 s, swipe 48 px within 0.3 s. Fixed step 1/16 s.
 */
class TouchInputTest {

    static final String BINDINGS = """
            { "version": 1, "contexts": { "gameplay": { "actions": {
                "touch": ["TOUCH"], "tap": ["TAP"], "dtap": ["DOUBLE_TAP"], "long": ["LONG_PRESS"], "drag": ["DRAG"],
                "left": ["SWIPE_LEFT"], "right": ["SWIPE_RIGHT"], "up": ["SWIPE_UP"], "down": ["SWIPE_DOWN"],
                "click": ["MOUSE_LEFT"] } } } }
            """;
    static final String[] GESTURES = {"tap", "dtap", "long", "drag", "left", "right", "up", "down"};

    final TimeState time = new TimeState(1f / 16f);
    final DefaultInput input = new DefaultInput(time, new DefaultEventBus(false));
    final GdxInputSource src = input.source();
    final List<String> fixedEdges = new ArrayList<>();
    long nextDelta;

    final GameLoop loop = new GameLoop(() -> nextDelta, time, phase -> {
        if (phase == Phase.INPUT) {
            input.beginFrame();
        } else if (phase == Phase.FIXED) {
            input.beginFixedStep();
            if (input.justPressed("tap")) {
                fixedEdges.add("tap");
            }
            input.endFixedStep();
        }
    }, 5);

    TouchInputTest() {
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "touch.json"));
    }

    /** Runs one frame of {@code seconds} (real and game time). */
    void frame(double seconds) {
        nextDelta = Math.round(seconds * 1e9);
        loop.frame();
    }

    /** The gestures that went down this frame, plus "+name" for held ones. */
    List<String> edges() {
        List<String> out = new ArrayList<>();
        for (String g : GESTURES) {
            if (input.justPressed(g)) {
                out.add(g);
            }
            if (input.pressed(g)) {
                out.add("+" + g);
            }
        }
        return out;
    }

    @Test
    void shortStillTouchIsATap() {
        src.touchDown(100, 100, 0, Buttons.LEFT);
        frame(0.05);
        assertThat(input.pressed("touch")).isTrue();
        assertThat(input.pointerJustDown(0)).isTrue();
        assertThat(edges()).isEmpty();

        src.touchDragged(106, 104, 0);   // within the 10 px slop
        src.touchUp(106, 104, 0, Buttons.LEFT);
        frame(0.1);

        assertThat(edges()).containsExactly("tap");
        assertThat(input.justReleased("tap")).as("momentary").isTrue();
        assertThat(input.justReleased("touch")).isTrue();
        assertThat(input.pointerJustUp(0)).isTrue();
        assertThat(input.pointerDown(0)).isFalse();
        assertThat(input.pointerScreen(0, new Vector2())).isEqualTo(new Vector2(106f, 104f));
    }

    @Test
    void aTapInsideOneFrameIsSeenByTheNextFixedStep() {
        src.touchDown(50, 50, 0, Buttons.LEFT);
        src.touchUp(50, 50, 0, Buttons.LEFT);
        frame(0.01);   // no fixed step yet
        assertThat(edges()).containsExactly("tap");
        assertThat(input.pointerJustDown(0)).isTrue();
        assertThat(input.pointerJustUp(0)).isTrue();
        assertThat(fixedEdges).isEmpty();

        frame(0.06);
        assertThat(fixedEdges).containsExactly("tap");
    }

    @Test
    void slowTouchIsNeitherTapNorLongPress() {
        src.touchDown(0, 0, 0, Buttons.LEFT);
        frame(0.05);
        frame(0.2);
        frame(0.15);   // 0.4 s held (the loop caps a frame at 0.25 s)
        src.touchUp(0, 0, 0, Buttons.LEFT);
        frame(0.01);

        assertThat(edges()).isEmpty();
        assertThat(input.justReleased("touch")).isTrue();
    }

    @Test
    void stillTouchBecomesALongPressAndIsNeverATap() {
        src.touchDown(0, 0, 0, Buttons.LEFT);
        frame(0.05);
        frame(0.25);
        assertThat(edges()).isEmpty();
        frame(0.25);   // 0.5 s after the touch
        assertThat(edges()).containsExactly("long", "+long");
        frame(0.1);
        assertThat(input.heldTime("long")).isGreaterThan(0f);

        src.touchUp(0, 0, 0, Buttons.LEFT);
        frame(0.01);
        assertThat(edges()).isEmpty();
        assertThat(input.justReleased("long")).isTrue();
    }

    @Test
    void movingTouchDragsWithADeltaAndIsNoTap() {
        Vector2 d = new Vector2();
        src.touchDown(100, 100, 0, Buttons.LEFT);
        frame(0.05);
        src.touchDragged(120, 90, 0);
        frame(0.05);

        assertThat(edges()).containsExactly("drag", "+drag");
        assertThat(input.dragDelta(d)).as("window pixels without a renderer").isEqualTo(new Vector2(20f, -10f));
        frame(0.05);
        assertThat(input.dragDelta(d)).as("no movement this frame").isEqualTo(Vector2.Zero);

        input.setScreenToWorld((x, y, out) -> out.set(x / 10f, -y / 10f));
        src.touchDragged(130, 90, 0);
        frame(0.5);
        assertThat(input.dragDelta(d)).isEqualTo(new Vector2(1f, 0f));
        assertThat(input.pressed("long")).as("a moved finger never long-presses").isFalse();

        src.touchUp(130, 90, 0, Buttons.LEFT);
        frame(0.01);
        assertThat(edges()).as("slow: no swipe, no tap").isEmpty();
        assertThat(input.justReleased("drag")).isTrue();
        assertThat(input.dragDelta(d)).isEqualTo(Vector2.Zero);
    }

    @Test
    void fastMovesAreSwipesInTheDominantDirection() {
        // every swipe is also a drag that started and ended in this frame
        swipe(100, 100, 200, 120);
        assertThat(edges()).containsExactly("drag", "right");
        swipe(300, 100, 240, 90);
        assertThat(edges()).containsExactly("drag", "left");
        swipe(100, 300, 110, 200);
        assertThat(edges()).as("screen y down: moving up the screen").containsExactly("drag", "up");
        swipe(100, 100, 100, 160);
        assertThat(edges()).containsExactly("drag", "down");
        swipe(100, 100, 130, 100);
        assertThat(edges()).as("30 px: a drag, too short for a swipe").containsExactly("drag");
    }

    private void swipe(int x0, int y0, int x1, int y1) {
        src.touchDown(x0, y0, 0, Buttons.LEFT);
        frame(0.05);
        src.touchDragged(x1, y1, 0);
        src.touchUp(x1, y1, 0, Buttons.LEFT);
        frame(0.1);
    }

    @Test
    void twoNearbyTapsMakeADoubleTapAndTheFirstTapIsNotDelayed() {
        tap(100, 100, 0.05);
        assertThat(edges()).containsExactly("tap");
        frame(0.1);
        tap(104, 98, 0.05);
        assertThat(edges()).containsExactly("tap", "dtap");
        frame(0.05);
        tap(104, 98, 0.05);
        assertThat(edges()).as("a third tap starts a new pair").containsExactly("tap");
    }

    @Test
    void tapsTooFarApartOrTooLateAreSingleTaps() {
        tap(100, 100, 0.05);
        tap(140, 100, 0.05);
        assertThat(edges()).containsExactly("tap");
        frame(0.25);
        frame(0.1);
        tap(140, 100, 0.05);
        assertThat(edges()).containsExactly("tap");
    }

    private void tap(int x, int y, double frameSeconds) {
        src.touchDown(x, y, 0, Buttons.LEFT);
        src.touchUp(x, y, 0, Buttons.LEFT);
        frame(frameSeconds);
    }

    @Test
    void fingersAreRecognisedIndependently() {
        src.touchDown(10, 10, 0, Buttons.LEFT);
        frame(0.05);
        src.touchDown(200, 200, 1, Buttons.LEFT);
        src.touchUp(200, 200, 1, Buttons.LEFT);
        frame(0.05);

        assertThat(edges()).as("second finger taps while the first holds").containsExactly("tap");
        assertThat(input.pressed("touch")).isTrue();
        assertThat(input.pointerDown(0)).isTrue();
        assertThat(input.pointerDown(1)).isFalse();
        assertThat(input.pointerScreen(1, new Vector2())).isEqualTo(new Vector2(200f, 200f));

        frame(0.2);
        frame(0.25);
        assertThat(edges()).as("first finger long-presses").containsExactly("long", "+long");
        src.touchUp(10, 10, 0, Buttons.LEFT);
        frame(0.01);
        assertThat(input.pressed("touch")).isFalse();
        assertThat(input.pressed("long")).isFalse();
    }

    @Test
    void pointersBeyondFiveAndOtherMouseButtonsAreIgnored() {
        src.touchDown(1, 1, 5, Buttons.LEFT);
        src.touchDown(1, 1, 0, Buttons.RIGHT);
        frame(0.05);

        assertThat(input.pressed("touch")).isFalse();
        assertThat(input.maxPointers()).isEqualTo(5);
        assertThatThrownBy(() -> input.pointerDown(5)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0..4");
        assertThatThrownBy(() -> input.pointerWorld(-1, new Vector2())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withoutMouseButtonsFingersDoNotDriveMouseBindings() {
        src.touchDown(1, 1, 0, Buttons.LEFT);
        frame(0.05);
        assertThat(input.pressed("click")).as("desktop: mouse button").isTrue();
        src.touchUp(1, 1, 0, Buttons.LEFT);
        frame(0.05);

        src.setMouseButtons(false);   // Android
        src.touchDown(1, 1, 0, Buttons.LEFT);
        frame(0.05);
        assertThat(input.pressed("click")).isFalse();
        assertThat(input.pressed("touch")).isTrue();
    }

    @Test
    void capturedPointerIgnoresNewTouchesUntilTheyLift() {
        boolean[] captured = {true};
        input.setCapture(new InputCapture() {
            @Override
            public boolean capturesKeyboard() {
                return false;
            }

            @Override
            public boolean capturesPointer() {
                return captured[0];
            }
        });
        src.touchDown(1, 1, 0, Buttons.LEFT);
        frame(0.05);
        captured[0] = false;
        src.touchUp(1, 1, 0, Buttons.LEFT);
        frame(0.05);

        assertThat(edges()).isEmpty();
        assertThat(input.justReleased("touch")).isFalse();
        tap(1, 1, 0.05);
        assertThat(edges()).containsExactly("tap");
    }

    @Test
    void releaseAllEndsTouchesWithoutGestures() {
        src.touchDown(100, 100, 0, Buttons.LEFT);
        frame(0.05);
        src.touchDragged(150, 100, 0);
        frame(0.05);
        assertThat(input.pressed("drag")).isTrue();

        input.releaseAll();
        frame(0.05);
        assertThat(input.pressed("drag")).isFalse();
        assertThat(input.pressed("touch")).isFalse();
        assertThat(input.pointerDown(0)).isFalse();

        src.touchUp(160, 100, 0, Buttons.LEFT);   // the lost lift arrives late
        frame(0.05);
        assertThat(edges()).isEmpty();
        tap(5, 5, 0.05);
        assertThat(edges()).containsExactly("tap");
    }

    @Test
    void thresholdsScaleWithDensity() {
        input.setDensity(2f);   // slop 20 px
        src.touchDown(100, 100, 0, Buttons.LEFT);
        src.touchDragged(115, 100, 0);
        src.touchUp(115, 100, 0, Buttons.LEFT);
        frame(0.05);

        assertThat(edges()).containsExactly("tap");
    }
}
