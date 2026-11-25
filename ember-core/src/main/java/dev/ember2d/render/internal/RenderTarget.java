package dev.ember2d.render.internal;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/** The low-resolution image the world is drawn into in the pixel-perfect modes. Replaced by a fake in tests. */
public interface RenderTarget extends Disposable {

    /** Sets the size in virtual pixels; recreates the buffer if it changed. */
    void resize(int width, int height);

    /** Starts drawing into the target (binds it and sets the GL viewport to its size). */
    void begin();

    /** Stops drawing into the target. */
    void end();

    /** The target's image, the right way up, for drawing to the window. */
    TextureRegion region();
}
