package dev.ember2d.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.utils.JsonReader;
import org.junit.jupiter.api.Test;

class InputBindingsTest {

    static final String VALID = """
            { "version": 1,
              "contexts": {
                "ui": {
                  "actions": { "ui_back": ["ESCAPE", "BACK"] },
                  "axes": { "ui_nav": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"], "down": ["DOWN"],
                                        "normalize": false } } },
                "gameplay": {
                  "actions": { "jump": ["SPACE", "Z", "SPACE"], "fire": ["MOUSE_LEFT"], "idle": [] },
                  "axes": {
                    "move": { "left": ["A", "LEFT"], "right": ["D", "RIGHT"], "up": ["W"], "down": ["S"] },
                    "steer": { "negative": ["Q"], "positive": ["E"] } } } } }
            """;

    static InputBindings parse(String json) {
        return InputBindings.parse(new JsonReader().parse(json), "input.json");
    }

    static InputBindingsException invalid(String json) {
        return catchThrowableOfType(InputBindingsException.class, () -> parse(json));
    }

    @Test
    void parsesContextsActionsAndAxes() {
        InputBindings b = parse(VALID);

        assertThat(b.contexts()).containsExactly("gameplay", "ui");
        assertThat(b.actions()).containsExactly("ui_back", "jump", "fire", "idle");
        assertThat(b.axes()).containsExactly("ui_nav", "move", "steer");
        assertThat(b.contextOf("jump")).isEqualTo("gameplay");
        assertThat(b.contextOf("ui_nav")).isEqualTo("ui");
        assertThat(b.actionInputs("jump")).as("duplicates dropped").containsExactly("SPACE", "Z");
        assertThat(b.actionInputs("idle")).isEmpty();
        assertThat(b.isAxis2D("move")).isTrue();
        assertThat(b.axisNormalized("move")).isTrue();
        assertThat(b.axisNormalized("ui_nav")).isFalse();
        assertThat(b.isAxis2D("steer")).isFalse();
        assertThat(b.axisInputs("move", "left")).containsExactly("A", "LEFT");
        assertThat(b.axisInputs("steer", "positive")).containsExactly("E");
        assertThat(b.binds("BACK")).isTrue();
        assertThat(b.binds("Q")).isTrue();
        assertThat(b.binds("F12")).isFalse();
    }

    @Test
    void emptyHasOnlyGameplay() {
        InputBindings b = InputBindings.empty();

        assertThat(b.contexts()).containsExactly("gameplay");
        assertThat(b.actions()).isEmpty();
        assertThat(b.axes()).isEmpty();
    }

    @Test
    void unknownNamesFailWithFileAndPath() {
        assertThatThrownBy(() -> parse(VALID).actionInputs("jupm"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jupm").hasMessageContaining("jump");

        InputBindingsException e = invalid("""
                { "version": 1, "contexts": { "gameplay": { "actions": { "jump": ["SPACE", "SPCE", "space"] } } } }
                """);

        assertThat(e.problems()).containsExactly(
                "input.json: contexts.gameplay.actions.jump[1]: unknown input name \"SPCE\"",
                "input.json: contexts.gameplay.actions.jump[2]: unknown input name \"space\" (did you mean \"SPACE\"?)");
        assertThat(e).hasMessageStartingWith("Invalid input bindings input.json:");
    }

    @Test
    void reportsEveryStructuralProblem() {
        InputBindingsException e = invalid("""
                { "version": 2, "extra": 1,
                  "contexts": {
                    "ui": { "actions": { "jump": ["ENTER"] }, "keys": {} },
                    "menu": [],
                    "other": {
                      "actions": { "jump": ["SPACE"], "fire": "MOUSE_LEFT", "x": [3] },
                      "axes": {
                        "move": { "left": ["A"], "right": ["D"], "up": ["W"], "sideways": ["S"] },
                        "zoom": { "negative": ["Q"], "positive": ["E"], "normalize": true },
                        "tilt": { "left": ["A"], "right": ["D"], "up": ["W"], "down": ["S"], "normalize": "yes" },
                        "bad": 5 } } } }
                """);

        assertThat(e.problems()).containsExactlyInAnyOrder(
                "input.json: extra: unknown field (expected version, contexts, touch, touchControls)",
                "input.json: version: unsupported version 2 (expected 1)",
                "input.json: contexts: the \"gameplay\" context is required",
                "input.json: contexts.ui.keys: unknown field (expected actions, axes)",
                "input.json: contexts.menu: must be an object with actions and/or axes",
                "input.json: contexts.other.actions.jump: name \"jump\" is already used by another action or axis",
                "input.json: contexts.other.actions.fire: must be a list of input names",
                "input.json: contexts.other.actions.x[0]: must be an input name string",
                "input.json: contexts.other.axes.move.sideways: unknown direction (expected left, right, up, down, normalize)",
                "input.json: contexts.other.axes.move: missing direction \"down\"",
                "input.json: contexts.other.axes.zoom.normalize: only 2D axes (left/right/up/down) can be normalised",
                "input.json: contexts.other.axes.tilt.normalize: must be true or false",
                "input.json: contexts.other.axes.bad: must be an object with negative/positive or left/right/up/down");
    }

    @Test
    void touchThresholdsDefaultAndOverride() {
        assertThat(parse(VALID).touch()).isEqualTo(TouchThresholds.DEFAULTS);
        assertThat(InputBindings.empty().touch()).isEqualTo(TouchThresholds.DEFAULTS);

        InputBindings b = parse("""
                { "version": 1, "touch": { "tapSlop": 16, "longPressTime": 0.8 },
                  "contexts": { "gameplay": { "actions": { "tap": ["TAP", "SWIPE_LEFT", "MOUSE_LEFT"] } } } }
                """);

        assertThat(b.touch()).isEqualTo(new TouchThresholds(16f, 0.3f, 0.3f, 0.8f, 48f, 0.3f));
        assertThat(b.actionInputs("tap")).containsExactly("TAP", "SWIPE_LEFT", "MOUSE_LEFT");
    }

    @Test
    void invalidTouchThresholdsAreReported() {
        assertThat(invalid("""
                { "version": 1, "touch": { "tapSlop": 0, "tapTime": "fast", "pinch": 1 },
                  "contexts": { "gameplay": { } } }
                """).problems()).containsExactly(
                "input.json: touch.tapSlop: must be a number > 0",
                "input.json: touch.tapTime: must be a number > 0",
                "input.json: touch.pinch: unknown field (expected tapSlop, tapTime, doubleTapTime, longPressTime, "
                        + "swipeDistance, swipeTime)");
        assertThat(invalid("""
                { "version": 1, "touch": [], "contexts": { "gameplay": { } } }
                """).problems()).hasSize(1).first().asString().startsWith("input.json: touch: must be an object");
    }

    @Test
    void touchControlsParseWithDefaults() {
        assertThat(parse(VALID).touchControls()).as("no section").isNull();
        InputBindings b = parse("""
                { "version": 1, "touchControls": { "joystick": { "axis": "move" },
                    "buttons": [ { "action": "jump", "label": "A", "x": 72, "y": -40 } ] },
                  "contexts": { "gameplay": { "actions": { "jump": ["SPACE"] },
                    "axes": { "move": { "left": ["A"], "right": ["D"], "up": ["W"], "down": ["S"] } } } } }
                """);

        TouchControlsConfig c = b.touchControls();
        assertThat(c.opacity()).isEqualTo(0.35f);
        assertThat(c.joystick()).isEqualTo(new TouchControlsConfig.Joystick("move", true, 96f, 96f, 56f, 0.2f));
        assertThat(c.buttons()).containsExactly(new TouchControlsConfig.Button("jump", "A", 72f, -40f, 40f));
    }

    @Test
    void invalidTouchControlsAreReported() {
        InputBindingsException e = invalid("""
                { "version": 1, "touchControls": { "opacity": 2, "size": 1,
                    "joystick": { "axis": "steer", "mode": "wobbly", "radius": 0 },
                    "buttons": [ { "action": "fly", "x": 10, "y": 10 }, { "action": "jump", "label": 3 }, 5 ] },
                  "contexts": { "gameplay": { "actions": { "jump": ["SPACE"] },
                    "axes": { "steer": { "negative": ["Q"], "positive": ["E"] } } } } }
                """);

        assertThat(e.problems()).containsExactlyInAnyOrder(
                "input.json: touchControls.size: unknown field (expected opacity, joystick, dpad, buttons, aimStick)",
                "input.json: touchControls.opacity: must be a number from 0 to 1",
                "input.json: touchControls.joystick.axis: axis \"steer\" must be 2D (left/right/up/down)",
                "input.json: touchControls.joystick.mode: must be \"floating\" or \"fixed\"",
                "input.json: touchControls.joystick.radius: must be a number > 0",
                "input.json: touchControls.buttons[0].action: unknown action \"fly\"",
                "input.json: touchControls.buttons[1].label: must be a string",
                "input.json: touchControls.buttons[1]: \"x\" and \"y\" are required",
                "input.json: touchControls.buttons[2]: must be an object");
    }

    @Test
    void rejectsMissingVersionAndContextsAndNonObjects() {
        assertThat(invalid("{ }").problems()).containsExactly(
                "input.json: version: missing (expected 1)", "input.json: contexts: missing");
        assertThat(invalid("[1]").problems()).containsExactly("input.json: (root): must be a JSON object");
    }
}
