package dev.ember2d.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.input.Input;
import dev.ember2d.render.RenderTestKit;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The UI layer's widgets (F-012, I-220), headless: a recording batch and scripted input actions. */
class UiTest {

    /** Scripted actions: {@code ui_nav} as x/y, and the actions pressed this frame. */
    final Vector2 nav = new Vector2();
    final Set<String> pressed = new HashSet<>();
    final List<String> log = new ArrayList<>();
    Ui ui;
    BitmapFont font;

    final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hasAction" -> true;
                case "justPressed" -> pressed.contains((String) args[0]);
                case "axis2" -> ((Vector2) args[1]).set(nav);
                default -> method.getReturnType() == boolean.class ? false
                        : method.getReturnType() == float.class ? 0f : null;
            });

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

    /** One frame: act, then clear the pressed actions. */
    void frame() {
        ui.act(1f / 60f);
        pressed.clear();
    }

    MenuList menu(String... labels) {
        MenuList menu = new MenuList(ui);
        for (String label : labels) {
            menu.add(label, () -> log.add(label));
        }
        ui.root().add(menu);
        ui.stage().act(0f);
        ui.root().validate();
        return menu;
    }

    @Test
    void thePixelSkinHasEveryStyleAndManagedTextures() {
        Skin skin = ui.skin();
        assertThat(skin.get(Label.LabelStyle.class)).isNotNull();
        assertThat(skin.get("title", Label.LabelStyle.class)).isNotNull();
        assertThat(skin.get("dim", Label.LabelStyle.class)).isNotNull();
        assertThat(skin.get(TextButton.TextButtonStyle.class)).isNotNull();
        assertThat(skin.get("focused", TextButton.TextButtonStyle.class)).isNotNull();
        assertThat(skin.get("default-horizontal", Slider.SliderStyle.class)).isNotNull();
        assertThat(skin.get("default-horizontal", ProgressBar.ProgressBarStyle.class)).isNotNull();
        assertThat(skin.get(Window.WindowStyle.class)).isNotNull();
        for (String d : List.of("white", "panel", "button", "button-over", "button-down", "button-focused",
                "button-disabled", "bar-bg", "bar-fill")) {
            assertThat(skin.getDrawable(d)).as(d).isNotNull();
        }
        List<Texture> textures = new ArrayList<>();
        skin.getAll(Texture.class).values().forEach(textures::add);
        assertThat(textures).isNotEmpty().allMatch(Texture::isManaged);
    }

    @Test
    void menusNavigateWithWrapAndSkipDisabledItems() {
        MenuList menu = menu("Play", "Settings", "Quit");
        assertThat(menu.focused()).isZero();
        nav.set(0f, -1f);   // down
        frame();
        assertThat(menu.focused()).isEqualTo(1);
        nav.setZero();
        frame();
        nav.set(0f, -1f);
        frame();
        nav.setZero();
        frame();
        nav.set(0f, -1f);
        frame();
        assertThat(menu.focused()).as("wraps").isZero();
        nav.setZero();
        frame();

        menu.setDisabled(1, true);
        nav.set(0f, -1f);
        frame();
        assertThat(menu.focused()).as("skips the disabled item").isEqualTo(2);
        nav.setZero();
        frame();

        pressed.add("ui_confirm");
        frame();
        assertThat(log).containsExactly("Quit");
        menu.onBack(() -> log.add("back"));
        pressed.add("ui_back");
        frame();
        assertThat(log).containsExactly("Quit", "back");
    }

    @Test
    void aHeldDirectionRepeats() {
        MenuList menu = menu("A", "B", "C", "D", "E", "F");
        nav.set(0f, -1f);
        for (int i = 0; i < 60; i++) {   // one second held
            frame();
        }
        // first step at once, repeats from 0.35 s every 0.1 s: 1 + 7 steps = index 8 % 6
        assertThat(menu.focused()).isEqualTo(2);
    }

    @Test
    void valueItemsChangeWithLeftRightAndWrapOnConfirm() {
        int[] volume = {80};
        MenuList menu = new MenuList(ui);
        TextButton item = menu.addValue("Music", () -> volume[0], v -> volume[0] = v, 0, 100, 10);
        ui.root().add(menu);
        assertThat(item.getText().toString()).isEqualTo("Music  80");
        nav.set(1f, 0f);
        frame();
        nav.setZero();
        frame();
        nav.set(1f, 0f);
        frame();
        assertThat(volume[0]).as("clamped at max").isEqualTo(100);
        nav.setZero();
        pressed.add("ui_confirm");
        frame();
        assertThat(volume[0]).as("confirm wraps").isZero();
        assertThat(item.getText().toString()).isEqualTo("Music  0");
        assertThatThrownBy(() -> menu.addValue("Bad", () -> 0, v -> { }, 5, 5, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aClickActivatesTheItemUnderThePointer() {
        menu("Play", "Settings", "Quit");
        TextButton settings = findButton("Settings");
        Vector2 centre = settings.localToStageCoordinates(new Vector2(settings.getWidth() / 2f,
                settings.getHeight() / 2f));
        int screenY = (int) (180 - centre.y);
        assertThat(ui.inputProcessor().touchDown((int) centre.x, screenY, 0, 0)).as("consumed by the UI").isTrue();
        ui.inputProcessor().touchUp((int) centre.x, screenY, 0, 0);
        assertThat(log).containsExactly("Settings");
        assertThat(ui.inputProcessor().touchDown(2, 2, 0, 0)).as("empty area: goes to the game").isFalse();
    }

    TextButton findButton(String text) {
        for (com.badlogic.gdx.scenes.scene2d.Actor a : ui.stage().getActors()) {
            TextButton found = find(a, text);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError(text);
    }

    TextButton find(com.badlogic.gdx.scenes.scene2d.Actor actor, String text) {
        if (actor instanceof TextButton b && b.getText().toString().equals(text)) {
            return b;
        }
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group g) {
            for (com.badlogic.gdx.scenes.scene2d.Actor c : g.getChildren()) {
                TextButton found = find(c, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void aDialogTakesTheKeyboardFromTheMenuBelow() {
        MenuList menu = menu("Resume", "Main menu");
        Table dialog = ConfirmDialog.show(ui, "Quit to menu?", "Yes", "No", () -> log.add("yes"));
        assertThat(ui.activeMenu()).isNotSameAs(menu);
        pressed.add("ui_confirm");
        frame();
        assertThat(log).as("No is focused first; the menu below did nothing").isEmpty();
        assertThat(dialog.getStage()).isNull();
        assertThat(ui.activeMenu()).isSameAs(menu);

        ConfirmDialog.show(ui, "Quit to menu?", "Yes", "No", () -> log.add("yes"));
        nav.set(0f, 1f);
        frame();
        nav.setZero();
        pressed.add("ui_confirm");
        frame();
        assertThat(log).containsExactly("yes");

        Table third = ConfirmDialog.show(ui, "Quit?", "Yes", "No", () -> log.add("yes again"));
        pressed.add("ui_back");
        frame();
        assertThat(third.getStage()).as("back answers no").isNull();
        assertThat(log).containsExactly("yes");
    }

    @Test
    void hudCountersChangeTextOnlyWhenTheValueChanges() {
        HudCounter deaths = new HudCounter(ui.skin(), "Deaths", null);
        deaths.setValue(2);
        deaths.setValue(2);
        deaths.setValue(2);
        assertThat(deaths.updates).isEqualTo(1);
        assertThat(deaths.label().getText().toString()).isEqualTo("2");

        HudCounter time = new HudCounter(ui.skin(), null);
        time.setSeconds(65.2f);
        time.setSeconds(65.9f);
        assertThat(time.updates).isEqualTo(1);
        assertThat(time.label().getText().toString()).isEqualTo("1:05");
        time.setSeconds(600f);
        assertThat(time.label().getText().toString()).isEqualTo("10:00");
    }

    @Test
    void hudBarsClampTheirValue() {
        HudBar bar = new HudBar(ui.skin());
        bar.setValue(1.5f);
        assertThat(bar.value()).isEqualTo(1f);
        bar.setValue(-1f);
        assertThat(bar.value()).isZero();
    }

    @Test
    void theUiAreaFollowsTheLayout() {
        ui.layout(400f, 180f, 0, 0, 2400, 1080);
        assertThat(ui.width()).isEqualTo(400f);
        assertThat(ui.height()).isEqualTo(180f);
        assertThat(ui.stage().getViewport().getScreenWidth()).isEqualTo(2400);
    }
}
