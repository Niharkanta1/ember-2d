package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;

/** A bullet removed by BulletSystem.clear with pickups on (F-024); queued, one per bullet. */
@PublicApi
public record BulletCleared(float x, float y) {
}
