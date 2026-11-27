package dev.ember2d.kit.topdown.shooter;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the shooter components (F-016) so prefabs and Tiled objects can use them. */
@PublicApi
public final class ShooterKit {

    private ShooterKit() {
    }

    /** Aim and Weapon. */
    public static void register(ComponentRegistry registry) {
        registry.register(Aim.class, Aim::new).register(Weapon.class, Weapon::new);
    }
}
