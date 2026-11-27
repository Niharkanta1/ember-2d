package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Damage was applied (F-021); the knockback is for genre kits to apply. */
@PublicApi
public record Damaged(int target, int source, int amount, int healthLeft, float knockbackX, float knockbackY) {
}
