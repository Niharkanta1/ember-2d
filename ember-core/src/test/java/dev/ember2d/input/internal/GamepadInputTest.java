package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.GamepadConnected;
import dev.ember2d.input.GamepadDisconnected;
import dev.ember2d.input.InputBindings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Gamepad input (F-005, I-530) through a fake pad source. */
class GamepadInputTest {

    static final String BINDINGS = """
            { "version": 1, "contexts": {
                "gameplay": {
                  "actions": { "jump": ["SPACE", "PAD_A"], "right": ["PAD_LSTICK_RIGHT"] },
                  "axes": { "move": { "left": ["A", "PAD_LEFT", "PAD_LSTICK_LEFT"], "right": ["D", "PAD_RIGHT", "PAD_LSTICK_RIGHT"],
                                      "up": ["W", "PAD_LSTICK_UP"], "down": ["S", "PAD_LSTICK_DOWN"] } } },
                "ui": { "actions": { "ui_confirm": ["ENTER", "PAD_A"] } } } }
            """;

    /** A pad whose values the test sets; null name = disconnected. */
    static final class FakePad implements GamepadSource {
        final float[] values = new float[InputNames.PAD_COUNT];
        String name = "Test Pad";

        void set(String input, float value) {
            values[InputNames.PAD_NAMES.indexOf(input)] = value;
        }

        void stick(float x, float y) {   // raw left stick, y up
            set("PAD_LSTICK_LEFT", Math.max(0f, -x));
            set("PAD_LSTICK_RIGHT", Math.max(0f, x));
            set("PAD_LSTICK_UP", Math.max(0f, y));
            set("PAD_LSTICK_DOWN", Math.max(0f, -y));
        }

        @Override
        public String poll(float[] out) {
            if (name == null) {
                java.util.Arrays.fill(out, 0f);
                return null;
            }
            System.arraycopy(values, 0, out, 0, values.length);
            return name;
        }
    }

    final TimeState time = new TimeState(1f / 60f);
    final DefaultEventBus bus = new DefaultEventBus(false);
    final DefaultInput input = new DefaultInput(time, bus);
    final FakePad pad = new FakePad();
    final List<Object> events = new ArrayList<>();

    GamepadInputTest() {
        input.setGamepadSource(pad);
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "test.json"));
        bus.onGlobal(GamepadConnected.class, events::add);
        bus.onGlobal(GamepadDisconnected.class, events::add);
    }

    void frame() {
        input.beginFrame();
        bus.flush();
    }

    Vector2 move() {
        return input.axis2("move", new Vector2());
    }

    @Test
    void padNamesAreBindingNames() {
        assertThat(InputNames.code("PAD_A")).isEqualTo(InputNames.PAD_BASE);
        assertThat(InputNames.name(InputNames.PAD_BASE + InputNames.PAD_COUNT - 1)).isEqualTo("PAD_RSTICK_DOWN");
        assertThat(InputNames.all()).contains("PAD_START", "PAD_L2", "PAD_LSTICK_UP");
        assertThat(new HashSet<>(InputNames.PAD_NAMES)).hasSize(InputNames.PAD_COUNT);
        assertThat(InputNames.isPad(InputNames.code("SPACE"))).isFalse();
    }

    @Test
    void buttonsPressActionsLikeKeys() {
        frame();
        pad.set("PAD_A", 1f);
        frame();
        assertThat(input.pressed("jump")).isTrue();
        assertThat(input.justPressed("jump")).isTrue();
        frame();
        assertThat(input.justPressed("jump")).isFalse();
        pad.set("PAD_A", 0f);
        frame();
        assertThat(input.justReleased("jump")).isTrue();
    }

    @Test
    void sticksAreAnalogWithARadialDeadZone() {
        pad.stick(0.15f, 0f);
        frame();
        assertThat(move()).isEqualTo(new Vector2(0f, 0f));
        pad.stick(0.6f, 0f);
        frame();
        assertThat(move().x).isCloseTo(0.5f, within(1e-5f));   // (0.6 - 0.2) / 0.8
        pad.stick(0f, 1f);
        frame();
        assertThat(move().y).isCloseTo(1f, within(1e-5f));
        pad.stick(0.9f, 0.9f);   // a raw corner, longer than 1
        frame();
        assertThat(move().len()).isCloseTo(1f, within(1e-4f));
        assertThat(move().x).isCloseTo(move().y, within(1e-5f));
    }

    @Test
    void theDpadAndKeysStillGiveDigitalDiagonals() {
        pad.set("PAD_RIGHT", 1f);
        input.source().keyDown(com.badlogic.gdx.Input.Keys.W);
        frame();
        assertThat(move().x).isCloseTo(0.7071f, within(1e-4f));
        assertThat(move().y).isCloseTo(0.7071f, within(1e-4f));
    }

    @Test
    void stickActionsUseHysteresis() {
        pad.stick(0.7f, 0f);   // 0.625 after the dead zone: past 0.5
        frame();
        assertThat(input.pressed("right")).isTrue();
        pad.stick(0.55f, 0f);   // 0.4375: above the release threshold 0.4
        frame();
        assertThat(input.pressed("right")).isTrue();
        pad.stick(0.5f, 0f);   // 0.375: released
        frame();
        assertThat(input.pressed("right")).isFalse();
        pad.stick(0.58f, 0f);   // 0.475: above the release threshold, but not past 0.5 again
        frame();
        assertThat(input.pressed("right")).isFalse();
    }

    @Test
    void padInputsBelongToTheTopContext() {
        pad.set("PAD_A", 1f);
        frame();
        assertThat(input.pressed("jump")).isTrue();
        input.pushContext("ui");
        frame();
        assertThat(input.pressed("jump")).isFalse();
        assertThat(input.pressed("ui_confirm")).as("held across the change: up until pressed again").isFalse();
        pad.set("PAD_A", 0f);
        frame();
        pad.set("PAD_A", 1f);
        frame();
        assertThat(input.justPressed("ui_confirm")).isTrue();
    }

    @Test
    void connectingAndDisconnectingAreReportedAndReleaseEverything() {
        pad.name = null;
        frame();
        assertThat(input.gamepadConnected()).isFalse();
        assertThat(input.gamepadName()).isEmpty();
        pad.name = "Test Pad";
        pad.set("PAD_A", 1f);
        pad.stick(1f, 0f);
        frame();
        assertThat(input.gamepadConnected()).isTrue();
        assertThat(input.gamepadName()).isEqualTo("Test Pad");
        assertThat(input.pressed("jump")).isTrue();
        pad.name = null;
        frame();
        assertThat(input.pressed("jump")).isFalse();
        assertThat(move()).isEqualTo(new Vector2(0f, 0f));
        assertThat(events).containsExactly(new GamepadConnected("Test Pad"), new GamepadDisconnected("Test Pad"));
    }

    @Test
    void withoutARunningAppTheRealSourceReadsNothing() {
        DefaultInput plain = new DefaultInput(time, bus);
        plain.beginFrame();
        assertThat(plain.gamepadConnected()).isFalse();
        float[] values = new float[InputNames.PAD_COUNT];
        assertThat(new GdxGamepads().poll(values)).isNull();
    }
}
