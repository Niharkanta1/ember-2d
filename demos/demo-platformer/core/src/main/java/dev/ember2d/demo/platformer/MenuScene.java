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
import dev.ember2d.app.Transition;
import dev.ember2d.input.Input;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * The demo's main menu (I-220): the title over the level's background, the frog, and Play, Settings and Quit (desktop
 * only; on Android, Back leaves the app). Play opens the level list (I-250): unlocked levels with their best times,
 * locked ones disabled. The game version is in the top right corner. Menus are driven by the {@code ui} input
 * context.
 */
final class MenuScene extends Scene {

    private Input input;
    private Table panel;
    private Label title;
    private Image frog;
    private Progress progress;
    private Label version;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        progress = Progress.load(context.saves());
        // The newest unlocked level loads while the player reads the menu (I-240).
        context.scenes().preload(Levels.get(progress.unlocked()).group());
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
        menu.add("Play", () -> showLevels(context, ui));
        menu.add("Settings", () -> showSettings(context, ui));
        menu.add("Crates", () -> context.scenes().replace(new CratesScene(), Transition.fade(0.4f)));   // Box2D (I-500)
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

    private void showLevels(EngineContext context, Ui ui) {
        MenuList menu = new MenuList(ui);
        for (Levels.Level level : Levels.ALL) {
            int n = level.number();
            float best = context.saves().global().getFloat(level.bestTimeKey(), -1f);
            String label = n + " " + level.title() + (best >= 0f ? "  " + LevelSystem.clock(best) : "");
            menu.add(label, () -> context.scenes().replace(new DemoScene(n), Transition.fade(0.5f)));
            if (!progress.isUnlocked(n)) {
                menu.setDisabled(n - 1, true);
            }
        }
        menu.add("Back", () -> showMain(context, ui));
        menu.onBack(() -> showMain(context, ui));
        menu.focus(progress.unlocked() - 1);
        DemoUi.show(panel, "Levels", menu);
        layout(ui, false);   // the list needs the room, like the settings
    }

    /** The title and frog over the panel, or the panel alone (the settings need the room on small screens). */
    private void layout(Ui ui, boolean header) {
        Table root = ui.root();
        root.clearChildren();
        if (header) {
            root.add(title).padBottom(2f).row();   // tight: the main menu has four entries (I-500)
            root.add(frog).size(32f, 32f).padBottom(2f).row();
        }
        root.add(panel).minWidth(130f).row();
        Label version = new Label("v" + PlatformerDemo.version(), ui.skin());
        version.setColor(1f, 1f, 1f, 0.6f);
        ui.stage().addActor(version);   // replaced on every layout
        if (this.version != null) {
            this.version.remove();
        }
        this.version = version;
        version.setPosition(ui.stage().getWidth() - version.getPrefWidth() - 4f,
                ui.stage().getHeight() - version.getPrefHeight() - 3f);
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
