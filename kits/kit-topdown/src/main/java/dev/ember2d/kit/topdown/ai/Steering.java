package dev.ember2d.kit.topdown.ai;

import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntityRef;

/**
 * Moves a {@code TopDownBody} towards or away from a target (F-016, I-330), through {@link AiSystem}: seek, flee,
 * arrive (slowing down near the target), or follow a path on the tile grid around walls. The target is an entity, or
 * the point ({@link #targetX}, {@link #targetY}) when {@link #target} is null or dead.
 */
@PublicApi
public final class Steering {

    /** What the body does. */
    public enum Mode { NONE, SEEK, FLEE, ARRIVE, FOLLOW_PATH }

    public Mode mode = Mode.FOLLOW_PATH;
    public EntityRef target;
    public float targetX;
    public float targetY;
    /** Arrive and path ends: close enough, world units. */
    public float arriveRadius = 0.3f;
    /** Arrive and path ends: start slowing down at this distance. */
    public float slowRadius = 2f;
    /** Flee only while the target is closer than this; 0 always flees. */
    public float fleeRadius;
    /** Seek and path modes stop when the target is farther than this; 0 has no limit. */
    public float sight;
    /** How often a path is planned again (the target moves). */
    public float repathSeconds = 0.5f;
    /** Runtime: the last planning found a path. */
    public boolean pathFound;
    /** Runtime: seconds until the next planning. */
    public float repathLeft;
    /** Runtime: the waypoint being walked to. */
    public int waypoint;
}
