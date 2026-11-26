package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/** A platformer body started a dash (F-015); {@code direction} is +1 right or -1 left. */
@PublicApi
public record Dashed(int entity, int direction) {
}
