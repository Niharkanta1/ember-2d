package dev.ember2d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.PublicApi;

/**
 * World text on an entity (F-004, I-140), drawn by a {@link TextRenderer}. One font pixel is one virtual pixel
 * ({@code scale} 1). The anchor is the entity's {@code Transform} plus {@code offset}: the baseline's left end, centre
 * or right end, by {@code align}. With {@code wrapWidth} > 0 lines wrap inside a box of that width placed by the same
 * rule.
 */
@PublicApi
public final class Text {

    /** Asset ID of a {@code font} or {@code freetype} entry. */
    public String font;
    public String text = "";
    public final Color color = new Color(Color.WHITE);
    public float scale = 1f;
    public TextAlign align = TextAlign.LEFT;
    /** Wrap width in world units; 0 = no wrapping. */
    public float wrapWidth;
    /** Offset from the entity's position, in world units. */
    public final Vector2 offset = new Vector2();
    /** Render layer. */
    public String layer = "world";

    // layout cache (TextRenderer)
    final GlyphLayout layout = new GlyphLayout();
    BitmapFont laidFont;
    String laidText;
    float laidScale = Float.NaN;
    TextAlign laidAlign;
    float laidWrap = Float.NaN;
    float laidColor = Float.NaN;
    String resolvedId;
    BitmapFont resolved;
    int resolvedGeneration;
    int layoutCount;
}
