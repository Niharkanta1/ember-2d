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
                "input.json: extra: unknown field (expected version, contexts)",
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
    void rejectsMissingVersionAndContextsAndNonObjects() {
        assertThat(invalid("{ }").problems()).containsExactly(
                "input.json: version: missing (expected 1)", "input.json: contexts: missing");
        assertThat(invalid("[1]").problems()).containsExactly("input.json: (root): must be a JSON object");
    }
}
