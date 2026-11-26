package dev.ember2d.app;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.input.TouchControlsConfig;
import dev.ember2d.input.internal.TouchControls;
import dev.ember2d.ui.UiSkins;

/**
 * Draws the on-screen controls (F-022, I-122, I-225) over the finished frame, in window coordinates, with skin
 * drawables: the generated pixel look by default, or a game's own drawables from the skin asset named by
 * {@code EngineConfig.touchControlsSkin} (any of {@code touch-base}, {@code touch-knob}, {@code touch-button},
 * {@code touch-button-down}, {@code touch-dpad}, {@code touch-dpad-down}). Held parts are drawn brighter; controls
 * that are not live (another input context on top) are not drawn. Created on first use.
 */
final class TouchControlsOverlay implements Disposable {

    private final Skin defaults = UiSkins.touch();
    private final Skin custom;
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();

    TouchControlsOverlay(Skin custom) {
        this.custom = custom;
    }

    private Drawable drawable(String name) {
        if (custom != null && custom.has(name, Drawable.class)) {
            return custom.getDrawable(name);
        }
        return defaults.getDrawable(name);
    }

    void draw(TouchControls controls, Batch batch, float width, float height) {
        TouchControlsConfig config = controls.config();
        float alpha = controls.opacity();
        projection.setToOrtho2D(0f, 0f, width, height);
        batch.setProjectionMatrix(projection);
        batch.begin();
        if (controls.stickShown()) {
            float r = controls.stickRadius();
            boolean held = controls.stickHeld();
            if (controls.isDpad()) {
                batch.setColor(1f, 1f, 1f, Math.min(1f, alpha * (held ? 2f : 1.2f)));
                draw(batch, drawable(held ? "touch-dpad-down" : "touch-dpad"), controls.baseX(), controls.baseY(), r);
            } else {
                batch.setColor(1f, 1f, 1f, Math.min(1f, alpha * (held ? 1.6f : 1f)));
                draw(batch, drawable("touch-base"), controls.baseX(), controls.baseY(), r);
                batch.setColor(1f, 1f, 1f, Math.min(1f, alpha * (held ? 2.2f : 1.4f)));
                draw(batch, drawable("touch-knob"), controls.knobX(), controls.knobY(), r * 0.45f);
            }
        }
        font.getData().setScale(Math.max(1f, Math.round(controls.density() * 1.5f)));
        for (int i = 0; i < controls.buttonCount(); i++) {
            if (!controls.buttonShown(i)) {
                continue;
            }
            boolean down = controls.pressed(i);
            float x = controls.buttonX(i);
            float y = controls.buttonY(i);
            batch.setColor(1f, 1f, 1f, Math.min(1f, alpha * (down ? 2.2f : 1.2f)));
            draw(batch, drawable(down ? "touch-button-down" : "touch-button"), x, y, controls.buttonRadius(i));
            String label = config.buttons().get(i).label();
            if (!label.isEmpty()) {
                font.setColor(0.1f, 0.1f, 0.15f, Math.min(1f, alpha * 2.5f));
                layout.setText(font, label);
                font.draw(batch, layout, x - layout.width / 2f, y + layout.height / 2f);
            }
        }
        batch.end();
        batch.setColor(Color.WHITE);
    }

    private static void draw(Batch batch, Drawable drawable, float x, float y, float radius) {
        drawable.draw(batch, x - radius, y - radius, radius * 2f, radius * 2f);
    }

    @Override
    public void dispose() {
        defaults.dispose();
        font.dispose();
    }
}
