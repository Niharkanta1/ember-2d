package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Health reached 0 (F-021); published exactly once per entity. The killer is World.NONE when unknown. */
@PublicApi
public record Died(int entity, int killer) {
}
