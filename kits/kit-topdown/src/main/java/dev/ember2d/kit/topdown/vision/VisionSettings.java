package dev.ember2d.kit.topdown.vision;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;

/**
 * How far and how wide the viewer sees (F-030), in world units and degrees. The defaults are Gold in Shadows' values at
 * 16 pixels per unit: a 34-pixel circle all around, a 120-pixel cone of 38 degrees each side of the aim whose sides
 * fade over 14 degrees, a 10-pixel soft fog edge, a 6-pixel peek into walls and 90% darkness.
 */
@PublicApi
public final class VisionSettings {

    /** Full sight all around, in world units. */
    public float nearRadius = 34f / 16f;
    /** How far the cone reaches, in world units. */
    public float coneRange = 120f / 16f;
    /** Half the cone's width, in degrees. */
    public float coneHalfAngle = 38f;
    /** The cone's sides narrow towards {@link #nearRadius} over this many degrees. */
    public float coneSoftness = 14f;
    /** Rays per update: the outline's resolution. */
    public int rayCount = 240;
    /** The fog's edge fades over this distance, in world units. */
    public float edgeSoftness = 10f / 16f;
    /** How far sight enters a wall, so its face is visible, in world units. */
    public float wallPeek = 6f / 16f;
    /** Fog opacity, 0 (none) to 1 (black). */
    public float darkness = 0.9f;
    /** The fog colour (Gold in Shadows' darkest palette colour). */
    public final Color fogColor = new Color(0x18 / 255f, 0x18 / 255f, 0x30 / 255f, 1f);

    /** Throws if a value is out of range. */
    public void validate() {
        if (!(nearRadius > 0f) || coneRange < nearRadius) {
            throw new IllegalArgumentException("VisionSettings: need 0 < nearRadius <= coneRange");
        }
        if (coneHalfAngle < 0f || coneHalfAngle > 180f || coneSoftness < 0f) {
            throw new IllegalArgumentException("VisionSettings: cone angles out of range");
        }
        if (rayCount < 16) {
            throw new IllegalArgumentException("VisionSettings: rayCount must be at least 16");
        }
        if (edgeSoftness < 0f || wallPeek < 0f || darkness < 0f || darkness > 1f) {
            throw new IllegalArgumentException("VisionSettings: edgeSoftness and wallPeek >= 0, darkness 0..1");
        }
    }
}
