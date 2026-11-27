package dev.ember2d.demo.novel;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.assets.Assets;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;
import dev.ember2d.ui.UiSkins;

/** Small UI helpers shared by the novel demo's scenes. */
final class NovelUi {

    private NovelUi() {
    }

    /** The generated pixel skin over the 8 px pixel font, disposed with the scene's UI. */
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
}
