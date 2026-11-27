package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;

/** Items lying in the world (F-017); the entity also needs a TriggerZone (and a trigger Collider). */
@PublicApi
public final class ItemPickup {
    /** The item ID (see ItemCatalog). */
    public String item = "";
    /** How many; what does not fit stays here. */
    public int count = 1;
}
