package dev.ember2d.demo.platformer;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import dev.ember2d.app.EngineContext;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;
import dev.ember2d.input.Input;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.RebindMenu;
import dev.ember2d.ui.TouchLayoutEditor;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiSkins;
import java.util.List;

/** UI pieces shared by the demo's scenes (I-220): the pixel skin, panels and the settings menu. */
final class DemoUi {

    private DemoUi() {
    }

    /** The demo's skin: the generated pixel skin over the 8 px pixel font, disposed with the scene's UI. */
    static void useSkin(Ui ui, Assets assets) {
        ui.setSkin(UiSkins.pixel(assets.get("font.pixel", BitmapFont.class)), true);
    }

    /** A modal overlay with a centred panel; returns the panel (the overlay is its parent). */
    static Table overlay(Ui ui) {
        Table overlay = new Table(ui.skin());
        overlay.setFillParent(true);
        overlay.setTouchable(Touchable.enabled);
        Table panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(8f, 10f, 8f, 10f);
        overlay.add(panel).minWidth(150f);
        ui.stage().addActor(overlay);
        return panel;
    }

    /** Replaces a panel's content with a title and a menu. */
    static void show(Table panel, String title, MenuList menu) {
        panel.clearChildren();
        panel.add(new Label(title, panel.getSkin(), "title")).padBottom(8f).row();
        panel.add(menu).growX();
    }

    /**
     * Music and sound volumes in steps of 10%; while on-screen controls are shown, Vibration on/off and "Edit
     * controls" (it reopens the settings with {@code reopen} when done); and Back. Leaving the menu saves the settings
     * (I-230).
     */
    /** What the player can rebind in Settings > Controls (I-535). */
    static final List<RebindMenu.Entry> CONTROLS = List.of(
            RebindMenu.action("jump", "Jump"), RebindMenu.action("dash", "Dash"), RebindMenu.action("pause", "Pause"),
            RebindMenu.axis("move", "left", "Left"), RebindMenu.axis("move", "right", "Right"),
            RebindMenu.axis("move", "up", "Up"), RebindMenu.axis("move", "down", "Down"));

    static MenuList settings(Ui ui, EngineContext context, Runnable back, Runnable reopen) {
        Audio audio = context.audio();
        Input input = context.input();
        Runnable saveAndBack = () -> {
            context.saveSettings();
            back.run();
        };
        MenuList menu = new MenuList(ui);
        menu.addValue("Music", () -> percent(audio.volume(AudioCategory.MUSIC)),
                v -> audio.setVolume(AudioCategory.MUSIC, v / 100f), 0, 100, 10);
        menu.addValue("Sounds", () -> percent(audio.volume(AudioCategory.SFX)),
                v -> audio.setVolume(AudioCategory.SFX, v / 100f), 0, 100, 10);
        if (input.touchControlsActive()) {
            TextButton[] vibration = new TextButton[1];
            vibration[0] = menu.add(vibrationLabel(input), () -> {
                input.setHapticsEnabled(!input.hapticsEnabled());
                vibration[0].setText(vibrationLabel(input));
                input.vibrate(40);   // a sample buzz when switched on
            });
            menu.add("Edit controls", () -> TouchLayoutEditor.open(ui, input, () -> {
                context.saveSettings();
                reopen.run();
            }));
        }
        boolean android = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        if (!android || input.gamepadConnected()) {   // something to rebind: a keyboard or a pad (I-535)
            menu.add("Controls", () -> RebindMenu.open(ui, CONTROLS, context::saveSettings));
        }
        menu.add("Back", saveAndBack);
        menu.onBack(saveAndBack);
        return menu;
    }

    private static String vibrationLabel(Input input) {
        return "Vibration  " + (input.hapticsEnabled() ? "On" : "Off");
    }

    private static int percent(float volume) {
        return Math.round(volume * 100f);
    }
}
