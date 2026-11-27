package dev.ember2d.demo.novel;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.core.Log;
import dev.ember2d.input.Input;
import dev.ember2d.kit.novel.NovelDirector;
import dev.ember2d.kit.novel.NovelLine;
import dev.ember2d.kit.novel.NovelSettings;
import dev.ember2d.kit.novel.NovelView;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.SaveResult;
import dev.ember2d.save.SlotMeta;
import dev.ember2d.ui.ConfirmDialog;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * Reads the chapter full-screen with kit-novel's {@link NovelView}. A Menu button (or Escape / Back) pauses: Resume,
 * Save, Load, Title. Saving stores the story, the stage, the line on screen and the backlog in slot 0; loading
 * restores them exactly. The end shows which ending was reached and records it.
 */
final class StoryScene extends Scene {

    private static final Log LOG = Log.get("novel-demo");

    private final SaveData saved;
    private Input input;
    private NovelView view;
    private NovelDirector director;
    private Table pausePanel;
    private boolean paused;
    private boolean justClosed;
    private boolean ended;
    private String ending = "";
    private float seconds;

    /** @param saved the slot to continue from, or null to start the chapter */
    StoryScene(SaveData saved) {
        this.saved = saved;
    }

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        Ui ui = ui();
        NovelUi.useSkin(ui, context.assets());
        view = new NovelView(ui, context.assets(), context.audio(), input, context.renderer().camera(),
                new NovelSettings().load(context.saves().settings()));
        view.setCharacterArea(0.25f, 0.6f);   // pixel-art characters stand on the dialogue box
        view.setOnLine(this::line);
        play(context, saved);
        TextButton menuButton = new TextButton("Menu", ui.skin());
        menuButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openPause();
            }
        });
        menuButton.pack();
        menuButton.setPosition(4f, ui.height() - menuButton.getHeight() - 4f);
        ui.stage().addActor(menuButton);
    }

    private void play(EngineContext context, SaveData from) {
        director = Chapter.director(context, view);
        if (from != null) {
            director.load(from);
            seconds = from.getFloat("seconds", 0f);
        }
        view.play(director, this::finished);
    }

    private void line(NovelLine line) {
        for (String tag : line.tags()) {
            if (tag.startsWith("ending_")) {   // a plain game tag: ending_together, ending_alone
                ending = tag.substring("ending_".length()).trim();
            }
        }
    }

    private void finished() {
        ended = true;
        context().saves().flush();   // read lines and CGs
        if (!ending.isEmpty()) {
            Chapter.recordEnding(context().saves(), ending);
        }
        LOG.info("Chapter finished: ending \"" + ending + "\" after " + (int) seconds + " s");
        Ui ui = ui();
        Table panel = NovelUi.overlay(ui);
        MenuList menu = new MenuList(ui);
        menu.add("Title", () -> context().scenes().replace(new TitleScene(), Transition.fade(0.6f)));
        NovelUi.show(panel, "The End", menu);
        panel.row();
        panel.add(new Label("together".equals(ending) ? "Friends at sunset" : "A quiet evening", ui.skin(), "dim"))
                .padTop(4f);
    }

    @Override
    protected void update(float delta) {
        if (!paused && !ended) {
            seconds += delta;
            view.update(delta);
            if (!justClosed && input.justPressed("ui_back") && ui().activeMenu() == null) {
                openPause();
            }
        }
        justClosed = false;
    }

    // ---- pause ----

    private void openPause() {
        if (paused || ended) {
            return;
        }
        paused = true;
        Ui ui = ui();
        pausePanel = NovelUi.overlay(ui);
        MenuList menu = new MenuList(ui);
        menu.add("Resume", this::closePause);
        menu.add("Save", () -> {
            save();
            closePause();
        });
        if (Chapter.hasSave(context().saves())) {
            menu.add("Load", () -> {
                SaveData data = Chapter.loadSave(context().saves());
                if (data != null) {
                    context().scenes().replace(new StoryScene(data), Transition.fade(0.4f));
                }
            });
        }
        menu.add("Title", () -> ConfirmDialog.show(ui, "Back to the title?", "Yes", "No",
                () -> context().scenes().replace(new TitleScene(), Transition.fade(0.5f))));
        menu.onBack(this::closePause);
        NovelUi.show(pausePanel, "Menu", menu);
    }

    private void closePause() {
        if (!paused) {
            return;
        }
        paused = false;
        justClosed = true;
        pausePanel.getParent().remove();
    }

    private void save() {
        SaveData data = new SaveData();
        director.save(data);
        data.set("seconds", seconds);
        SaveResult result = context().saves().save(Chapter.SLOT, new SlotMeta("story", seconds, "Chapter 1"), data);
        context().saves().flush();
        if (!result.ok()) {
            LOG.error("Saving failed: " + result.error());
        }
    }

    @Override
    protected void exit() {
        context().saves().flush();
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
