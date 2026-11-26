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
    /** Render layer name. */
    public String layer = "world";
    /** Order within a {@link SortMode#Z} layer, ascending. */
    public float z;
    /** Draw anchor and rotation/scale pivot, in region pixels from the bottom-left corner. */
    public final Vector2 origin = new Vector2();

    // the region last filled from regionId and the asset generation it came from (hot reload, F-008)
    TextureRegion resolvedRegion;
    int resolvedGeneration;
}
