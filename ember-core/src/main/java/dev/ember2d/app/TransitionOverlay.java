package dev.ember2d.app;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

/**
 * Draws the scene transition cover and the loading screen (F-010, I-240) over the finished frame, in window pixels.
 * Created on first use.
 */
final class TransitionOverlay implements Disposable {

    private final Texture texture;
    private final TextureRegion pixel;
    private final Matrix4 projection = new Matrix4();

    TransitionOverlay() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        texture = new Texture(pixmap);
        pixmap.dispose();
        pixel = new TextureRegion(texture);
    }

    /**
     * @param progress the loading screen's progress, or a negative number when it does not show
     */
    void draw(Batch batch, float width, float height, Color cover, float alpha, LoadingScreen loadingScreen,
            float progress) {
        projection.setToOrtho2D(0f, 0f, width, height);
        batch.setProjectionMatrix(projection);
        batch.begin();
        batch.setColor(cover.r, cover.g, cover.b, cover.a * alpha);
        batch.draw(pixel, 0f, 0f, width, height);
        batch.setColor(Color.WHITE);
        if (loadingScreen != null && progress >= 0f) {
            loadingScreen.draw(batch, pixel, width, height, progress);
            batch.setColor(Color.WHITE);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        texture.dispose();
    }
}
