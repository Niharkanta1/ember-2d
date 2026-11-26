package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.Viewport;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.render.RenderMode;
import dev.ember2d.render.RenderTestKit;
import dev.ember2d.render.internal.ScreenLayout;
import dev.ember2d.ui.Insets;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiMode;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Scene UI wiring (F-012, I-220): layout per mode, input routing, lifecycle and config. */
class SceneUiTest {

    final EngineContext.Builder builder = new EngineContext.Builder(new EngineConfig());
    final EngineContext context = builder.build();
    final SceneManager scenes = context.scenes();
    final GameLoop loop = new GameLoop(() -> 16_666_668L, builder.timeState(), scenes::runPhase, 5);

    /** A scene with one button in its UI. */
    final class Menu extends Scene {
        Ui shown;
        TextButton button;

        @Override
        protected void enter(EngineContext ctx) {
            shown = ui();
            button = new TextButton("Play", shown.skin());
            shown.root().add(button).size(60f, 20f);
        }
    }

    @BeforeEach
    void gdx() {
        HeadlessGdx.install();
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(),
                new Class<?>[] {Graphics.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getBackBufferWidth" -> 320;
                    case "getHeight", "getBackBufferHeight" -> 180;
                    default -> method.getReturnType() == int.class ? 0 : method.getReturnType() == float.class ? 0f
                            : method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == long.class ? 0L : null;
                });
    }

    @AfterEach
    void clear() {
        scenes.exitAll();
        HeadlessGdx.clear();
    }

    Ui ui() {
        Ui ui = new Ui(new RenderTestKit().batch, context.input());
        ui.layout(320f, 180f, 0, 0, 320, 180);
        return ui;
    }

    @Test
    void virtualUiSharesTheWorldsScreenRectangle() {
        EngineConfig config = new EngineConfig();
        assertLayout(config, 1280, 720, RenderMode.PIXEL_PERFECT, 320f, 180f, 0, 0, 1280, 720);
        assertLayout(config, 1366, 768, RenderMode.PIXEL_PERFECT, 320f, 180f, 43, 24, 1280, 720);
        assertLayout(config, 2400, 1080, RenderMode.PIXEL_PERFECT_FILL, 400f, 180f, 0, 0, 2400, 1080);
        config.uiMode = UiMode.SCREEN;
        config.uiScale = 2f;
        assertLayout(config, 1366, 768, RenderMode.PIXEL_PERFECT, 683f, 384f, 0, 0, 1366, 768);
    }

    void assertLayout(EngineConfig config, int w, int h, RenderMode mode, float uiW, float uiH, int x, int y, int sw,
            int sh) {
        Ui ui = ui();
        EmberGame.layoutUi(ui, config, ScreenLayout.compute(w, h, 320, 180, mode), w, h, Insets.NONE, 1f);
        Viewport v = ui.stage().getViewport();
        assertThat(new float[] {ui.width(), ui.height()}).containsExactly(uiW, uiH);
        assertThat(new int[] {v.getScreenX(), v.getScreenY(), v.getScreenWidth(), v.getScreenHeight()})
                .as("%dx%d %s", w, h, mode).containsExactly(x, y, sw, sh);
        ui.dispose();
    }

    @Test
    void safeInsetsPadTheUiAndTouchSizesFollowTheDensity() {
        EngineConfig config = new EngineConfig();
        config.uiMode = UiMode.SCREEN;
        Ui ui = ui();
        // 2400 x 1080 phone at density 3, a 90 px cutout on the left: uiScale 0 means "from density".
        EmberGame.layoutUi(ui, config, ScreenLayout.compute(2400, 1080, 320, 180, RenderMode.PIXEL_PERFECT_FILL),
                2400, 1080, new Insets(90f, 0f, 0f, 30f), 3f);
        assertThat(ui.width()).isEqualTo(800f);
        assertThat(ui.safeInsets()).isEqualTo(new Insets(30f, 0f, 0f, 10f));
        assertThat(ui.root().getPadLeft()).isEqualTo(30f);
        assertThat(ui.dp(40f)).as("40 dp at 3 px per dp and 3 px per unit").isEqualTo(40f);
        assertThat(new MenuList(ui).defaults().getMinHeight()).isEqualTo(40f);

        // VIRTUAL: 1366 x 768 has 43 px bars on the sides; a 60 px inset on the left is mostly covered by them.
        config.uiMode = UiMode.VIRTUAL;
        EmberGame.layoutUi(ui, config, ScreenLayout.compute(1366, 768, 320, 180, RenderMode.PIXEL_PERFECT), 1366, 768,
                new Insets(60f, 0f, 0f, 0f), 2f);
        assertThat(ui.safeInsets().left()).isEqualTo((60f - 43f) / 4f);
        assertThat(ui.safeInsets().right()).isZero();
        assertThat(ui.dp(40f)).as("2 px per dp, 4 px per unit").isEqualTo(20f);
        assertThat(new MenuList(ui).defaults().getMinHeight()).as("never below 24 units").isEqualTo(24f);
        ui.dispose();
    }

    @Test
    void theTopScenesUiGetsInputFirstAndIsDisposedOnExit() {
        scenes.setUiFactory(this::ui);
        Menu menu = new Menu();
        scenes.push(menu);
        loop.frame();
        assertThat(scenes.currentUi()).isSameAs(menu.shown);
        menu.shown.stage().act(0f);
        menu.shown.root().validate();

        UiInputRouter router = new UiInputRouter(scenes);
        Vector2 c = menu.button.localToStageCoordinates(new Vector2(30f, 10f));
        assertThat(router.touchDown((int) c.x, (int) (180 - c.y), 0, 0)).as("on the button").isTrue();
        assertThat(router.touchDown(2, 2, 1, 0)).as("elsewhere: the game gets it").isFalse();

        Ui shown = menu.shown;
        scenes.replace(new Scene() {
            @Override
            protected void enter(EngineContext ctx) {
            }
        });
        loop.frame();
        assertThat(scenes.currentUi()).as("a scene without UI has none").isNull();
        assertThat(router.touchDown(2, 2, 0, 0)).isFalse();
        assertThat(shown.stage().getActors()).as("the old scene's UI was disposed").isEmpty();
    }

    @Test
    void scenesWithoutARendererCannotHaveUi() {
        scenes.push(new Scene() {
            @Override
            protected void enter(EngineContext ctx) {
                ui();
            }
        });
        assertThatThrownBy(loop::frame).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("needs a renderer");
    }

    @Test
    void uiConfigIsValidated() {
        EngineConfig config = new EngineConfig();
        config.uiMode = null;
        config.uiScale = -1f;
        EngineConfigException e = catchThrowableOfType(EngineConfigException.class, config::validate);
        assertThat(e.problems()).contains("uiMode must be set (was null)",
                "uiScale must be 0 (from density) or a finite number > 0 (was -1.0)");
    }
}
