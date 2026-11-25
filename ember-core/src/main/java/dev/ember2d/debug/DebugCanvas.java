package dev.ember2d.debug;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import dev.ember2d.core.PublicApi;

/**
 * Drawing backend of the {@link DebugOverlay} (F-014). The engine provides one based on libGDX
 * {@code ShapeRenderer} and the built-in font; tests use a recording fake. Games do not call it.
 */
@PublicApi
public interface DebugCanvas {

    /** Starts outline drawing with this projection (world camera for shapes, screen for the panel). */
    void beginLines(Matrix4 projection);

    void line(float x1, float y1, float x2, float y2, Color color);

    void rect(float x, float y, float width, float height, Color color);

    void circle(float x, float y, float radius, Color color);

    void endLines();

    /** A translucent filled rectangle in screen pixels (panel background). */
    void panel(float x, float y, float width, float height, Color color, Matrix4 projection);

    /** Starts text drawing in screen pixels. */
    void beginText(Matrix4 projection);

    /** Draws one line of text with its top-left corner at (x, y), in screen pixels. */
    void text(CharSequence text, float x, float y, Color color);

    void endText();

    /** Height of one text line in screen pixels. */
    float lineHeight();
}
