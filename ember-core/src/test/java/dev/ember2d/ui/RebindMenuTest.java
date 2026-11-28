package dev.ember2d.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputDevice;
import dev.ember2d.input.internal.DefaultInput;
import dev.ember2d.input.internal.GamepadSource;
import dev.ember2d.input.internal.InputNames;
import dev.ember2d.render.RenderTestKit;
import java.lang.reflect.Proxy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The rebinding screen (F-005, I-535) with a real input and a headless stage. */
class RebindMenuTest {

    static final String BINDINGS = """
            { "version": 1, "contexts": {
                "gameplay": {
                  "actions": { "jump": ["SPACE", "Z", "PAD_A", "TAP"], "dash": ["X", "PAD_X"] },
                  "axes": { "move": { "left": ["A", "PAD_LEFT"], "right": ["D", "PAD_RIGHT"], "up": ["W"], "down": ["S"] } } },
                "ui": { "actions": { "ui_back": ["ESCAPE"], "ui_confirm": ["ENTER"] },
                        "axes": { "ui_nav": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"], "down": ["DOWN"] } } } } }
            """;

    final TimeState time = new TimeState(1f / 60f);
    final DefaultInput input = new DefaultInput(time, new DefaultEventBus(false));
    final float[] padValues = new float[InputNames.PAD_COUNT];
    String padName;
    Ui ui;
    BitmapFont font;
    int done;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[] {Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> 320;
                    case "getHeight", "getBackBufferHeight" -> 180;
                    case "getDeltaTime" -> 1f / 60f;
                    default -> method.getReturnType() == int.class ? 0 : method.getReturnType() == float.class ? 0f
                            : method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == long.class ? 0L : null;
                });
        input.setGamepadSource((GamepadSource) values -> {
            System.arraycopy(padValues, 0, values, 0, values.length);
            return padName;
        });
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "test.json"));
        input.pushContext("ui");
        font = new BitmapFont();
        ui = new Ui(new RenderTestKit().batch, input);
        ui.setSkin(UiSkins.pixel(font), true);
        ui.layout(320f, 180f, 0, 0, 320, 180);
    }

    @AfterEach
    void tearDown() {
        ui.dispose();
        font.dispose();
        HeadlessGdx.clear();
    }

    RebindMenu open() {
        return RebindMenu.open(ui, List.of(RebindMenu.action("jump", "Jump"), RebindMenu.action("dash", "Dash"),
                RebindMenu.axis("move", "left", "Move left")), () -> done++);
    }

    void frame(float seconds) {
        input.beginFrame();
        ui.act(seconds);
    }

    void key(int key) {
        input.source().keyDown(key);
        frame(1f / 60f);
        input.source().keyUp(key);
        frame(1f / 60f);
    }

    @Test
    void rowsShowTheCurrentDevicesInputs() {
        RebindMenu menu = open();
        assertThat(menu.device()).isEqualTo(InputDevice.KEYBOARD);
        assertThat(menu.rowText(0)).isEqualTo("Jump:  Space, Z");
        assertThat(menu.rowText(2)).isEqualTo("Move left:  A");
        menu.menu().activate(0);   // the device switch
        assertThat(menu.device()).isEqualTo(InputDevice.GAMEPAD);
        assertThat(menu.rowText(0)).isEqualTo("Jump:  A");
        assertThat(menu.rowText(2)).isEqualTo("Move left:  D-pad left");
        assertThat(menu.status()).isEqualTo("No gamepad connected");
    }

    @Test
    void aConnectedPadIsShownFirst() {
        padName = "Pad";
        input.beginFrame();
        assertThat(open().device()).isEqualTo(InputDevice.GAMEPAD);
    }

    @Test
    void aNewKeyReplacesOnlyTheKeyboardInputs() {
        RebindMenu menu = open();
        menu.menu().activate(1);   // Jump
        assertThat(menu.isListening()).isTrue();
        assertThat(menu.rowText(0)).isEqualTo("Jump:  press a key...");
        key(Keys.J);
        assertThat(menu.isListening()).isFalse();
        assertThat(input.actionInputs("jump")).containsExactly("PAD_A", "TAP", "J");
        assertThat(menu.rowText(0)).isEqualTo("Jump:  J");
        assertThat(menu.status()).isEqualTo("Jump: J");
    }

    @Test
    void aPadButtonReplacesOnlyThePadInputsOfAnAxisDirection() {
        padName = "Pad";
        input.beginFrame();
        RebindMenu menu = open();
        menu.menu().activate(3);   // Move left
        padValues[InputNames.PAD_NAMES.indexOf("PAD_L1")] = 1f;
        frame(1f / 60f);
        assertThat(input.axisInputs("move", "left")).containsExactly("A", "PAD_L1");
        assertThat(input.bindingOverrides()).containsKey("move:left");
    }

    @Test
    void sharedInputsAreReportedNotRemoved() {
        RebindMenu menu = open();
        menu.menu().activate(2);   // Dash
        key(Keys.Z);
        assertThat(menu.status()).isEqualTo("Z is also: Jump");
        assertThat(input.actionInputs("jump")).contains("Z");
        assertThat(input.actionInputs("dash")).containsExactly("PAD_X", "Z");
    }

    @Test
    void escapeCancelsAndSilenceTimesOut() {
        RebindMenu menu = open();
        menu.menu().activate(1);
        key(Keys.ESCAPE);
        assertThat(menu.status()).isEqualTo("Unchanged");
        assertThat(done).as("Escape only cancels the listening, not the screen").isZero();
        assertThat(input.actionInputs("jump")).containsExactly("SPACE", "Z", "PAD_A", "TAP");

        menu.menu().activate(1);
        frame(RebindMenu.LISTEN_SECONDS + 0.1f);
        assertThat(menu.isListening()).isFalse();
        assertThat(input.isListening()).isFalse();
        assertThat(menu.status()).isEqualTo("No input: unchanged");
    }

    @Test
    void resetAllAndDone() {
        RebindMenu menu = open();
        input.rebindAction("jump", List.of("K"));
        menu.menu().activate(4);   // Reset all
        assertThat(input.actionInputs("jump")).containsExactly("SPACE", "Z", "PAD_A", "TAP");
        assertThat(menu.rowText(0)).isEqualTo("Jump:  Space, Z");
        menu.menu().activate(5);   // Done
        assertThat(done).isEqualTo(1);
        assertThat(ui.stage().getActors().size).as("the overlay is gone").isEqualTo(1);
    }

    @Test
    void labelsAreReadable() {
        assertThat(RebindMenu.label("SPACE")).isEqualTo("Space");
        assertThat(RebindMenu.label("L_SHIFT")).isEqualTo("L Shift");
        assertThat(RebindMenu.label("MOUSE_LEFT")).isEqualTo("Mouse Left");
        assertThat(RebindMenu.label("PAD_A")).isEqualTo("A");
        assertThat(RebindMenu.label("PAD_R2")).isEqualTo("RT");
        assertThat(RebindMenu.label("PAD_LSTICK_LEFT")).isEqualTo("L-stick left");
    }
}
