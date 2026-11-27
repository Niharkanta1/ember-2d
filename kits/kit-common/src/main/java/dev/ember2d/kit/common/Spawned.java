package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** A spawner created an instance (F-021). */
@PublicApi
public record Spawned(int spawner, int entity) {
}
