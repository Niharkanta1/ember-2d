package dev.ember2d.data;

import dev.ember2d.core.PublicApi;

/** Published (immediately) after a prefab instance is complete, overrides and references applied (F-010). */
@PublicApi
public record EntitySpawned(int entity, String prefab) {
}
