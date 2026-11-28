package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputDevice;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Listening for a new input and axis overrides (F-005, I-535). */
class RebindListenTest {

    static final String BINDINGS = """
            { "version": 1, "contexts": {
                "gameplay": {
                  "actions": { "jump": ["SPACE", "PAD_A"], "fire": ["MOUSE_LEFT"] },
                  "axes": { "move": { "left": ["A", "PAD_LEFT"], "right": ["D"], "up": ["W"], "down": ["S"] },
                            "zoom": { "negative": ["Q"], "positive": ["E"] } } } } }
            """;

    final TimeState time = new TimeState(1f / 60f);
    final DefaultInput input = new DefaultInput(time, new DefaultEventBus(false));
    final GamepadInputTest.FakePad pad = new GamepadInputTest.FakePad();
    final List<String> heard = new ArrayList<>();
    int cancelled;

    RebindListenTest() {
        input.setGamepadSource(pad);
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "test.json"));
    }

    void listen(InputDevice... devices) {
        input.listenForInput(EnumSet.of(devices[0], devices), heard::add, () -> cancelled++);
    }

    void press(int key) {
        input.source().keyDown(key);
        input.beginFrame();
    }

    void release(int key) {
        input.source().keyUp(key);
        input.beginFrame();
    }

    @Test
    void theNextKeyIsDeliveredAndTriggersNothing() {
        listen(InputDevice.KEYBOARD);
        assertThat(input.isListening()).isTrue();
        press(Keys.SPACE);
        assertThat(heard).containsExactly("SPACE");
        assertThat(input.isListening()).isFalse();
        assertThat(input.pressed("jump")).as("swallowed").isFalse();
        release(Keys.SPACE);
        press(Keys.SPACE);
        assertThat(input.pressed("jump")).as("the next press acts normally").isTrue();
    }

    @Test
    void onlyWantedDevicesAreDeliveredAndOthersAreSwallowed() {
        listen(InputDevice.GAMEPAD);
        press(Keys.SPACE);
        assertThat(heard).isEmpty();
        assertThat(input.pressed("jump")).as("a key while listening for the pad is swallowed").isFalse();
        assertThat(input.isListening()).isTrue();
        pad.set("PAD_Y", 1f);
        input.beginFrame();
        assertThat(heard).containsExactly("PAD_Y");
    }

    @Test
    void theMouseKeepsWorkingUnlessListenedFor() {
        listen(InputDevice.KEYBOARD);
        input.source().touchDown(5, 5, 0, com.badlogic.gdx.Input.Buttons.LEFT);
        input.beginFrame();
        assertThat(input.pressed("fire")).isTrue();
        assertThat(heard).isEmpty();
    }

    @Test
    void escapeAndBackCancel() {
        listen(InputDevice.KEYBOARD);
        press(Keys.ESCAPE);
        assertThat(cancelled).isEqualTo(1);
        assertThat(heard).isEmpty();
        assertThat(input.isListening()).isFalse();
        listen(InputDevice.KEYBOARD);
        press(Keys.BACK);
        assertThat(cancelled).isEqualTo(2);
    }

    @Test
    void stoppingCallsNothing() {
        listen(InputDevice.KEYBOARD);
        input.stopListening();
        press(Keys.SPACE);
        assertThat(heard).isEmpty();
        assertThat(cancelled).isZero();
        assertThat(input.pressed("jump")).isTrue();
    }

    @Test
    void axisDirectionsCanBeRebound() {
        input.rebindAxis("move", "left", List.of("J", "PAD_LEFT"));
        assertThat(input.axisInputs("move", "left")).containsExactly("J", "PAD_LEFT");
        press(Keys.J);
        assertThat(input.axis2("move", new Vector2()).x).isEqualTo(-1f);
        assertThat(input.bindingOverrides()).containsEntry("move:left", List.of("J", "PAD_LEFT"));

        // Overrides survive a reload of the file, and reset like action overrides.
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "test.json"));
        assertThat(input.axisInputs("move", "left")).containsExactly("J", "PAD_LEFT");
        input.resetBinding("move:left");
        assertThat(input.axisInputs("move", "left")).containsExactly("A", "PAD_LEFT");

        input.rebindAxis("zoom", "positive", List.of("R"));
        assertThatThrownBy(() -> input.rebindAxis("move", "positive", List.of("R")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no direction");
        assertThatThrownBy(() -> input.rebindAxis("move", "left", List.of())).hasMessageContaining("at least one");
    }

    @Test
    void resetAllRemovesEveryOverride() {
        input.rebindAction("jump", List.of("Z"));
        input.rebindAxis("move", "right", List.of("L"));
        input.resetAllBindings();
        assertThat(input.bindingOverrides()).isEqualTo(Map.of());
        assertThat(input.actionInputs("jump")).containsExactly("SPACE", "PAD_A");
        assertThat(input.axisInputs("move", "right")).containsExactly("D");
    }

    @Test
    void devicesOfBindingNames() {
        assertThat(InputDevice.of("SPACE")).isEqualTo(InputDevice.KEYBOARD);
        assertThat(InputDevice.of("BACK")).isEqualTo(InputDevice.KEYBOARD);
        assertThat(InputDevice.of("MOUSE_RIGHT")).isEqualTo(InputDevice.MOUSE);
        assertThat(InputDevice.of("DOUBLE_TAP")).isEqualTo(InputDevice.TOUCH);
        assertThat(InputDevice.of("PAD_LSTICK_UP")).isEqualTo(InputDevice.GAMEPAD);
        assertThat(InputDevice.of("NOPE")).isNull();
    }
}
