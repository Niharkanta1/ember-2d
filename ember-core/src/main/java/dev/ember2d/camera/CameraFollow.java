package dev.ember2d.camera;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntityRef;

/**
 * Makes the camera follow an entity (F-004); put it on any entity (usually a dedicated camera entity) and add a
 * {@link CameraFollowSystem}.
 */
@PublicApi
public final class CameraFollow {

    /** The entity to follow (it needs a {@code Transform}); nothing happens while null or dead. */
    public EntityRef target;
    /**
     * Area around the camera centre, in world units, inside which the target can move without moving the camera.
     * {@code x}/{@code y} offset the zone's centre from the camera centre; zero size follows exactly.
     */
    public final Rectangle deadZone = new Rectangle();
    /** 0 follows instantly; larger values ease towards the target (per-second rate). */
    public float smoothing;
    /** Keeps the whole view inside this rectangle, in world units; null for no limit. */
    public Rectangle bounds;
}
