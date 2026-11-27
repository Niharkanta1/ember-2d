package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the common kit's components (F-021) so prefabs and Tiled objects can use them. */
@PublicApi
public final class CommonKit {

    private CommonKit() {
    }

    /** Health, Team, Hitbox, Hurtbox, Lifetime, TriggerZone and Spawner. */
    public static void register(ComponentRegistry registry) {
        registry.register(Health.class, Health::new)
                .register(Team.class, Team::new)
                .register(Hitbox.class, Hitbox::new)
                .register(Hurtbox.class, Hurtbox::new)
                .register(Lifetime.class, Lifetime::new)
                .register(TriggerZone.class, TriggerZone::new)
                .register(Spawner.class, Spawner::new);
    }
}
