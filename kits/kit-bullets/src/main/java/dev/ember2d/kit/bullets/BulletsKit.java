package dev.ember2d.kit.bullets;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the bullet kit components (F-024) so prefabs and Tiled objects can use them. */
@PublicApi
public final class BulletsKit {

    private BulletsKit() {
    }

    /** BulletTarget. */
    public static void register(ComponentRegistry registry) {
        registry.register(BulletTarget.class, BulletTarget::new);
    }
}
