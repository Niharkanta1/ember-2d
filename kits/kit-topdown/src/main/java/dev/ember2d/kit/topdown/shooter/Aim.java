package dev.ember2d.kit.topdown.shooter;

import dev.ember2d.core.PublicApi;

/**
 * Where an entity aims (F-016), updated by AimSystem: the aim axis (touch aim stick or keys) when held, else the
 * mouse when mouse is true, else the last aim (at first the TopDownBody facing).
 */
@PublicApi
public final class Aim {
    /** The 2D input axis that aims. */
    public String axis = "aim";
    /** Aim at the mouse pointer (desktop); leave off on touch screens. */
    public boolean mouse;
    /** Runtime: the aim direction, unit length. */
    public float aimX = 1f;
    public float aimY;
    /** Runtime: the aim angle in degrees (0 right, 90 up). */
    public float angle;
    /** Runtime: set once a direction has been chosen. */
    public boolean aimed;
}
