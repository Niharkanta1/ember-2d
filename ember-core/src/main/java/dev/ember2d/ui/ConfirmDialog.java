package dev.ember2d.ui;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.core.PublicApi;
import java.util.Objects;

/**
 * A modal question with two choices (F-012, I-220), above everything else in the UI. It takes the keyboard from the
 * menu below until it closes; {@code ui_back} answers no. The "no" choice is focused first.
 */
@PublicApi
public final class ConfirmDialog {

    private ConfirmDialog() {
    }

    /** Shows the dialog; returns its root actor (already on the stage). */
    public static Table show(Ui ui, String message, String yes, String no, Runnable onYes) {
        Objects.requireNonNull(onYes, "onYes");
        Skin skin = ui.skin();
        Table overlay = new Table(skin);
        overlay.setFillParent(true);
        overlay.setTouchable(Touchable.enabled);   // modal: clicks do not reach what is below
        Table panel = new Table(skin);
        panel.setBackground("panel");
        panel.pad(8f);
        panel.add(new Label(message, skin)).padBottom(6f).row();
        MenuList choices = new MenuList(ui);
        choices.add(yes, () -> {
            overlay.remove();
            onYes.run();
        });
        choices.add(no, overlay::remove);
        choices.onBack(overlay::remove);
        choices.focus(1);
        panel.add(choices).minWidth(90f);
        overlay.add(panel);
        ui.stage().addActor(overlay);
        return overlay;
    }
}
