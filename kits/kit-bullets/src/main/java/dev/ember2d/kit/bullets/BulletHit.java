package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;

/** A bullet hit a target (F-024); queued. Damage is applied by kit-common when the system has a HealthSystem. */
@PublicApi
public record BulletHit(int target, int type, int damage, float x, float y, int team) {
}
