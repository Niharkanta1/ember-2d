package dev.ember2d.kit.topdown;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the top-down kit components (F-016) so prefabs and Tiled objects can use them. */
@PublicApi
public final class TopDownKit {

    private TopDownKit() {
    }

    /** TopDownBody (the shooter package adds its own in later tasks). */
    public static void register(ComponentRegistry registry) {
        registry.register(TopDownBody.class, TopDownBody::new);
    }
}
