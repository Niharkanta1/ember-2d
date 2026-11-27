package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.kit.common.Despawn;
import dev.ember2d.kit.common.ZoneEntered;

/**
 * Item pickups (F-017): when an entity with an {@link Inventory} enters a trigger zone with an {@link ItemPickup}, it
 * takes what fits and {@link ItemPickedUp} is published; a pickup taken whole is despawned, a partly taken one keeps the
 * rest. It subscribes when created (create it in a scene's {@code enter}); it does no per-entity work.
 */
@PublicApi
public final class ItemPickupSystem extends EntitySystem {

    private final ItemCatalog catalog;

    public ItemPickupSystem(World world, ItemCatalog catalog, EventBus events) {
        super(Phase.FIXED, world.family(ItemPickup.class));
        this.catalog = catalog;
        events.on(ZoneEntered.class, e -> {
            if (!world().isAlive(e.zone()) || !world().isAlive(e.entity())) {
                return;
            }
            ItemPickup pickup = world().get(e.zone(), ItemPickup.class);
            Inventory inventory = world().get(e.entity(), Inventory.class);
            if (pickup == null || inventory == null || pickup.count <= 0) {
                return;
            }
            int taken = inventory.add(catalog, pickup.item, pickup.count);
            if (taken == 0) {
                return;
            }
            pickup.count -= taken;
            events.publish(new ItemPickedUp(e.entity(), e.zone(), pickup.item, taken));
            if (pickup.count == 0) {
                Despawn.despawn(world(), e.zone());
            }
        });
    }

    @Override
    protected void process(int entity, float delta) {
    }

    /** The catalog pickups are checked against. */
    public ItemCatalog catalog() {
        return catalog;
    }
}
