package dev.ember2d.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.input.TouchControlsConfig;
import dev.ember2d.input.internal.TouchControls;

/**
 * Draws the on-screen controls (F-022, I-122) over the finished frame, in window coordinates: the joystick base and
 * knob and the round buttons with their labels, at the configured opacity; held parts are drawn brighter. Created on
 * first use; owns its shape renderer and font.
 */
final class TouchControlsOverlay implements Disposable {

    private static final int SEGMENTS = 40;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();
    private final Color color = new Color();

    void draw(TouchControls controls, Batch batch, float width, float height) {
        TouchControlsConfig config = controls.config();
        float alpha = config.opacity();
        projection.setToOrtho2D(0f, 0f, width, height);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (config.joystick() != null) {
            float r = controls.stickRadius();
            shapes.setColor(color.set(1f, 1f, 1f, alpha * (controls.stickHeld() ? 0.6f : 0.35f)));
            shapes.circle(controls.baseX(), controls.baseY(), r, SEGMENTS);
            shapes.setColor(color.set(1f, 1f, 1f, Math.min(1f, alpha * (controls.stickHeld() ? 2.2f : 1.4f))));
            shapes.circle(controls.knobX(), controls.knobY(), r * 0.45f, SEGMENTS);
        }
        for (int i = 0; i < controls.buttonCount(); i++) {
            shapes.setColor(color.set(1f, 1f, 1f, Math.min(1f, alpha * (controls.pressed(i) ? 2.2f : 1f))));
            shapes.circle(controls.buttonX(i), controls.buttonY(i), controls.buttonRadius(i), SEGMENTS);
        }
        shapes.end();
        batch.setProjectionMatrix(projection);
        batch.begin();
        font.getData().setScale(Math.max(1f, Math.round(controls.density() * 1.5f)));
        font.setColor(0.1f, 0.1f, 0.15f, Math.min(1f, alpha * 2.5f));
        for (int i = 0; i < controls.buttonCount(); i++) {
            String label = config.buttons().get(i).label();
            if (!label.isEmpty()) {
                layout.setText(font, label);
                font.draw(batch, layout, controls.buttonX(i) - layout.width / 2f,
                        controls.buttonY(i) + layout.height / 2f);
            }
        }
        batch.end();
        batch.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        font.dispose();
    }
}
