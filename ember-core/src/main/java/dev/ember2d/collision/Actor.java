package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.World;

/**
 * A body moved only through {@link Collision} (F-006). Actors are blocked by solids and solid tiles, never by other
 * actors. The hit fields describe the last move (read-only for game code).
 */
@PublicApi
public final class Actor {
    /** While above 0, one-way solids and tiles do not block; counts down by one per move. */
    public int dropThroughTicks;
    public boolean hitX;
    public boolean hitY;
    /** The solid hit on x, or {@link World#NONE} for a tile or nothing. */
    public int hitEntityX = World.NONE;
    /** The solid hit on y, or {@link World#NONE} for a tile or nothing. */
    public int hitEntityY = World.NONE;
}
