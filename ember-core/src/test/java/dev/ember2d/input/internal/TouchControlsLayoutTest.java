package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputBindingsException;
import dev.ember2d.input.TouchLayout;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Full on-screen controls (F-022, I-225) on an 800 x 480 window at density 1: D-pad, controls per input context,
 * auto-hide, per-button visibility, the player's layout, safe insets and haptics. Touch coordinates are libGDX's (y
 * down). D-pad centre (96, 384 y-down), radius 56. Jump button "A" centre (728, 408), radius 40.
 */
class TouchControlsLayoutTest {

    static String bindings(String stick) {
        return """
                { "version": 1,
                  "contexts": {
                    "gameplay": { "actions": { "jump": ["SPACE"], "dash": ["X"], "touch": ["TOUCH"] },
                                  "axes": { "move": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"],
                                                      "down": ["DOWN"] } } },
                    "ui": { "actions": { "ui_back": ["ESCAPE"] } } },
                  "touchControls": {
                    %s
                    "buttons": [ { "action": "jump", "label": "A", "x": 72, "y": 72, "radius": 40 },
                                 { "action": "dash", "label": "B", "x": 160, "y": 64, "radius": 30 } ] } }
                """.formatted(stick);
    }

    final TimeState time = new TimeState(1f / 60f);
    final DefaultInput input = new DefaultInput(time, new DefaultEventBus(false));
    final GdxInputSource src = input.source();
    final Vector2 v = new Vector2();
    final Vector3 c = new Vector3();

    TouchControlsLayoutTest() {
        use("\"dpad\": { \"axis\": \"move\", \"x\": 96, \"y\": 96, \"radius\": 56 },");
    }

    void use(String stick) {
        input.setBindings(InputBindings.parse(new JsonReader().parse(bindings(stick)), "controls.json"));
        input.setScreenSize(800f, 480f);
        input.setTouchControlsVisible(true);
    }

    void frame() {
        input.beginFrame();
    }

    Vector2 dpadAt(int x, int yDown) {
        src.touchDragged(x, yDown, 0);
        frame();
        return input.axis2("move", v);
    }

    @Test
    void theDpadGivesDigitalDirectionsWithDiagonalsAndADeadZone() {
        src.touchDown(96, 384, 0, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).as("centre: dead zone").isEqualTo(Vector2.Zero);
        assertThat(dpadAt(140, 384)).isEqualTo(new Vector2(1f, 0f));
        assertThat(dpadAt(96, 340)).as("screen up").isEqualTo(new Vector2(0f, 1f));
        Vector2 diag = dpadAt(130, 350);
        assertThat(diag.x).isCloseTo(0.7071f, within(1e-3f));
        assertThat(diag.y).isCloseTo(0.7071f, within(1e-3f));
        assertThat(dpadAt(50, 384)).as("slid across to the left").isEqualTo(new Vector2(-1f, 0f));
        assertThat(input.pressed("touch")).as("claimed").isFalse();
        src.touchUp(50, 384, 0, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);

        use("\"dpad\": { \"axis\": \"move\", \"x\": 96, \"y\": 96, \"radius\": 56, \"diagonals\": false },");
        src.touchDown(130, 350, 0, Buttons.LEFT);
        frame();
        assertThat(input.axis2("move", v)).as("four directions: the dominant axis").isIn(new Vector2(1f, 0f),
                new Vector2(0f, 1f));
        assertThat(input.touchControlNames()).containsExactly("dpad", "jump", "dash");
    }

    @Test
    void aJoystickAndADpadTogetherAreRejected() {
        InputBindingsException e = catchThrowableOfType(InputBindingsException.class, () -> InputBindings.parse(
                new JsonReader().parse(bindings("\"joystick\": { \"axis\": \"move\" }, "
                        + "\"dpad\": { \"axis\": \"move\", \"diagonals\": 1 },")), "c.json"));
        assertThat(e.problems()).contains("c.json: touchControls.dpad: use a joystick or a dpad, not both",
                "c.json: touchControls.dpad.diagonals: must be true or false");
    }

    @Test
    void controlsOnlyWorkWhileTheirContextIsOnTop() {
        src.touchDown(728, 408, 0, Buttons.LEFT);
        frame();
        assertThat(input.pressed("jump")).isTrue();

        input.pushContext("ui");
        frame();
        assertThat(input.touchControls().buttonShown(0)).as("hidden in menus").isFalse();
        assertThat(input.touchControls().stickShown()).isFalse();
        assertThat(input.touchControls().pressed(0)).as("let go").isFalse();
        src.touchUp(728, 408, 0, Buttons.LEFT);
        src.touchDown(728, 408, 1, Buttons.LEFT);
        frame();
        assertThat(input.touchControls().pressed(0)).as("not live: does not press").isFalse();

        input.popContext();
        src.touchUp(728, 408, 1, Buttons.LEFT);
        frame();
        assertThat(input.touchControls().buttonShown(0)).isTrue();
    }

    @Test
    void keyboardUseHidesTheControlsAndATouchBringsThemBack() {
        input.setAutoHide(true);
        src.keyDown(Keys.SPACE);
        frame();
        assertThat(input.touchControlsActive()).as("hidden after a key").isFalse();
        src.keyUp(Keys.SPACE);
        src.keyDown(Keys.BACK);
        frame();
        src.keyUp(Keys.BACK);

        src.touchDown(728, 408, 0, Buttons.LEFT);
        frame();
        assertThat(input.touchControlsActive()).isTrue();
        assertThat(input.pressed("jump")).as("the touch that brought them back still acts").isTrue();

        input.setAutoHide(false);
        src.keyDown(Keys.SPACE);
        frame();
        assertThat(input.touchControlsActive()).as("ALWAYS: never auto-hidden").isTrue();
    }

    @Test
    void gameCodeCanHideAButton() {
        input.setTouchButtonVisible("dash", false);
        assertThat(input.touchControls().buttonShown(1)).isFalse();
        src.touchDown(800 - 160, 480 - 64, 0, Buttons.LEFT);
        frame();
        assertThat(input.pressed("dash")).isFalse();
        assertThat(input.pressed("touch")).as("an empty spot: the game gets the touch").isTrue();
        input.setTouchButtonVisible("dash", true);
        assertThat(input.touchControls().buttonShown(1)).isTrue();
        assertThatThrownBy(() -> input.setTouchButtonVisible("fly", true))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fly");
    }

    @Test
    void thePlayersLayoutMovesSizesAndHidesControls() {
        input.moveTouchControl("jump", 600f, 100f);   // window pixels, y up
        input.touchControlCircle("jump", c);
        assertThat(c.x).isCloseTo(600f, within(1e-3f));
        assertThat(c.y).isCloseTo(100f, within(1e-3f));
        input.moveTouchControl("jump", 600f, 400f);
        assertThat(input.touchLayout().get("jump").y()).as("upper half: from the top").isNegative();
        input.touchControlCircle("jump", c);
        assertThat(c.y).isCloseTo(400f, within(1e-3f));

        TouchLayout layout = input.touchLayout();
        layout.scale("dpad", 5f).setVisible("dash", false).setOpacity(0.05f);
        input.setTouchLayout(layout);
        input.touchControlCircle("dpad", c);
        assertThat(c.z).as("scale clamped to 1.6").isCloseTo(56f * 1.6f, within(1e-3f));
        assertThat(input.touchControls().buttonShown(1)).isFalse();
        assertThat(input.touchControls().opacity()).isEqualTo(TouchLayout.MIN_OPACITY);

        TouchLayout copy = TouchLayout.parse(layout.toJson());
        assertThat(copy.toJson()).isEqualTo(layout.toJson());
        assertThat(copy.scaleOf("dpad")).isEqualTo(1.6f);
        assertThat(copy.visibleOf("dash")).isFalse();
        assertThatThrownBy(() -> TouchLayout.parse("{\"version\": 2}")).isInstanceOf(IllegalArgumentException.class);

        input.setTouchLayout(TouchLayout.defaults());
        input.touchControlCircle("jump", c);
        assertThat(c.x).isEqualTo(728f);
        assertThat(input.touchControlCircle("nope", c)).isFalse();
    }

    @Test
    void controlsStayInsideTheSafeArea() {
        input.setSafeInsets(40f, 0f, 60f, 20f);
        input.touchControlCircle("jump", c);
        assertThat(c.x).isEqualTo(800f - 60f - 72f);
        assertThat(c.y).isEqualTo(20f + 72f);
        input.touchControlCircle("dpad", c);
        assertThat(c.x).isEqualTo(40f + 96f);
    }

    @Test
    void vibrationOnlyWhenEnabled() {
        HeadlessGdx.install();
        List<Object> calls = new ArrayList<>();
        Gdx.input = HeadlessGdx.recorder(com.badlogic.gdx.Input.class, (name, args) -> {
            if (name.equals("vibrate")) {
                calls.add(args[0]);
            }
        });
        try {
            input.vibrate(40);
            input.setHapticsEnabled(false);
            input.vibrate(40);
            assertThat(calls).containsExactly(40);
            assertThat(input.hapticsEnabled()).isFalse();
        } finally {
            Gdx.input = null;
            HeadlessGdx.clear();
        }
    }
}
