package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.World;

/** The first hit of a ray (F-006); filled by {@link Collision#raycast}. */
@PublicApi
public final class RayHit {
    /** The entity hit, or {@link World#NONE} when a tile was hit. */
    public int entity = World.NONE;
    public boolean tile;
    public float x;
    public float y;
    public float distance;
    public float normalX;
    public float normalY;
}
