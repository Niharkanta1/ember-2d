package dev.ember2d.kit.topdown.vision;

import dev.ember2d.core.PublicApi;

/**
 * Hidden while outside the field of view (F-030): {@link VisionSystem} sets the entity's {@code Sprite.visible} and
 * queues {@link CameIntoView} / {@link WentOutOfView} when that changes. Put it on enemies, pick-ups and other things
 * that should not be seen in the dark.
 */
@PublicApi
public final class HiddenInFog {

    /** The entity counts as seen when any part of a circle this big (world units) is in view. */
    public float radius = 0.4f;
    /** Height of the circle's centre above the entity's position, in world units. */
    public float centerY = 0.5f;
    /** Runtime: whether it is in view now. */
    public boolean inView = true;
    /** Runtime: false until the first update, so the first state is set without an event. */
    public boolean known;
}
