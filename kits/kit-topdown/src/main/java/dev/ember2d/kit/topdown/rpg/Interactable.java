package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;

/** Something the player can use (F-017): an NPC, a door, a chest. InteractionSystem focuses it when in reach. */
@PublicApi
public final class Interactable {
    /** Text for the prompt UI. */
    public String prompt = "Talk";
    /** A game-defined ID, such as talk:elder or open:chest_1. */
    public String action = "";
    /** Reach in world units, measured to the offset point. */
    public float range = 1.2f;
    /** The point measured to, relative to the Transform. */
    public float offsetX;
    public float offsetY;
    public boolean enabled = true;
}
