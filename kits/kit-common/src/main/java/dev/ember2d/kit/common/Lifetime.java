package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Despawns the entity after a time (F-021), counted on scaled time by {@link LifetimeSystem}. */
@PublicApi
public final class Lifetime {
    public float seconds = 1f;
    /** Runtime: seconds left; -1 until the system first sees the entity. */
    public float left = -1f;
}
