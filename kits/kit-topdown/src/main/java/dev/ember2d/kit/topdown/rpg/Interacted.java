package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;

/** The interactor used its focused Interactable (F-017); action is the Interactable action ID. */
@PublicApi
public record Interacted(int interactor, int target, String action) {
}
