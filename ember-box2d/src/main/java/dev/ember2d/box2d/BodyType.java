package dev.ember2d.box2d;

import dev.ember2d.core.PublicApi;

/** How a {@link RigidBody} moves (F-006B). */
@PublicApi
public enum BodyType {
    /** Never moves (floors, walls). */
    STATIC,
    /** Moves only by its velocity; not pushed by other bodies (moving platforms). */
    KINEMATIC,
    /** Fully simulated: gravity, forces, collisions (crates, balls, props). */
    DYNAMIC
}
