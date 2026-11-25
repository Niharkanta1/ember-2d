package dev.ember2d.ecs;

import dev.ember2d.core.PublicApi;

/**
 * A stored reference to an entity (F-003). Store {@code EntityRef}s in components instead of raw handles:
 * after the entity is destroyed — even if its slot is reused — {@link #isAlive(World)} returns false and
 * {@link #entity(World)} returns -1. A reference is only meaningful for the world that created it.
 */
@PublicApi
public final class EntityRef {

    private final int handle;

    EntityRef(int handle) {
        this.handle = handle;
    }

    /** Whether the referenced entity still exists in this world. */
    public boolean isAlive(World world) {
        return world.isAlive(handle);
    }

    /** The entity handle, or -1 if the entity no longer exists. */
    public int entity(World world) {
        return world.isAlive(handle) ? handle : World.NONE;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntityRef ref && ref.handle == handle;
    }

    @Override
    public int hashCode() {
        return handle;
    }

    @Override
    public String toString() {
        return "EntityRef[" + World.describe(handle) + "]";
    }
}
