package dev.ember2d.ui;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import dev.ember2d.core.PublicApi;

/**
 * A HUD number or time with an optional icon (F-012, I-220). The text changes only when the shown value changes, so
 * calling the setters every frame allocates nothing while the value stays the same.
 */
@PublicApi
public final class HudCounter extends Table {

    private final Label label;
    private final StringBuilder text = new StringBuilder();
    private int shown = Integer.MIN_VALUE;
    private boolean seconds;
    int updates;

    /** A counter with an optional prefix text (null for none) and icon (null for none). */
    public HudCounter(Skin skin, String prefix, Drawable icon) {
        super(skin);
        setTouchable(Touchable.disabled);
        if (icon != null) {
            add(new Image(icon)).padRight(3f);
        }
        if (prefix != null) {
            add(new Label(prefix, skin)).padRight(4f);
        }
        label = new Label("", skin);
        add(label);
    }

    public HudCounter(Skin skin, Drawable icon) {
        this(skin, null, icon);
    }

    /** Shows a whole number. */
    public void setValue(int value) {
        if (value == shown && !seconds) {
            return;
        }
        seconds = false;
        shown = value;
        text.setLength(0);
        text.append(value);
        label.setText(text);
        updates++;
    }

    /** Shows a time as m:ss (whole seconds). */
    public void setSeconds(float time) {
        int whole = Math.max(0, (int) time);
        if (whole == shown && seconds) {
            return;
        }
        seconds = true;
        shown = whole;
        text.setLength(0);
        text.append(whole / 60).append(':');
        if (whole % 60 < 10) {
            text.append('0');
        }
        text.append(whole % 60);
        label.setText(text);
        updates++;
    }

    /** The label showing the value. */
    public Label label() {
        return label;
    }
}
