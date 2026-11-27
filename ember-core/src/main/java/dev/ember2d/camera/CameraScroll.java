package dev.ember2d.camera;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.PublicApi;

/**
 * Scrolls the camera at a constant speed and keeps tagged entities inside the view (F-004, I-310): shoot-'em-up stages.
 * Put it on a camera entity instead of {@link CameraFollow} and add a {@link CameraScrollSystem}.
 */
@PublicApi
public final class CameraScroll {

    /** World units per second (scaled time: the scroll stops while the game is paused). */
    public float speedX;
    public float speedY = 2f;
    /** The view stays inside this rectangle and the scroll stops at its edge; null for no end. */
    public Rectangle bounds;
    /** Entities with this world tag are kept inside the view. */
    public String clampTag = "player";
    /** How far inside the view edges they are kept, in world units. */
    public float margin = 0.5f;
    /** Runtime: the scroll reached the edge of {@link #bounds}. */
    public boolean finished;
}
