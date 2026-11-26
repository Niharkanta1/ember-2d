package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;

/** A platformer body started a jump (F-015); for sound and particles. */
@PublicApi
public record Jumped(int entity, JumpKind kind) {
}
