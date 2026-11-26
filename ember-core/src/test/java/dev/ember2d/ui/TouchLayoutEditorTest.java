package dev.ember2d.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.internal.DefaultInput;
import dev.ember2d.render.RenderTestKit;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The touch layout editor (F-022, I-225) on a 320 x 180 window whose UI maps 1:1 to it. The jump button sits at
 * (248, 72) with radius 40, window pixels y up.
 */
class TouchLayoutEditorTest {

    static final String BINDINGS = """
            { "version": 1,
              "contexts": {
                "gameplay": { "actions": { "jump": ["SPACE"] },
                              "axes": { "move": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"],
                                                  "down": ["DOWN"] } } },
                "ui": { "actions": { "ui_back": ["ESCAPE"], "ui_confirm": ["ENTER"] } } },
              "touchControls": {
                "joystick": { "axis": "move" },
                "buttons": [ { "action": "jump", "label": "A", "x": 72, "y": 72, "radius": 40 } ] } }
            """;

    final DefaultInput input = new DefaultInput(new TimeState(1f / 60f), new DefaultEventBus(false));
    final Vector3 c = new Vector3();
    Ui ui;
    BitmapFont font;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[] {Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> 320;
                    case "getHeight", "getBackBufferHeight" -> 180;
                    default -> method.getReturnType() == int.class ? 0 : method.getReturnType() == float.class ? 0f
                            : method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == long.class ? 0L : null;
                });
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "input.json"));
        input.setScreenSize(320f, 180f);
        input.setTouchControlsVisible(true);
        font = new BitmapFont();
        ui = new Ui(new RenderTestKit().batch, input);
        ui.setSkin(UiSkins.pixel(font), true);
        ui.layout(320f, 180f, 0, 0, 320, 180);
        ui.root().add(new Label("settings", ui.skin()));
    }

    @AfterEach
    void tearDown() {
        ui.dispose();
        font.dispose();
        HeadlessGdx.clear();
    }

    @Test
    void dragMovesAControlAndDoneRestoresTheUi() {
        input.pushContext("ui");
        boolean[] done = {false};
        Table overlay = TouchLayoutEditor.open(ui, input, () -> done[0] = true);
        assertThat(ui.root().isVisible()).as("the rest of the UI is hidden").isFalse();
        assertThat(input.touchControls().stickShown()).as("shown while editing, even in menus").isTrue();
        ui.stage().act(0f);
        overlay.validate();

        // Stage touches use window pixels, y down.
        ui.stage().touchDown(248, 180 - 72, 0, 0);
        ui.stage().touchDragged(200, 180 - 90, 0);
        ui.stage().touchUp(200, 180 - 90, 0, 0);
        input.touchControlCircle("jump", c);
        assertThat(c.x).isCloseTo(200f, within(1e-3f));
        assertThat(c.y).isCloseTo(90f, within(1e-3f));
        assertThat(input.pressed("jump")).as("inert while editing").isFalse();
        assertThat(labels(overlay)).contains("jump");

        input.source().keyDown(Keys.ESCAPE);
        input.beginFrame();
        ui.act(1f / 60f);
        assertThat(done[0]).isTrue();
        assertThat(overlay.getStage()).isNull();
        assertThat(ui.root().isVisible()).isTrue();
        assertThat(input.touchControls().stickShown()).as("back to hidden in menus").isFalse();
    }

    @Test
    void aTouchAwayFromTheControlsIsIgnored() {
        Table overlay = TouchLayoutEditor.open(ui, input, () -> { });
        ui.stage().act(0f);
        overlay.validate();
        ui.stage().touchDown(160, 10, 0, 0);
        ui.stage().touchDragged(170, 20, 0);
        input.touchControlCircle("jump", c);
        assertThat(c.x).isEqualTo(248f);
        assertThat(labels(overlay)).contains("Drag a control");
    }

    static java.util.List<String> labels(Actor root) {
        java.util.List<String> out = new java.util.ArrayList<>();
        collect(root, out);
        return out;
    }

    static void collect(Actor actor, java.util.List<String> out) {
        if (actor instanceof Label label) {
            out.add(label.getText().toString());
        }
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group group) {
            for (Actor child : group.getChildren()) {
                collect(child, out);
            }
        }
    }
}
