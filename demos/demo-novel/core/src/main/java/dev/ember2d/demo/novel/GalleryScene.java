package dev.ember2d.demo.novel;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Scaling;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.input.Input;
import dev.ember2d.kit.novel.NovelMemory;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/** The CG gallery: pictures unlocked by reading are shown (tap to view full screen); locked ones show "?". */
final class GalleryScene extends Scene {

    private Input input;

    @Override
    protected void enter(EngineContext context) {
        input = context.input();
        input.pushContext("ui");
        Ui ui = ui();
        NovelUi.useSkin(ui, context.assets());
        NovelMemory memory = Chapter.memory(context);
        Table root = ui.root();
        root.add(new Label("Gallery", ui.skin(), "title")).padBottom(6f).row();
        Table grid = new Table(ui.skin());
        for (String cg : Chapter.CGS) {
            Table frame = new Table(ui.skin());
            frame.setBackground("panel");
            frame.pad(3f);
            if (memory.isUnlocked(cg)) {
                Image thumb = new Image(context.assets().region(cg));
                thumb.setScaling(Scaling.fit);
                frame.add(thumb).size(128f, 72f);
                frame.setTouchable(Touchable.enabled);
                frame.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        showFull(context, cg);
                    }
                });
            } else {
                frame.add(new Label("?", ui.skin(), "title")).size(128f, 72f).center();
            }
            grid.add(frame).pad(4f);
        }
        root.add(grid).row();
        root.add(new Label(memory.unlockedCgs().size() + "/" + Chapter.CGS.length + " unlocked", ui.skin(), "dim"))
                .padTop(4f).row();
        MenuList menu = new MenuList(ui);
        menu.add("Back", () -> context.scenes().replace(new TitleScene(), Transition.fade(0.3f)));
        menu.onBack(() -> context.scenes().replace(new TitleScene(), Transition.fade(0.3f)));
        root.add(menu).minWidth(100f).padTop(6f);
    }

    private void showFull(EngineContext context, String cg) {
        Image full = new Image(context.assets().region(cg));
        full.setScaling(Scaling.fit);
        full.setFillParent(true);
        full.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                full.remove();
            }
        });
        ui().stage().addActor(full);
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }
}
