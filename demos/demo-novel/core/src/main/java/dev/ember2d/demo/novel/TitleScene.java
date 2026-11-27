package dev.ember2d.demo.novel;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Scaling;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.input.Input;
import dev.ember2d.kit.novel.NovelSettings;
import dev.ember2d.save.SaveData;
import dev.ember2d.ui.ConfirmDialog;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * The novel demo's title screen: Start, Continue (with a save), Gallery, Settings (text speed, auto delay, skip
 * unread), Quit (desktop), with the endings seen and the version.
 */
final class TitleScene extends Scene {

    private Input input;
    private Table panel;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        Ui ui = ui();
        NovelUi.useSkin(ui, context.assets());
        Image background = new Image(context.assets().region("bg.meadow"));
        background.setScaling(Scaling.fill);
        background.setFillParent(true);
        ui.stage().getRoot().addActorAt(0, background);
        Table root = ui.root();
        root.add(new Label("Kero and the", ui.skin(), "title")).row();
        root.add(new Label("Lost Cherries", ui.skin(), "title")).padBottom(6f).row();
        panel = new Table(ui.skin());
        panel.setBackground("panel");
        panel.pad(6f, 10f, 6f, 10f);
        root.add(panel).minWidth(150f).row();
        root.add(new Label("Endings " + Chapter.endingsSeen(context.saves()) + "/" + Chapter.ENDINGS.length + "   v"
                + NovelDemo.version(), ui.skin(), "dim")).padTop(4f);
        showMain(context);
    }

    private void showMain(EngineContext context) {
        Ui ui = ui();
        panel.clearChildren();
        MenuList menu = new MenuList(ui);
        boolean saved = Chapter.hasSave(context.saves());
        if (saved) {
            menu.add("Continue", () -> {
                SaveData data = Chapter.loadSave(context.saves());
                if (data == null) {
                    ConfirmDialog.show(ui, "Save unreadable. Start?", "Yes", "No", () -> start(context, null));
                    return;
                }
                start(context, data);
            });
        }
        menu.add("Start", () -> start(context, null));
        menu.add("Gallery", () -> context.scenes().replace(new GalleryScene(), Transition.fade(0.3f)));
        menu.add("Settings", () -> showSettings(context));
        boolean android = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        if (!android) {
            menu.add("Quit", () -> Gdx.app.exit());
        } else {
            menu.onBack(() -> Gdx.app.exit());
        }
        panel.add(menu).growX();
    }

    private void showSettings(EngineContext context) {
        Ui ui = ui();
        NovelSettings settings = new NovelSettings().load(context.saves().settings());
        panel.clearChildren();
        MenuList menu = new MenuList(ui);
        menu.addValue("Text speed", () -> (int) settings.textSpeed, v -> settings.textSpeed = v, 0, 100, 10);
        menu.addValue("Auto wait", () -> Math.round(settings.autoDelay), v -> settings.autoDelay = v, 1, 5, 1);
        TextButton[] skip = new TextButton[1];
        skip[0] = menu.add(skipLabel(settings), () -> {
            settings.skipUnread = !settings.skipUnread;
            skip[0].setText(skipLabel(settings));
        });
        Runnable back = () -> {
            settings.save(context.saves().settings());
            context.saves().flush();
            showMain(context);
        };
        menu.add("Back", back);
        menu.onBack(back);
        panel.add(new Label("Settings", ui.skin(), "title")).padBottom(4f).row();
        panel.add(menu).growX();
    }

    private static String skipLabel(NovelSettings settings) {
        return settings.skipUnread ? "Skip: all text" : "Skip: read only";
    }

    private void start(EngineContext context, SaveData saved) {
        context.scenes().replace(new StoryScene(saved), Transition.fade(0.6f));
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
