package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.World;

/**
 * The outcome of one move (F-006). {@link Collision} reuses one instance: it is valid until the next move. The normal
 * points away from what was hit (a landing gives (0, 1)).
 */
@PublicApi
public final class MoveResult {
    public boolean hitX;
    public boolean hitY;
    /** The solid hit on x, or {@link World#NONE} for a tile or nothing. */
    public int entityX = World.NONE;
    /** The solid hit on y, or {@link World#NONE} for a tile or nothing. */
    public int entityY = World.NONE;
    public float normalX;
    public float normalY;

    void reset() {
        hitX = false;
        hitY = false;
        entityX = World.NONE;
        entityY = World.NONE;
        normalX = 0f;
        normalY = 0f;
    }
}
