package dev.ember2d.box2d;

import dev.ember2d.core.PublicApi;
import dev.ember2d.data.ComponentRegistry;

/** Registers the Box2D module's components (F-006B) so prefabs and Tiled objects can use them. */
@PublicApi
public final class Box2dKit {

    private Box2dKit() {
    }

    /** {@link RigidBody}. */
    public static void register(ComponentRegistry registry) {
        registry.register(RigidBody.class, RigidBody::new);
    }
}
