package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputCapture;
import org.junit.jupiter.api.Test;

/**
 * On-screen controls (F-022, I-122) on an 800 x 480 window at density 1. Touch coordinates are libGDX's (y down).
 * Jump button "A": centre (728, 408), radius 40. Pause button "II": centre (744, 56), radius 24. Floating stick:
 * radius 56, dead zone 0.2, left half of the screen.
 */
class TouchControlsTest {

    static final String BINDINGS = """
            { "version": 1,
              "contexts": {
                "gameplay": { "actions": { "jump": ["SPACE"], "pause": ["P"], "tap": ["TAP"], "touch": ["TOUCH"] },
                              "axes": { "move": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"], "down": ["DOWN"] } } },
                "ui": { "actions": { "ui_back": ["ESCAPE"] } } },
              "touchControls": {
                "opacity": 0.4,
                "joystick": { "axis": "move" },
                "buttons": [ { "action": "jump", "label": "A", "x": 72, "y": 72, "radius": 40 },
                             { "action": "pause", "label": "II", "x": 56, "y": -56, "radius": 24 } ] } }
            """;

    final TimeState time = new TimeState(1f / 60f);
    final DefaultInput input = new DefaultInput(time, new DefaultEventBus(false));
    final GdxInputSource src = input.source();
    final Vector2 v = new Vector2();

    TouchControlsTest() {
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "controls.json"));
        input.setScreenSize(800f, 480f);
        input.setTouchControlsVisible(true);
    }

    void frame() {
        input.beginFrame();
    }

    @Test
    void buttonsPressActionsAndNeverBecomeTaps() {
        assertThat(input.touchControlsActive()).isTrue();
        src.touchDown(728, 408, 0, Buttons.LEFT);
        frame();
        assertThat(input.justPressed("jump")).isTrue();
        assertThat(input.pressed("jump")).isTrue();
        assertThat(input.pressed("touch")).as("claimed: no gesture").isFalse();
        assertThat(input.touchControls().pressed(0)).isTrue();

        src.touchUp(728, 408, 0, Buttons.LEFT);
        frame();
        assertThat(input.justReleased("jump")).isTrue();
        assertThat(input.justPressed("tap")).as("a button press is not a TAP").isFalse();

        src.touchDown(744, 56, 1, Buttons.LEFT);
        src.touchUp(744, 56, 1, Buttons.LEFT);
        frame();
        assertThat(input.justPressed("pause")).as("top-right button, quick tap").isTrue();
        assertThat(input.justReleased("pause")).isTrue();
    }

    @Test
    void aButtonPressIsLatchedForTheNextFixedStep() {
        src.touchDown(728, 408, 0, Buttons.LEFT);
        src.touchUp(728, 408, 0, Buttons.LEFT);
        frame();
        input.beginFixedStep();
        assertThat(input.justPressed("jump")).isTrue();
        input.endFixedStep();
        input.beginFixedStep();
        assertThat(input.justPressed("jump")).isFalse();
        input.endFixedStep();
    }

    @Test
    void floatingStickStartsWhereTheThumbLandsWithDeadZoneAndClamp() {
        src.touchDown(200, 300, 0, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).as("at the origin").isEqualTo(Vector2.Zero);
        assertThat(input.touchControls().baseX()).isEqualTo(200f);
        assertThat(input.touchControls().baseY()).as("y up").isEqualTo(180f);

        src.touchDragged(205, 300, 0);   // 5 px: inside the dead zone (0.2 * 56 = 11.2 px)
        frame();
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);

        src.touchDragged(228, 300, 0);   // half way: (0.5 - 0.2) / 0.8
        frame();
        assertThat(input.axis2("move", v).x).isCloseTo(0.375f, within(1e-4f));
        assertThat(v.y).isZero();

        src.touchDragged(200, 100, 0);   // far up (y down on screen): clamped to length 1
        frame();
        assertThat(input.axis2("move", v).y).isCloseTo(1f, within(1e-4f));
        assertThat(input.touchControls().knobY()).as("knob stays on the rim").isCloseTo(180f + 56f, within(1e-3f));
        assertThat(input.pressed("touch")).as("claimed").isFalse();

        src.touchUp(200, 100, 0, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);
        assertThat(input.joystickValue(v)).isEqualTo(Vector2.Zero);
    }

    @Test
    void keysDriveTheAxisWhileTheStickIsIdle() {
        src.keyDown(Keys.LEFT);
        frame();
        assertThat(input.axis2("move", v)).isEqualTo(new Vector2(-1f, 0f));
        src.touchDown(200, 300, 0, Buttons.LEFT);
        src.touchDragged(256, 300, 0);
        frame();
        assertThat(input.axis2("move", v)).as("the held stick wins").isEqualTo(new Vector2(1f, 0f));
    }

    @Test
    void stickAndButtonWorkTogetherWithTwoFingers() {
        src.touchDown(200, 300, 0, Buttons.LEFT);
        src.touchDragged(256, 300, 0);
        src.touchDown(728, 408, 1, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).isEqualTo(new Vector2(1f, 0f));
        assertThat(input.justPressed("jump")).isTrue();

        src.touchUp(728, 408, 1, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).as("stick still held").isEqualTo(new Vector2(1f, 0f));
        assertThat(input.pressed("jump")).isFalse();
    }

    @Test
    void theRightHalfOutsideButtonsIsLeftToGestures() {
        src.touchDown(500, 200, 0, Buttons.LEFT);
        src.touchUp(500, 200, 0, Buttons.LEFT);
        frame();
        assertThat(input.justPressed("tap")).isTrue();
        assertThat(input.touchControls().stickHeld()).isFalse();
    }

    @Test
    void fixedStickOnlyNearItsPosition() {
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS.replace(
                "\"joystick\": { \"axis\": \"move\" }",
                "\"joystick\": { \"axis\": \"move\", \"mode\": \"fixed\", \"x\": 100, \"y\": 100 }")), "c.json"));
        input.setScreenSize(800f, 480f);
        input.setTouchControlsVisible(true);
        src.touchDown(300, 380, 0, Buttons.LEFT);   // left half, but far from (100, 100)
        frame();
        assertThat(input.touchControls().stickHeld()).isFalse();
        src.touchUp(300, 380, 0, Buttons.LEFT);
        src.touchDown(130, 380, 1, Buttons.LEFT);   // 30 px right of the centre (100, 380 y down)
        frame();
        assertThat(input.touchControls().stickHeld()).isTrue();
        assertThat(input.touchControls().baseX()).as("origin is the fixed centre").isEqualTo(100f);
        assertThat(input.axis2("move", v).x).isCloseTo((30f / 56f - 0.2f) / 0.8f, within(1e-4f));
    }

    @Test
    void aHigherContextGetsNoStickValue() {
        src.touchDown(200, 300, 0, Buttons.LEFT);
        src.touchDragged(256, 300, 0);
        frame();
        input.pushContext("ui");
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);
        input.popContext();
        assertThat(input.axis2("move", v)).isEqualTo(new Vector2(1f, 0f));
    }

    @Test
    void hiddenOrCapturedControlsClaimNothing() {
        input.setTouchControlsVisible(false);
        assertThat(input.touchControlsActive()).isFalse();
        src.touchDown(728, 408, 0, Buttons.LEFT);
        src.touchUp(728, 408, 0, Buttons.LEFT);
        frame();
        assertThat(input.justPressed("jump")).isFalse();
        assertThat(input.justPressed("tap")).as("a plain tap when hidden").isTrue();

        input.setTouchControlsVisible(true);
        input.setCapture(new InputCapture() {
            @Override
            public boolean capturesKeyboard() {
                return false;
            }

            @Override
            public boolean capturesPointer() {
                return true;
            }
        });
        src.touchDown(728, 408, 1, Buttons.LEFT);
        frame();
        assertThat(input.pressed("jump")).isFalse();
    }

    @Test
    void releaseAllLetsGoOfTheControls() {
        src.touchDown(728, 408, 0, Buttons.LEFT);
        src.touchDown(200, 300, 1, Buttons.LEFT);
        src.touchDragged(256, 300, 1);
        frame();
        input.releaseAll();
        frame();
        assertThat(input.justReleased("jump")).isTrue();
        assertThat(input.touchControls().stickHeld()).isFalse();
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);
        src.touchUp(728, 408, 0, Buttons.LEFT);   // the late lift is harmless
        frame();
        assertThat(input.justReleased("jump")).isFalse();
    }

    @Test
    void densityScalesSizesAndPositions() {
        input.setDensity(2f);
        assertThat(input.touchControls().buttonX(0)).isEqualTo(800f - 144f);
        assertThat(input.touchControls().buttonRadius(0)).isEqualTo(80f);
        assertThat(input.touchControls().buttonY(1)).isEqualTo(480f - 112f);
    }
}
