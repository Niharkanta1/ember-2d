package dev.ember2d.collision;

import dev.ember2d.core.PublicApi;

/** A moving solid could not push an actor out of its way (the actor was left where it was) (F-006); queued, delivered after the fixed step. */
@PublicApi
public record ActorSquished(int actor, int solid) {
}
