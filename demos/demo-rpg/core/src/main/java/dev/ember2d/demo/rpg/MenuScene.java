package dev.ember2d.demo.rpg;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.input.Input;
import dev.ember2d.ui.ConfirmDialog;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/** The RPG demo's main menu: Continue (with a save), New game, Quit (desktop), and the version. */
final class MenuScene extends Scene {

    private Input input;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        context.scenes().preload("town");
        Ui ui = ui();
        RpgUi.useSkin(ui, context.assets());
        TextureAtlas atlas = context.assets().get("atlas.rpg", TextureAtlas.class);
        Table root = ui.root();
        root.add(new Label("Ember2D RPG", ui.skin(), "title")).padBottom(4f).row();
        Table art = new Table();
        art.add(new Image(atlas.findRegion("knight"))).size(32f).padRight(10f);
        art.add(new Image(atlas.findRegion("goblin"))).size(32f).padRight(10f);
        art.add(new Image(atlas.findRegion("slime"))).size(32f);
        root.add(art).padBottom(4f).row();
        Table panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(6f, 10f, 6f, 10f);
        MenuList menu = new MenuList(ui);
        boolean saved = Game.hasSave(context.saves());
        if (saved) {
            menu.add("Continue", () -> {
                Game game = Game.continueSaved(context);
                if (game == null) {
                    ConfirmDialog.show(ui, "Save unreadable. New game?", "Yes", "No",
                            () -> newGame(context));
                    return;
                }
                context.scenes().replace(new AreaScene(game), Transition.fade(0.5f));
            });
        }
        menu.add("New game", () -> {
            if (saved) {
                ConfirmDialog.show(ui, "Start over? This replaces the save.", "Yes", "No",
                        () -> newGame(context));
            } else {
                newGame(context);
            }
        });
        boolean android = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        if (!android) {
            menu.add("Quit", () -> Gdx.app.exit());
        } else {
            menu.onBack(() -> Gdx.app.exit());
        }
        panel.add(menu).growX();
        root.add(panel).minWidth(150f).row();
        root.add(new Label("v" + RpgDemo.version(), ui.skin(), "dim")).padTop(4f);
    }

    private static void newGame(EngineContext context) {
        context.scenes().replace(new AreaScene(Game.start(context)), Transition.fade(0.5f));
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
