package dev.ember2d.box2d;

import dev.ember2d.core.PublicApi;

/** Two solid (non-sensor) bodies stopped touching (F-006B); queued during the step, delivered after it. */
@PublicApi
public record ContactEnded(int a, int b) {

    /** True if either entity is {@code entity}. */
    public boolean involves(int entity) {
        return a == entity || b == entity;
    }

    /** The other entity of the pair, or -1 if {@code entity} is not part of it. */
    public int other(int entity) {
        return a == entity ? b : b == entity ? a : -1;
    }
}
