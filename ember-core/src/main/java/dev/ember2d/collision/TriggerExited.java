package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;

/** An actor stopped overlapping a trigger, or one of them was destroyed (F-006); queued, delivered after the fixed step. */
@PublicApi
public record TriggerExited(int trigger, int actor) {
}
