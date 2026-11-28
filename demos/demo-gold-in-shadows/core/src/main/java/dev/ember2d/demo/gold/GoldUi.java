package dev.ember2d.demo.gold;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.utils.Align;
import dev.ember2d.app.EngineContext;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;
import dev.ember2d.input.Input;
import dev.ember2d.save.SaveData;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.RebindMenu;
import dev.ember2d.ui.TouchLayoutEditor;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiPalette;
import dev.ember2d.ui.UiSkins;
import java.util.List;
import java.util.function.Supplier;

/**
 * The game's menus in the original's style (F-029), ported from its {@code UiStyle} and {@code MenuPanel}: the pixel
 * font, a dark panel with a one-pixel maroon border, options in tan and the chosen one in cream behind a "&gt;" cursor.
 * Menus are Ember2D {@link MenuList}s, so the keyboard, the mouse, touch and the gamepad all drive them, and
 * {@link RebindMenu} (Settings &gt; Controls) gets the same look. Also: the settings menu, and the desktop display
 * options (fullscreen, vsync, the FPS counter) kept in the settings store.
 */
final class GoldUi {

    static final Color DARK = Color.valueOf("181830");
    static final String DIFFICULTY = "gold.difficulty";
    static final String FULLSCREEN = "gold.fullscreen";
    static final String VSYNC = "gold.vsync";
    static final String SHOW_FPS = "gold.showFps";
    /** The original's menu row height in pixels. */
    static final float ROW = 11f;

    /** What the player can rebind in Settings &gt; Controls. */
    static final List<RebindMenu.Entry> CONTROLS = List.of(
            RebindMenu.axis("move", "up", "Up"), RebindMenu.axis("move", "down", "Down"),
            RebindMenu.axis("move", "left", "Left"), RebindMenu.axis("move", "right", "Right"),
            RebindMenu.action("fire", "Fire"), RebindMenu.action("reload", "Reload"),
            RebindMenu.action("interact", "Open"), RebindMenu.action("pause", "Pause"));

    private GoldUi() {
    }

    static boolean android() {
        return Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
    }

    /** The game's skin over the original pixel font, disposed with the scene's UI. */
    static void useSkin(Ui ui, Assets assets) {
        BitmapFont font = assets.get("font.pixel", BitmapFont.class);
        UiPalette palette = UiPalette.standard();
        palette.panel.set(DARK.r, DARK.g, DARK.b, 0.92f);
        palette.panelBorder.set(Hud.MAROON);
        palette.text.set(Color.WHITE);   // labels are tinted per use; "white" stays white
        palette.accent.set(Hud.CREAM);
        palette.dim.set(Hud.TAN);
        palette.disabled.set(Hud.MAROON);
        palette.button.set(DARK);
        palette.buttonOver.set(DARK);
        palette.buttonDown.set(DARK);
        Skin skin = UiSkins.pixel(font, palette);
        TextButton.TextButtonStyle plain = new TextButton.TextButtonStyle(new Cursor(font, null), null, null, font);
        plain.fontColor = new Color(Hud.TAN);
        plain.disabledFontColor = new Color(Hud.MAROON);
        plain.overFontColor = new Color(Hud.TAN);
        skin.add("default", plain);
        TextButton.TextButtonStyle focused = new TextButton.TextButtonStyle(plain);
        focused.up = new Cursor(font, Hud.CREAM);
        focused.over = focused.up;
        focused.fontColor = new Color(Hud.CREAM);
        focused.overFontColor = new Color(Hud.CREAM);
        skin.add("focused", focused);
        ui.setSkin(skin, true);
    }

    /** A menu with the original's row height (taller on phones, so rows are easy to tap). */
    static MenuList menu(Ui ui) {
        MenuList menu = new MenuList(ui);
        menu.defaults().minHeight(Math.max(ROW, ui.dp(MenuList.MIN_TOUCH_DP) * 0.6f)).pad(0f);
        return menu;
    }

    /** Adds an option, left-aligned behind the cursor, that plays the menu sound. */
    static TextButton add(MenuList menu, String label, Audio audio, Runnable action) {
        TextButton button = menu.add(label, () -> {
            audio.play("sfx.menu");
            action.run();
        });
        button.getLabel().setAlignment(Align.left);
        return button;
    }

    /** Adds an option whose text is re-read after every press (toggles). */
    static TextButton toggle(MenuList menu, Supplier<String> label, Audio audio, Runnable action) {
        TextButton[] button = new TextButton[1];
        button[0] = add(menu, label.get(), audio, () -> {
            action.run();
            button[0].setText(label.get());
        });
        return button[0];
    }

    /** The dark full-screen background with a title, like the original's settings and end screens. */
    static Table screen(Ui ui, String title, Color titleColor) {
        Table root = ui.root();
        root.clearChildren();
        root.setBackground(ui.skin().newDrawable("white", DARK));
        root.center();
        if (title != null) {
            root.add(title(ui, title, titleColor)).padBottom(8f).row();
        }
        return root;
    }

    /** A modal overlay over the game (the original's pause screen: the dark colour at 75%). */
    static Table overlay(Ui ui) {
        Table overlay = new Table(ui.skin());
        overlay.setFillParent(true);
        overlay.setTouchable(Touchable.enabled);
        overlay.setBackground(ui.skin().newDrawable("white", DARK.r, DARK.g, DARK.b, 0.75f));
        ui.stage().addActor(overlay);
        return overlay;
    }

    /** A double-size title label. */
    static Label title(Ui ui, String text, Color color) {
        Label label = label(ui, text, color);
        label.setFontScale(2f);
        return label;
    }

    static Label label(Ui ui, String text, Color color) {
        Label label = new Label(text, ui.skin());
        label.setColor(color);
        return label;
    }

    /** A bordered panel around a menu. */
    static Table panel(Ui ui, MenuList menu) {
        Table panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(4f, 4f, 3f, 8f);
        panel.add(menu).growX();
        return panel;
    }

    // ---- settings ----

    /** Reads the saved difficulty and display options and applies them (once, at start). */
    static void applySaved(EngineContext context) {
        SaveData settings = context.saves().settings();
        String name = settings.getString(DIFFICULTY, Difficulty.NORMAL.name());
        for (Difficulty d : Difficulty.values()) {
            if (d.name().equals(name)) {
                GoldGame.setDifficulty(d);
            }
        }
        if (!android() && Gdx.graphics != null) {
            Gdx.graphics.setVSync(settings.getBool(VSYNC, true));
            if (settings.getBool(FULLSCREEN, false)) {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
        }
    }

    static boolean showFps(EngineContext context) {
        return context.saves().settings().getBool(SHOW_FPS, false);
    }

    /**
     * The settings menu (the original's, plus volumes and controls): difficulty (it applies from the next level, as in
     * the original), music and sound volumes, Controls (rebinding; on touch screens, moving the on-screen controls),
     * and on desktop fullscreen, vsync and the FPS counter. Every change is saved; Back runs {@code back}.
     */
    static MenuList settings(Ui ui, EngineContext context, Runnable back, Runnable reopen) {
        Audio audio = context.audio();
        Input input = context.input();
        SaveData settings = context.saves().settings();
        MenuList menu = menu(ui);
        toggle(menu, () -> "Difficulty " + GoldGame.difficulty().label, audio, () -> {
            GoldGame.setDifficulty(GoldGame.difficulty().next());
            settings.set(DIFFICULTY, GoldGame.difficulty().name());
        });
        menu.addValue("Music", () -> Math.round(audio.volume(AudioCategory.MUSIC) * 100f),
                v -> audio.setVolume(AudioCategory.MUSIC, v / 100f), 0, 100, 10).getLabel().setAlignment(Align.left);
        menu.addValue("Sound", () -> Math.round(audio.volume(AudioCategory.SFX) * 100f),
                v -> audio.setVolume(AudioCategory.SFX, v / 100f), 0, 100, 10).getLabel().setAlignment(Align.left);
        if (!android() || input.gamepadConnected()) {
            add(menu, "Controls", audio, () -> openControls(ui, context));
        }
        if (input.touchControlsActive()) {
            add(menu, "Move buttons", audio, () -> TouchLayoutEditor.open(ui, input, () -> {
                context.saveSettings();
                reopen.run();
            }));
        }
        if (!android() && Gdx.graphics != null) {
            toggle(menu, () -> "Fullscreen " + onOff(Gdx.graphics.isFullscreen()), audio, () -> {
                boolean full = !Gdx.graphics.isFullscreen();
                if (full) {
                    Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
                } else {
                    Gdx.graphics.setWindowedMode(1280, 720);
                }
                settings.set(FULLSCREEN, full);
            });
            toggle(menu, () -> "Vsync " + onOff(settings.getBool(VSYNC, true)), audio, () -> {
                boolean on = !settings.getBool(VSYNC, true);
                settings.set(VSYNC, on);
                Gdx.graphics.setVSync(on);
            });
            toggle(menu, () -> "Show FPS " + onOff(settings.getBool(SHOW_FPS, false)), audio,
                    () -> settings.set(SHOW_FPS, !settings.getBool(SHOW_FPS, false)));
        }
        Runnable saveAndBack = () -> {
            context.saveSettings();
            back.run();
        };
        add(menu, "Back", audio, saveAndBack);
        menu.onBack(saveAndBack);
        return menu;
    }

    /** The rebinding screen, with the original's compact rows (the engine's are sized for taller screens). */
    static RebindMenu openControls(Ui ui, EngineContext context) {
        RebindMenu controls = RebindMenu.open(ui, CONTROLS, context::saveSettings);
        compact(ui.activeMenu(), ui);
        return controls;
    }

    /** Makes an existing menu's rows the original's height and left-aligned. */
    static void compact(MenuList menu, Ui ui) {
        if (menu == null) {
            return;
        }
        float height = Math.max(ROW, ui.dp(MenuList.MIN_TOUCH_DP) * 0.6f);
        for (com.badlogic.gdx.scenes.scene2d.ui.Cell<?> cell : menu.getCells()) {
            cell.minHeight(height).pad(0f);
            if (cell.getActor() instanceof TextButton button) {
                button.getLabel().setAlignment(Align.left);
            }
        }
        menu.invalidateHierarchy();
    }

    static String onOff(boolean value) {
        return value ? "On" : "Off";
    }

    /** The button background: room for the cursor on the left, and the "&gt;" itself on the chosen option. */
    private static final class Cursor extends BaseDrawable {
        private final BitmapFont font;
        private final Color color;

        Cursor(BitmapFont font, Color color) {
            this.font = font;
            this.color = color;
            setLeftWidth(9f);
            setRightWidth(1f);
        }

        @Override
        public void draw(Batch batch, float x, float y, float width, float height) {
            if (color == null) {
                return;
            }
            Color old = font.getColor().cpy();
            font.setColor(color);
            font.draw(batch, ">", x + 1f, y + (height + font.getCapHeight()) / 2f);
            font.setColor(old);
        }
    }
}
