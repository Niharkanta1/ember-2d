package dev.ember2d.kit.topdown;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the top-down kit components (F-016) so prefabs and Tiled objects can use them. */
@PublicApi
public final class TopDownKit {

    private TopDownKit() {
    }

    /** TopDownBody and the AI Steering (the shooter package registers its own with ShooterKit). */
    public static void register(ComponentRegistry registry) {
        registry.register(TopDownBody.class, TopDownBody::new)
                .register(dev.ember2d.kit.topdown.ai.Steering.class, dev.ember2d.kit.topdown.ai.Steering::new);
    }
}
