package dev.ember2d.demo.gold;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.audio.Audio;
import dev.ember2d.input.Input;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * The end of a run (F-029), ported from the original's {@code EndScreen}: "YOU DIED" after a death (Restart the level,
 * or the main menu) or "VICTORY!" after the last level (Play again, or the main menu), with the gold and kills. After a
 * victory the score is checked against the best.
 */
final class EndScene extends Scene {

    private final Campaign campaign;
    private final boolean victory;
    private final boolean newBest;
    private Input input;

    /**
     * @param newBest whether the campaign's score beat the best one (victory only)
     */
    EndScene(Campaign campaign, boolean victory, boolean newBest) {
        this.campaign = campaign;
        this.victory = victory;
        this.newBest = newBest;
    }

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        Ui ui = ui();
        GoldUi.useSkin(ui, context.assets());
        Audio audio = context.audio();
        Table root = GoldUi.screen(ui, null, Hud.CREAM);
        if (victory) {
            root.add(GoldUi.title(ui, "VICTORY!", Hud.CREAM)).padBottom(4f).row();
            root.add(GoldUi.label(ui, "THE GOLD IS YOURS", Hud.TAN)).padBottom(8f).row();
        } else {
            root.add(GoldUi.title(ui, "# YOU DIED #", Hud.MAROON)).padBottom(4f).row();
            root.add(GoldUi.label(ui, "LEVEL " + campaign.level + "/" + Campaign.LEVELS, Hud.TAN)).padBottom(8f).row();
        }
        root.add(GoldUi.label(ui, "$ GOLD  " + campaign.gold, Hud.TAN)).padBottom(2f).row();
        root.add(GoldUi.label(ui, "# KILLS " + campaign.kills, Hud.TAN)).padBottom(newBest ? 2f : 10f).row();
        if (newBest) {
            root.add(GoldUi.label(ui, "NEW BEST!", Hud.CREAM)).padBottom(8f).row();
        }
        MenuList menu = GoldUi.menu(ui);
        if (victory) {
            GoldUi.add(menu, "Play again", audio, () -> go(context, new LevelScene(Campaign.fresh())));
        } else {
            GoldUi.add(menu, "Restart", audio, () -> {
                campaign.retryLevel();
                go(context, new LevelScene(campaign));
            });
        }
        GoldUi.add(menu, "Main menu", audio, () -> go(context, new TitleScene()));
        menu.onBack(() -> go(context, new TitleScene()));
        root.add(GoldUi.panel(ui, menu)).minWidth(80f);
    }

    private void go(EngineContext context, Scene next) {
        context.scenes().replace(next, Transition.fade(0.5f));
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
