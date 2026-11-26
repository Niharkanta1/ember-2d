package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/** How a jump started (F-015). */
@PublicApi
public enum JumpKind {
    /** From the ground. */
    GROUND,
    /** Just after walking off a ledge (coyote time). */
    COYOTE,
    /** In the air (double or multi jump). */
    AIR,
    /** Off a wall. */
    WALL
}
