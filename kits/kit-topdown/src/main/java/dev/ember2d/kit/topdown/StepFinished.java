package dev.ember2d.kit.topdown;

import dev.ember2d.core.PublicApi;

/** A grid-mode {@link TopDownBody} finished a step (F-016); tileX and tileY are the tile its position is in. */
@PublicApi
public record StepFinished(int entity, int tileX, int tileY) {
}
