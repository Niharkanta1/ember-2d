package dev.ember2d.kit.platformer;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the platformer kit's components for prefabs and Tiled (F-015); call it from the game's setup. */
@PublicApi
public final class PlatformerKit {

    private PlatformerKit() {
    }

    public static void register(ComponentRegistry registry) {
        registry.register(PlatformerBody.class, PlatformerBody::new)
                .register(Patroller.class, Patroller::new)
                .register(MovingPlatform.class, MovingPlatform::new)
                .register(Ladder.class, Ladder::new);
    }
}
