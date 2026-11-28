package dev.ember2d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.PublicApi;

/**
 * A drawable region on an entity with a {@code Transform} (F-004). Set {@link #regionId} (resolved through the
 * asset service on first draw) or {@link #region} directly. Size is the region's pixel size divided by the
 * game's pixels per unit, times the transform scale.
 */
@PublicApi
public final class Sprite {

    /** Asset region ID, for example {@code "atlas.main/player_idle"} or a texture ID. */
    public String regionId;
    /** The region to draw; filled from {@link #regionId} when null. */
    public TextureRegion region;
    public final Color tint = new Color(Color.WHITE);
    public boolean flipX;
    public boolean flipY;
    /** False skips drawing (for example outside the player's field of view, F-030); everything else is kept. */
    public boolean visible = true;
    /** Render layer name. */
    public String layer = "world";
    /** Order within a {@link SortMode#Z} layer, ascending. */
    public float z;
    /** Draw anchor and rotation/scale pivot, in region pixels from the bottom-left corner. */
    public final Vector2 origin = new Vector2();

    // ---- materials (F-026, I-520): all off by default ----
    /** Hit flash: 0..1 mixes the colour towards {@link #flashColor}. */
    public float flash;
    public final Color flashColor = new Color(Color.WHITE);
    /** A 1-pixel outline around the opaque pixels; alpha 0 means none. Needs a transparent margin in the region. */
    public final Color outline = new Color(0f, 0f, 0f, 0f);
    /** Palette swap: a texture asset of up to 16 x 2 pixels (row 0: source colours, row 1: their replacements). */
    public String paletteId;

    /** Whether any material effect is on. */
    public boolean hasMaterial() {
        return flash > 0f || outline.a > 0f || paletteId != null;
    }

    // the region last filled from regionId and the asset generation it came from (hot reload, F-008)
    TextureRegion resolvedRegion;
    int resolvedGeneration;
}
