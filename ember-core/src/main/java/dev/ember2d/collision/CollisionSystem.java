package dev.ember2d.collision;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.Objects;

/**
 * The fixed-step work of a {@link Collision} (F-006): re-syncs its spatial hash with entities created, destroyed or
 * moved outside the collision API, then updates triggers and queues {@link TriggerEntered} / {@link TriggerExited}.
 * Register it after the game's movement systems.
 */
@PublicApi
public final class CollisionSystem extends EntitySystem {

    private final Collision collision;

    public CollisionSystem(World world, Collision collision) {
        super(Phase.FIXED, world.family(Transform.class, Collider.class));
        this.collision = Objects.requireNonNull(collision, "collision");
    }

    @Override
    protected void process(int entity, float delta) {
        // all work happens once per step in end()
    }

    @Override
    protected void end() {
        collision.step();
    }
}
