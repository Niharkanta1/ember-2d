package dev.ember2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import dev.ember2d.core.PublicApi;

/** A HUD bar showing 0..1 (health, a timer), drawn with the skin's {@code bar-bg} and {@code bar-fill} (F-012). */
@PublicApi
public final class HudBar extends Widget {

    private final Drawable background;
    private final Drawable fill;
    private float value = 1f;

    public HudBar(Skin skin) {
        background = skin.getDrawable("bar-bg");
        fill = skin.getDrawable("bar-fill");
        setTouchable(Touchable.disabled);
    }

    /** Sets the filled part, clamped to 0..1. */
    public void setValue(float zeroToOne) {
        value = MathUtils.clamp(zeroToOne, 0f, 1f);
    }

    public float value() {
        return value;
    }

    @Override
    public float getPrefWidth() {
        return 60f;
    }

    @Override
    public float getPrefHeight() {
        return 7f;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        Color c = getColor();
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
        background.draw(batch, getX(), getY(), getWidth(), getHeight());
        float inner = Math.max(0f, getWidth() - 2f);
        if (value > 0f) {
            fill.draw(batch, getX() + 1f, getY() + 1f, inner * value, Math.max(0f, getHeight() - 2f));
        }
    }
}
