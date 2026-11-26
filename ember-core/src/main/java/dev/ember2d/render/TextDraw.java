package dev.ember2d.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Align;
import dev.ember2d.core.PublicApi;

/**
 * Immediate world text (F-004, I-140): for debug labels and one-off text inside a render system or layer content. One
 * font pixel is one virtual pixel. Render thread only (one reused layout).
 */
@PublicApi
public final class TextDraw {

    private static final GlyphLayout LAYOUT = new GlyphLayout();

    private TextDraw() {
    }

    /**
     * Draws text with its baseline anchor at (x, y) in world units.
     *
     * @param wrapWidth world units; 0 = no wrapping
     */
    public static void draw(Batch batch, BitmapFont font, CharSequence text, float x, float y, TextAlign align,
            float wrapWidth, float pixelsPerUnit) {
        font.setUseIntegerPositions(false);
        font.getData().setScale(1f / pixelsPerUnit);
        LAYOUT.setText(font, text, 0, text.length(), font.getColor(), wrapWidth, align(align), wrapWidth > 0f, null);
        font.draw(batch, LAYOUT, boxX(x, align, wrapWidth), y + font.getCapHeight());
    }

    static int align(TextAlign align) {
        return switch (align) {
            case LEFT -> Align.left;
            case CENTER -> Align.center;
            case RIGHT -> Align.right;
        };
    }

    /** Left edge of the layout box so the anchor sits at x. */
    static float boxX(float x, TextAlign align, float wrapWidth) {
        if (wrapWidth <= 0f) {
            return x;   // with no target width libGDX aligns around x itself
        }
        return switch (align) {
            case LEFT -> x;
            case CENTER -> x - wrapWidth / 2f;
            case RIGHT -> x - wrapWidth;
        };
    }
}
