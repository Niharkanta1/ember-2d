package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;

/** The focused Interactable changed (F-017); target is World.NONE and prompt empty when nothing is in reach. */
@PublicApi
public record InteractionFocus(int interactor, int target, String prompt) {
}
