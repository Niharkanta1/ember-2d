package dev.ember2d.kit.common;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import dev.ember2d.core.PublicApi;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Reuses entities that are spawned often, such as effects and pickups (F-021). {@link #free(int)} takes every component
 * but its {@link Pooled} marker off an entity and keeps them; {@link #obtain(float, float)} resets them, attaches them
 * again and moves the entity. A freed entity stays alive with only the marker, so no system sees it. After warm-up no new entities or component
 * instances are created. {@link Despawn#despawn} returns pooled entities here automatically.
 *
 * <pre>{@code
 * EntityPool sparks = EntityPool.ofPrefab(world(), ctx.prefabs(), "fx/spark", 16);
 * int spark = sparks.obtain(x, y);   // later: Despawn.despawn(world, spark), or a Lifetime on the prefab
 * }</pre>
 *
 * Like {@code world.add}, attaching is queued while a system runs: the components are on the entity after that system.
 */
@PublicApi
public final class EntityPool {

    private final World world;
    private final IntSupplier create;
    private final Consumer<List<Object>> reset;
    private final IntArray free = new IntArray();
    private final IntMap<List<Object>> detached = new IntMap<>();
    private final List<Object> scratch = new ArrayList<>();
    private int inUse;

    /**
     * @param create makes a new entity (with a {@code Transform})
     * @param reset puts a freed entity's component instances back to their starting values
     * @param warm entities created now, so play does not create them
     */
    public EntityPool(World world, IntSupplier create, Consumer<List<Object>> reset, int warm) {
        this.world = Objects.requireNonNull(world, "world");
        this.create = Objects.requireNonNull(create, "create");
        this.reset = Objects.requireNonNull(reset, "reset");
        if (warm < 0) {
            throw new IllegalArgumentException("warm must be >= 0 (was " + warm + ")");
        }
        for (int i = 0; i < warm; i++) {
            int e = make();
            inUse++;
            free(e);
        }
    }

    /** A pool of one prefab: new entities are spawned from it, and reused ones reset to its values. */
    public static EntityPool ofPrefab(World world, PrefabLibrary prefabs, String prefab, int warm) {
        Objects.requireNonNull(prefabs, "prefabs");
        Objects.requireNonNull(prefab, "prefab");
        return new EntityPool(world, () -> prefabs.spawn(world, prefab, 0f, 0f),
                components -> prefabs.reset(prefab, components), warm);
    }

    /** A free entity (or a new one when none is free), reset and moved to (x, y). */
    public int obtain(float x, float y) {
        int e;
        if (free.size > 0) {
            e = free.pop();
            List<Object> components = detached.get(e);
            reset.accept(components);
            for (int i = 0; i < components.size(); i++) {
                Object c = components.get(i);
                if (c instanceof Transform t) {
                    t.teleport(x, y);
                }
                world.add(e, c);
            }
            world.get(e, Pooled.class).inUse = true;   // the marker stays on while the entity is free
        } else {
            e = make();
            Transform t = world.get(e, Transform.class);
            if (t != null) {
                t.teleport(x, y);
            }
        }
        inUse++;
        return e;
    }

    /**
     * Takes every component off a pooled entity and keeps them for the next {@link #obtain}.
     *
     * @throws IllegalArgumentException if the entity is not from this pool or is already free
     */
    public void free(int entity) {
        Pooled pooled = world.get(entity, Pooled.class);
        if (pooled == null || pooled.pool != this || !pooled.inUse) {
            throw new IllegalArgumentException("Entity " + entity + " is not in use in this pool");
        }
        pooled.inUse = false;
        List<Object> components = detached.get(entity);
        if (components == null) {
            components = new ArrayList<>();
            detached.put(entity, components);
        }
        components.clear();
        scratch.clear();
        world.components(entity, scratch);
        for (int i = 0; i < scratch.size(); i++) {
            Object c = scratch.get(i);
            if (c != pooled) {
                components.add(c);
                world.remove(entity, c.getClass());
            }
        }
        free.add(entity);
        inUse--;
    }

    /** Whether the entity was made by this pool. */
    public boolean owns(int entity) {
        Pooled pooled = world.get(entity, Pooled.class);
        return pooled != null && pooled.pool == this;
    }

    /** Entities ready for {@link #obtain}. */
    public int freeCount() {
        return free.size;
    }

    /** Entities obtained and not freed yet. */
    public int inUse() {
        return inUse;
    }

    private int make() {
        int e = create.getAsInt();
        Pooled pooled = new Pooled(this);
        pooled.inUse = true;
        world.add(e, pooled);
        return e;
    }
}
