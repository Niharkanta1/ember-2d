package dev.ember2d.demo.rpg;

import dev.ember2d.camera.CameraFollow;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;
import dev.ember2d.kit.common.CommonKit;
import dev.ember2d.kit.topdown.TopDownKit;
import dev.ember2d.kit.topdown.rpg.RpgKit;

/** Every component the RPG demo's prefabs and maps use (also drives the R8 keep rules). */
public final class RpgComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        CommonKit.register(registry);    // TriggerZone
        TopDownKit.register(registry);   // TopDownBody
        RpgKit.register(registry);       // Interactable, Interactor, ItemPickup
        registry.register(CameraFollow.class, CameraFollow::new)
                .register(Door.class, Door::new)
                .register(Chest.class, Chest::new)
                .register(Travel.class, Travel::new);
    }
}
