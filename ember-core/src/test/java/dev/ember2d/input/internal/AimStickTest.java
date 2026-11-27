package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import org.junit.jupiter.api.Test;

/**
 * The twin-stick aim stick (F-022 extension, I-320) on an 800 x 480 window at density 1. Touch coordinates are y down.
 * Button "B" (bomb) centre (800 - 72, 480 - 72) = (728, 408), radius 40.
 */
class AimStickTest {

    static String bindings(String aim) {
        return """
                { "version": 1,
                  "contexts": {
                    "gameplay": { "actions": { "fire": ["SPACE"], "bomb": ["B"] },
                                  "axes": { "move": { "left": ["A"], "right": ["D"], "up": ["W"], "down": ["S"] },
                                            "aim": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"],
                                                     "down": ["DOWN"] } } },
                    "ui": { "actions": { "ui_back": ["ESCAPE"] } } },
                  "touchControls": {
                    "joystick": { "axis": "move", "mode": "floating" },
                    "buttons": [ { "action": "bomb", "label": "B", "x": 72, "y": 72, "radius": 40 } ],
                    %s } }
                """.formatted(aim);
    }

    final DefaultInput input = new DefaultInput(new TimeState(1f / 60f), new DefaultEventBus(false));
    final GdxInputSource src = input.source();
    final Vector2 v = new Vector2();

    AimStickTest() {
        input.setBindings(InputBindings.parse(new JsonReader().parse(bindings(
                "\"aimStick\": { \"axis\": \"aim\", \"radius\": 50, \"deadZone\": 0.2, \"fire\": \"fire\", "
                        + "\"fireAt\": 0.5 }")), "controls.json"));
        input.setScreenSize(800f, 480f);
        input.setTouchControlsVisible(true);
    }

    void frame() {
        input.beginFrame();
    }

    @Test
    void theRightHalfAimsAndFiresPastTheThreshold() {
        src.touchDown(600, 300, 1, Buttons.LEFT);
        frame();
        assertThat(input.axis2("aim", v)).isEqualTo(new Vector2(0f, 0f));
        src.touchDragged(620, 300, 1);   // 0.4 of the radius: aiming, not firing
        frame();
        assertThat(input.axis2("aim", v).x).isCloseTo(0.25f, within(1e-4f));
        assertThat(input.pressed("fire")).isFalse();
        src.touchDragged(600, 260, 1);   // 0.8 up (y is down on screen)
        frame();
        assertThat(input.axis2("aim", v).y).isCloseTo(0.75f, within(1e-4f));
        assertThat(input.pressed("fire")).isTrue();
        assertThat(input.justPressed("fire")).isTrue();
        src.touchDragged(600, 380, 1);   // past the radius: clamped
        frame();
        assertThat(input.axis2("aim", v).len()).isCloseTo(1f, within(1e-4f));
        src.touchUp(600, 380, 1, Buttons.LEFT);
        frame();
        assertThat(input.pressed("fire")).isFalse();
        assertThat(input.axis2("aim", v).isZero()).isTrue();
        assertThat(input.touchControls().aimHeld()).isFalse();
    }

    @Test
    void buttonsAndTheLeftHalfKeepTheirTouches() {
        src.touchDown(728, 408, 2, Buttons.LEFT);   // on the bomb button
        frame();
        assertThat(input.pressed("bomb")).isTrue();
        assertThat(input.touchControls().aimHeld()).isFalse();
        src.touchDown(100, 300, 3, Buttons.LEFT);   // left half: the movement stick
        frame();
        assertThat(input.touchControls().stickHeld()).isTrue();
        assertThat(input.touchControls().aimHeld()).isFalse();
        src.touchDown(500, 200, 4, Buttons.LEFT);
        src.touchDragged(540, 200, 4);
        frame();
        assertThat(input.axis2("aim", v).x).isGreaterThan(0.5f);
        assertThat(input.axis2("move", v).isZero()).as("the two sticks are separate").isTrue();
    }

    @Test
    void anotherContextOnTopLetsGoAndReleasesFire() {
        src.touchDown(600, 300, 1, Buttons.LEFT);
        src.touchDragged(600, 250, 1);
        frame();
        assertThat(input.pressed("fire")).isTrue();
        input.pushContext("ui");
        frame();
        input.popContext();
        frame();
        assertThat(input.pressed("fire")).isFalse();
        assertThat(input.touchControls().aimHeld()).isFalse();
    }

    @Test
    void badAimSticksAreReported() {
        assertThatThrownBy(() -> InputBindings.parse(new JsonReader().parse(bindings(
                "\"aimStick\": { \"axis\": \"move\", \"fire\": \"shoot\", \"size\": 3 }")), "controls.json"))
                .hasMessageContaining("touchControls.aimStick.axis: must differ from the joystick's or D-pad's axis")
                .hasMessageContaining("touchControls.aimStick.fire: unknown action \"shoot\"")
                .hasMessageContaining("touchControls.aimStick.size");
    }
}
