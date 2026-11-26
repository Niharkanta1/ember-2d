package dev.ember2d.demo.platformer;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;
import dev.ember2d.input.Input;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.TouchLayoutEditor;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiSkins;

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
     * Music and sound volumes in steps of 10%, "Edit controls" while on-screen controls are shown (it reopens the
     * settings with {@code reopen} when done), and Back.
     */
    static MenuList settings(Ui ui, Audio audio, Input input, Runnable back, Runnable reopen) {
        MenuList menu = new MenuList(ui);
        menu.addValue("Music", () -> percent(audio.volume(AudioCategory.MUSIC)),
                v -> audio.setVolume(AudioCategory.MUSIC, v / 100f), 0, 100, 10);
        menu.addValue("Sounds", () -> percent(audio.volume(AudioCategory.SFX)),
                v -> audio.setVolume(AudioCategory.SFX, v / 100f), 0, 100, 10);
        if (input.touchControlsActive()) {
            menu.add("Edit controls", () -> TouchLayoutEditor.open(ui, input, reopen));
        }
        menu.add("Back", back);
        menu.onBack(back);
        return menu;
    }

    private static int percent(float volume) {
        return Math.round(volume * 100f);
    }
}
