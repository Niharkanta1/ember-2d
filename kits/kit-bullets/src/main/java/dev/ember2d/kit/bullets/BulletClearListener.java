package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;

/** Told about each bullet removed by a clear (F-024), for example to drop score pickups. */
@PublicApi
@FunctionalInterface
public interface BulletClearListener {
    void cleared(float x, float y);
}
