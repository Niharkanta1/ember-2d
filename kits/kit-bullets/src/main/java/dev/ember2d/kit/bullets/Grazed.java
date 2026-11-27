package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;

/** A bullet passed within a target graze radius without hitting it (F-024); queued, once per bullet. */
@PublicApi
public record Grazed(int target, float x, float y) {
}
