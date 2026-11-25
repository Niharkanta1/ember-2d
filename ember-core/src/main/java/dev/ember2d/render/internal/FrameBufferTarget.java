package dev.ember2d.render.internal;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;

/**
 * A libGDX {@link FrameBuffer} at the virtual resolution, Nearest filtering. libGDX manages frame buffers and
 * rebuilds them after an Android graphics context loss.
 */
public final class FrameBufferTarget implements RenderTarget {

    private FrameBuffer buffer;
    private final TextureRegion region = new TextureRegion();

    @Override
    public void resize(int width, int height) {
        if (buffer != null && buffer.getWidth() == width && buffer.getHeight() == height) {
            return;
        }
        if (buffer != null) {
            buffer.dispose();
        }
        buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        Texture texture = buffer.getColorBufferTexture();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        region.setRegion(texture);
        region.flip(false, true);   // frame buffer images are upside down
    }

    @Override
    public void begin() {
        buffer.begin();
    }

    @Override
    public void end() {
        buffer.end();
    }

    @Override
    public TextureRegion region() {
        return region;
    }

    @Override
    public void dispose() {
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
    }
}
