package dev.ember2d.demo.gold;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.audio.Audio;
import dev.ember2d.input.Input;
import dev.ember2d.save.SaveData;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * The title screen (F-029), ported from the original's {@code MainMenuScreen}: the start-screen art with the menu in the
 * bottom-right corner: New game, Continue (from the level reached, when a campaign is in progress), Settings and Exit
 * (desktop; on Android, Back leaves the app). The best score is shown above the menu.
 */
final class TitleScene extends Scene {

    private static boolean settingsApplied;
    private Input input;

    @Override
    protected void enter(EngineContext context) {
        if (!settingsApplied) {
            settingsApplied = true;
            GoldUi.applySaved(context);
        }
        input = context.input();
        input.pushContext("ui");
        context.audio().playMusic("music.dungeon", 0.5f);
        Ui ui = ui();
        GoldUi.useSkin(ui, context.assets());
        Image background = new Image(context.assets().get("tex.start-screen", Texture.class));
        background.setFillParent(true);
        ui.stage().getRoot().addActorAt(0, background);
        showMain(context, ui);
    }

    private void showMain(EngineContext context, Ui ui) {
        Audio audio = context.audio();
        SaveData saved = context.saves().global();
        MenuList menu = GoldUi.menu(ui);
        GoldUi.add(menu, "New game", audio, () -> play(context, Campaign.fresh()));
        boolean resume = Campaign.canResume(saved);
        GoldUi.add(menu, resume ? "Continue  " + saved.getInt(Campaign.REACHED, 1) + "/" + Campaign.LEVELS
                : "Continue", audio, () -> play(context, Campaign.resume(saved)));
        if (!resume) {
            menu.setDisabled(1, true);
        }
        GoldUi.add(menu, "Settings", audio, () -> showSettings(context, ui));
        if (!GoldUi.android()) {
            GoldUi.add(menu, "Exit", audio, () -> Gdx.app.exit());
        }
        menu.onBack(() -> Gdx.app.exit());
        Table root = ui.root();
        root.clearChildren();
        root.setBackground((com.badlogic.gdx.scenes.scene2d.utils.Drawable) null);
        root.bottom().right().pad(5f);
        int best = Campaign.best(saved);
        if (best > 0) {
            root.add(GoldUi.label(ui, "Best $ " + best, Hud.CREAM)).right().padBottom(2f).row();
        }
        root.add(GoldUi.panel(ui, menu)).right().minWidth(78f).row();
        String hint = GoldUi.android() ? "" : input.gamepadConnected() ? "Sticks move and aim" : "WASD move  Mouse aim";
        if (!hint.isEmpty()) {
            Table back = new Table(ui.skin());   // readable over the art
            back.setBackground(ui.skin().newDrawable("white", GoldUi.DARK.r, GoldUi.DARK.g, GoldUi.DARK.b, 0.8f));
            back.add(GoldUi.label(ui, hint, Hud.TAN)).pad(1f, 2f, 0f, 2f);
            root.add(back).padTop(3f).align(Align.right);
        }
    }

    private void showSettings(EngineContext context, Ui ui) {
        Table root = GoldUi.screen(ui, "Settings", Hud.CREAM);
        root.add(GoldUi.panel(ui, GoldUi.settings(ui, context, () -> showMain(context, ui),
                () -> showSettings(context, ui)))).minWidth(110f).row();
        root.add(GoldUi.label(ui, "Difficulty applies next level", Hud.MAROON)).padTop(6f);
    }

    private void play(EngineContext context, Campaign campaign) {
        context.scenes().replace(new LevelScene(campaign), Transition.fade(0.5f));
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
