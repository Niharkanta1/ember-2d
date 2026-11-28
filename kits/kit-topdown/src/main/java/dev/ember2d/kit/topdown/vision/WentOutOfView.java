package dev.ember2d.kit.topdown.vision;

import dev.ember2d.core.PublicApi;

/** A {@link HiddenInFog} entity left the field of view (F-030); queued by {@link VisionSystem}. */
@PublicApi
public record WentOutOfView(int entity) {
}
