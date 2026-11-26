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
    private Label title;
    private Image frog;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        context.audio().playMusic("music.level", 0.5f);
        Ui ui = ui();
        DemoUi.useSkin(ui, context.assets());
        ui.root().setBackground(new TiledDrawable(new TextureRegion(context.assets().get("tex.background",
                Texture.class))));
        title = new Label("Ember2D Platformer", ui.skin(), "title");
        frog = new Image(new TextureRegion(context.assets().get("tex.frog_idle", Texture.class), 0, 0, 32, 32));
        panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(8f, 10f, 8f, 10f);
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
        layout(ui, true);
    }

    /** The title and frog over the panel, or the panel alone (the settings need the room on small screens). */
    private void layout(Ui ui, boolean header) {
        Table root = ui.root();
        root.clearChildren();
        if (header) {
            root.add(title).padBottom(6f).row();
            root.add(frog).size(32f, 32f).padBottom(6f).row();
        }
        root.add(panel).minWidth(130f);
    }

    private void showSettings(EngineContext context, Ui ui) {
        DemoUi.show(panel, "Settings", DemoUi.settings(ui, context,
                () -> showMain(context, ui), () -> showSettings(context, ui)));
        layout(ui, false);
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
