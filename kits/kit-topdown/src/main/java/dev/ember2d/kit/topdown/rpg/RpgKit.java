package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the RPG components (F-017) so prefabs and Tiled objects can use them. */
@PublicApi
public final class RpgKit {

    private RpgKit() {
    }

    /** Interactable, Interactor, Inventory and ItemPickup. */
    public static void register(ComponentRegistry registry) {
        registry.register(Interactable.class, Interactable::new).register(Interactor.class, Interactor::new)
                .register(Inventory.class, Inventory::new).register(ItemPickup.class, ItemPickup::new);
    }
}
