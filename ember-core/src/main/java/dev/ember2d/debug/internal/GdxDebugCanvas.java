package dev.ember2d.debug.internal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.debug.DebugCanvas;

/**
 * The overlay's drawing backend: libGDX {@link ShapeRenderer} for lines and the panel background, the built-in
 * {@link BitmapFont} for text (scaled up on high-density screens), drawn with the renderer's batch.
 */
public final class GdxDebugCanvas implements DebugCanvas, Disposable {

    private final Batch batch;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();

    public GdxDebugCanvas(Batch batch) {
        this.batch = batch;
        float density = Gdx.graphics == null ? 1f : Gdx.graphics.getDensity();
        font.getData().setScale(Math.max(1, Math.round(density)));
    }

    @Override
    public void beginLines(Matrix4 projection) {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Line);
    }

    @Override
    public void line(float x1, float y1, float x2, float y2, Color color) {
        shapes.setColor(color);
        shapes.line(x1, y1, x2, y2);
    }

    @Override
    public void rect(float x, float y, float width, float height, Color color) {
        shapes.setColor(color);
        shapes.rect(x, y, width, height);
    }

    @Override
    public void circle(float x, float y, float radius, Color color) {
        shapes.setColor(color);
        shapes.circle(x, y, radius, 24);
    }

    @Override
    public void endLines() {
        shapes.end();
    }

    @Override
    public void panel(float x, float y, float width, float height, Color color, Matrix4 projection) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(color);
        shapes.rect(x, y, width * font.getData().scaleX, height);
        shapes.end();
    }

    @Override
    public void beginText(Matrix4 projection) {
        batch.setProjectionMatrix(projection);
        batch.setColor(Color.WHITE);
        batch.begin();
    }

    @Override
    public void text(CharSequence text, float x, float y, Color color) {
        font.setColor(color);
        font.draw(batch, text, x, y);
    }

    @Override
    public void endText() {
        batch.end();
    }

    @Override
    public float lineHeight() {
        return font.getLineHeight();
    }

    @Override
    public void dispose() {
        shapes.dispose();
        font.dispose();
    }
}
