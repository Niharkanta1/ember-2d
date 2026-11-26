package dev.ember2d.demo.platformer;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.input.Input;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * The demo's main menu (I-220): the title over the level's background, the frog, and Play, Settings and Quit (desktop
 * only; on Android, Back leaves the app). Menus are driven by the {@code ui} input context.
 */
final class MenuScene extends Scene {

    private Input input;
    private Table panel;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        context.audio().playMusic("music.level", 0.5f);
        Ui ui = ui();
        DemoUi.useSkin(ui, context.assets());
        Table root = ui.root();
        root.setBackground(new TiledDrawable(new TextureRegion(context.assets().get("tex.background", Texture.class))));
        root.add(new Label("Ember2D Platformer", ui.skin(), "title")).padBottom(6f).row();
        TextureRegion frog = new TextureRegion(context.assets().get("tex.frog_idle", Texture.class), 0, 0, 32, 32);
        root.add(new Image(frog)).size(32f, 32f).padBottom(6f).row();
        panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(8f, 10f, 8f, 10f);
        root.add(panel).minWidth(130f);
        showMain(context, ui);
    }

    private void showMain(EngineContext context, Ui ui) {
        MenuList menu = new MenuList(ui);
        menu.add("Play", () -> context.scenes().replace(new DemoScene()));
        menu.add("Settings", () -> showSettings(context, ui));
        boolean android = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        if (!android) {
            menu.add("Quit", () -> Gdx.app.exit());
        } else {
            menu.onBack(() -> Gdx.app.exit());   // Android: Back leaves the app
        }
        panel.clearChildren();
        panel.add(menu).growX();
    }

    private void showSettings(EngineContext context, Ui ui) {
        DemoUi.show(panel, "Settings", DemoUi.settings(ui, context.audio(), () -> showMain(context, ui)));
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
