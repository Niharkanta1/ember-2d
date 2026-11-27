package dev.ember2d.demo.shooter;

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
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/** The shooter demo's main menu: Play, Bullet stress test, Quit (desktop); the best score and the version. */
final class MenuScene extends Scene {

    private Input input;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        context.scenes().preload("room");
        Ui ui = ui();
        ShooterUi.useSkin(ui, context.assets());
        TextureAtlas atlas = context.assets().get("atlas.shooter", TextureAtlas.class);
        Table root = ui.root();
        root.add(new Label("Ember2D Shooter", ui.skin(), "title")).padBottom(4f).row();
        Table art = new Table();
        art.add(new Image(atlas.findRegion("player"))).size(16f).padRight(12f);
        art.add(new Image(atlas.findRegion("boss"))).size(48f).padRight(12f);
        art.add(new Image(atlas.findRegion("slime"))).size(16f);
        root.add(art).padBottom(4f).row();
        Table panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(6f, 10f, 6f, 10f);
        MenuList menu = new MenuList(ui);
        menu.add("Play", () -> context.scenes().replace(new RoomScene(new Run()), Transition.fade(0.5f)));
        menu.add("Bullet stress test", () -> context.scenes().replace(new StressScene(), Transition.fade(0.4f)));
        if (context.config().devMode) {   // dev builds: straight to the boss, for testing
            menu.add("Skip to boss (dev)", () -> context.scenes().replace(new BossScene(new Run()),
                    Transition.fade(0.4f)));
        }
        boolean android = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        if (!android) {
            menu.add("Quit", () -> Gdx.app.exit());
        } else {
            menu.onBack(() -> Gdx.app.exit());
        }
        panel.add(menu).growX();
        root.add(panel).minWidth(150f).row();
        int best = context.saves().global().getInt(Run.BEST_KEY, 0);
        root.add(new Label((best > 0 ? "Best " + best + "   " : "") + "v" + ShooterDemo.version(), ui.skin(), "dim"))
                .padTop(4f);
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
