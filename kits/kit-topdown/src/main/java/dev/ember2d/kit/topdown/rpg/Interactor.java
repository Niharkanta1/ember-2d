package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.World;

/** Lets an entity (usually the player) use Interactables in front of it (F-017). */
@PublicApi
public final class Interactor {
    /** The input action that interacts. */
    public String inputAction = "interact";
    /** Degrees around the facing direction that count as in front. */
    public float cone = 120f;
    /** Runtime: the focused Interactable, or World.NONE. */
    public int target = World.NONE;
}
