package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;

/**
 * Makes an entity a bullet target (F-024): bullets of other teams (kit-common Team; none is team 0) hit it when the
 * circles touch, and pass within the graze radius report Grazed.
 */
@PublicApi
public final class BulletTarget {
    /** Hit circle radius around the Transform position (keep it small for players). */
    public float radius = 0.25f;
    /** 0 for no grazing. */
    public float grazeRadius;
    /** Circle centre offset from the Transform position. */
    public float offsetX;
    public float offsetY;
}
