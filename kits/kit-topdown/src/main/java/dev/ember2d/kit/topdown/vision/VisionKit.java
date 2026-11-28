package dev.ember2d.kit.topdown.vision;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the field-of-view components (F-030) so prefabs and Tiled objects can use them. */
@PublicApi
public final class VisionKit {

    private VisionKit() {
    }

    /** {@link Viewer} and {@link HiddenInFog}. */
    public static void register(ComponentRegistry registry) {
        registry.register(Viewer.class, Viewer::new)
                .register(HiddenInFog.class, HiddenInFog::new);
    }
}
