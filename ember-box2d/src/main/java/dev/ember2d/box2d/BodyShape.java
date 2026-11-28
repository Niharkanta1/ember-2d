package dev.ember2d.box2d;

import dev.ember2d.core.PublicApi;

/** The shape of a {@link RigidBody}'s fixture (F-006B). */
@PublicApi
public enum BodyShape {
    /** A {@code width} x {@code height} box. */
    BOX,
    /** A circle of {@code radius}. */
    CIRCLE
}
