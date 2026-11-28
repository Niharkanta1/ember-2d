package dev.ember2d.kit.topdown.vision;

import dev.ember2d.core.PublicApi;

/**
 * The entity whose eyes the field of view uses (F-030); one per scene. {@link VisionSystem} looks from its
 * {@code Transform} plus {@link #eyeY}, in the direction of its {@code Aim} (kit-topdown shooter) if it has one, else
 * {@link #angle}.
 */
@PublicApi
public final class Viewer {

    /** The look direction in degrees (0 right, 90 up) when the entity has no {@code Aim}. */
    public float angle;
    /** Eye height above the entity's position, in world units (the position is usually at the feet). */
    public float eyeY;
}
