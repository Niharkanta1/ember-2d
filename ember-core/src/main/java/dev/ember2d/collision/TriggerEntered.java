package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;

/** An actor started overlapping a trigger (F-006); queued, delivered after the fixed step. */
@PublicApi
public record TriggerEntered(int trigger, int actor) {
}
