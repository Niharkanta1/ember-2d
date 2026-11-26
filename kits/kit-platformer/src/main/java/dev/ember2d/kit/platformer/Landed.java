package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/** A platformer body touched the ground after being in the air (F-015). */
@PublicApi
public record Landed(int entity) {
}
