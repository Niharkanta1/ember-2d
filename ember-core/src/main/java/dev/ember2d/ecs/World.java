package dev.ember2d.ecs;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;
import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Entities, components and systems of one scene (F-003, ADR-004).
 *
 * <p>An entity is an {@code int} handle: a 20-bit slot index plus a 12-bit generation. Destroying an entity
 * bumps its slot's generation, so old handles and {@link EntityRef}s report not alive when the slot is reused.
 * Components are plain objects, one store per type. A component type is created from a class only if it is
 * registered ({@link #registerComponent(Class, Supplier)}; {@link Transform} is pre-registered); adding an
 * instance always works.
 *
 * <p>Structural changes (destroy, add, remove) apply immediately when no system is running. While a system
 * runs they are queued and applied after that system, so no system sees a half-changed entity. Creating an
 * entity is always immediate.
 */
@PublicApi
public final class World {

    /** "No entity". */
    public static final int NONE = -1;

    static final int INDEX_BITS = 20;
    static final int INDEX_MASK = (1 << INDEX_BITS) - 1;
    static final int GENERATION_MASK = (1 << (32 - INDEX_BITS)) - 1;
    /** Index {@code INDEX_MASK} is never used, so no handle equals {@link #NONE}. */
    static final int MAX_ENTITIES = INDEX_MASK;

    private static final Log LOG = Log.get("ecs");

    private enum OpType { DESTROY, ADD, REMOVE }

    private static final class Op implements Pool.Poolable {
        OpType type;
        int entity;
        Object component;
        Class<?> componentType;

        @Override
        public void reset() {
            component = null;
            componentType = null;
        }
    }

    private static final class Store {
        Object[] items = new Object[64];

        Object get(int index) {
            return index < items.length ? items[index] : null;
        }

        void set(int index, Object component) {
            if (index >= items.length) {
                Object[] grown = new Object[Math.max(index + 1, items.length * 2)];
                System.arraycopy(items, 0, grown, 0, items.length);
                items = grown;
            }
            items[index] = component;
        }

        Object remove(int index) {
            Object previous = get(index);
            if (previous != null) {
                items[index] = null;
            }
            return previous;
        }
    }

    private final boolean devMode;
    private final IntArray generations = new IntArray(true, 256);
    private final Bits alive = new Bits();
    private final IntArray freeSlots = new IntArray(false, 64);
    private final IntArray liveEntities = new IntArray(true, 256);
    private final ObjectMap<Class<?>, Store> stores = new ObjectMap<>();
    private final ObjectMap<Class<?>, Supplier<?>> factories = new ObjectMap<>();
    private final Array<Family> families = new Array<>(false, 16);
    @SuppressWarnings({"unchecked", "rawtypes"})
    private final Array<EntitySystem>[] systemsByPhase = new Array[Phase.values().length];
    private final ObjectSet<Class<?>> systemClasses = new ObjectSet<>();
    private final IntMap<String> names = new IntMap<>();
    private final ObjectIntMap<String> entitiesByName = new ObjectIntMap<>();
    private final ObjectMap<String, IntArray> entitiesByTag = new ObjectMap<>();
    private final Array<Op> pending = new Array<>(true, 64);
    private final Pool<Op> opPool = new Pool<>(64) {
        @Override
        protected Op newObject() {
            return new Op();
        }
    };
    private Store transforms;
    private boolean runningSystem;
    private boolean warnedDeadGet;

    public World(boolean devMode) {
        this.devMode = devMode;
        for (int i = 0; i < systemsByPhase.length; i++) {
            systemsByPhase[i] = new Array<>(true, 8);
        }
        registerComponent(Transform.class, Transform::new);
        transforms = store(Transform.class);
    }

    // ------------------------------------------------------------------------------------------------
    // Component registration
    // ------------------------------------------------------------------------------------------------

    /** Registers how to create a component type for {@link #add(int, Class)}. Re-registering replaces it. */
    public <T> void registerComponent(Class<T> type, Supplier<T> factory) {
        factories.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(factory, "factory"));
    }

    // ------------------------------------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------------------------------------

    /** Creates an entity with no components; always immediate. */
    public int create() {
        int index;
        if (freeSlots.size > 0) {
            index = freeSlots.pop();
        } else {
            index = generations.size;
            if (index >= MAX_ENTITIES) {
                throw new IllegalStateException("World is full: " + MAX_ENTITIES + " entities");
            }
            generations.add(0);
        }
        alive.set(index);
        int handle = handle(index, generations.get(index));
        liveEntities.add(handle);
        return handle;
    }

    /** Destroys an entity and all its components; queued while a system runs. Destroying a dead entity is a no-op. */
    public void destroy(int entity) {
        if (!isAlive(entity)) {
            return;
        }
        if (runningSystem) {
            queue(OpType.DESTROY, entity, null, null);
        } else {
            applyDestroy(entity);
        }
    }

    public boolean isAlive(int entity) {
        if (entity == NONE) {
            return false;
        }
        int index = entity & INDEX_MASK;
        return index < generations.size && alive.get(index) && generations.get(index) == (entity >>> INDEX_BITS);
    }

    /** A reference that becomes stale when the entity is destroyed. */
    public EntityRef ref(int entity) {
        requireAlive(entity, "ref");
        return new EntityRef(entity);
    }

    /** Number of live entities. */
    public int entityCount() {
        return liveEntities.size;
    }

    // ------------------------------------------------------------------------------------------------
    // Components
    // ------------------------------------------------------------------------------------------------

    /**
     * Creates a component of a registered type and adds it; returns it for configuration. Queued while a system
     * runs (the returned instance is attached after that system).
     *
     * @throws IllegalArgumentException if the type is not registered
     */
    public <T> T add(int entity, Class<T> type) {
        Supplier<?> factory = factories.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Component type " + type.getName() + " is not registered in this World. "
                    + "Call world.registerComponent(" + type.getSimpleName() + ".class, " + type.getSimpleName()
                    + "::new) or add an instance with world.add(entity, new " + type.getSimpleName() + "()).");
        }
        return add(entity, type.cast(factory.get()));
    }

    /** Adds (or replaces) a component instance; returns it. Queued while a system runs. */
    public <T> T add(int entity, T component) {
        Objects.requireNonNull(component, "component");
        requireAlive(entity, "add");
        if (runningSystem) {
            queue(OpType.ADD, entity, component, component.getClass());
        } else {
            applyAdd(entity, component);
        }
        return component;
    }

    /** Removes a component; queued while a system runs. Removing an absent component is a no-op. */
    public <T> void remove(int entity, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (!isAlive(entity)) {
            return;
        }
        if (runningSystem) {
            queue(OpType.REMOVE, entity, null, type);
        } else {
            applyRemove(entity, type);
        }
    }

    /** The component of this type, or null if absent or the entity is dead (logged once in dev mode). */
    public <T> T get(int entity, Class<T> type) {
        if (!isAlive(entity)) {
            if (devMode && !warnedDeadGet) {
                warnedDeadGet = true;
                LOG.warn("get(" + describe(entity) + ", " + type.getSimpleName() + ") on a dead entity returns null; "
                        + "store an EntityRef and check isAlive (logged once)");
            }
            return null;
        }
        Store store = stores.get(type);
        return store == null ? null : type.cast(store.get(entity & INDEX_MASK));
    }

    public <T> boolean has(int entity, Class<T> type) {
        if (!isAlive(entity)) {
            return false;
        }
        Store store = stores.get(type);
        return store != null && store.get(entity & INDEX_MASK) != null;
    }

    // ------------------------------------------------------------------------------------------------
    // Families and systems
    // ------------------------------------------------------------------------------------------------

    /** The cached family of entities that have all these component types. */
    public Family family(Class<?>... required) {
        return family(required, new Class<?>[0]);
    }

    Family family(Class<?>[] required, Class<?>[] excluded) {
        if (required.length == 0) {
            throw new IllegalArgumentException("A family needs at least one required component type");
        }
        for (Family family : families) {
            if (family.sameTypes(required, excluded)) {
                return family;
            }
        }
        Family family = new Family(this, required.clone(), excluded.clone());
        for (int i = 0; i < liveEntities.size; i++) {
            int entity = liveEntities.get(i);
            int index = entity & INDEX_MASK;
            family.update(entity, index, matches(family, index));
        }
        families.add(family);
        return family;
    }

    /**
     * Adds a system; systems run by phase, then in registration order.
     *
     * @throws IllegalArgumentException if a system of the same class is already registered, or its family
     *     belongs to another world
     */
    public void addSystem(EntitySystem system) {
        Objects.requireNonNull(system, "system");
        if (!families.contains(system.family(), true)) {
            throw new IllegalArgumentException(system.getClass().getName() + " uses a family of another World");
        }
        if (!systemClasses.add(system.getClass())) {
            throw new IllegalArgumentException("A system of class " + system.getClass().getName() + " is already registered");
        }
        system.attach(this);
        systemsByPhase[system.phase().ordinal()].add(system);
    }

    /**
     * Runs this phase's systems. The scene manager calls it for the active scene's world (I-070); call it
     * yourself only for worlds you run manually. {@code FIXED} first copies every transform's current state to
     * its previous state.
     */
    public void runPhase(Phase phase, Time time) {
        if (phase == Phase.FIXED) {
            storePreviousTransforms();
        }
        boolean pausable = phase == Phase.UPDATE || phase == Phase.LATE;
        float delta = switch (phase) {
            case FIXED -> time.fixedDelta();
            case RENDER -> time.unscaledDelta();
            default -> time.delta();
        };
        Array<EntitySystem> systems = systemsByPhase[phase.ordinal()];
        for (int s = 0; s < systems.size; s++) {
            EntitySystem system = systems.get(s);
            if (!system.enabled) {
                continue;
            }
            boolean unscaled = pausable && system.runsWhenPaused();
            if (pausable && time.paused() && !unscaled) {
                continue;
            }
            runSystem(system, unscaled ? time.unscaledDelta() : delta);
        }
        applyPending();
    }

    // ------------------------------------------------------------------------------------------------
    // Names and tags
    // ------------------------------------------------------------------------------------------------

    /** Names an entity (dev tools, lookups); null removes the name. A name points to the entity named last. */
    public void setName(int entity, String name) {
        requireAlive(entity, "setName");
        String previous = names.remove(entity & INDEX_MASK);
        if (previous != null && entitiesByName.get(previous, NONE) == entity) {
            entitiesByName.remove(previous, NONE);
        }
        if (name != null) {
            names.put(entity & INDEX_MASK, name);
            entitiesByName.put(name, entity);
        }
    }

    /** The entity's name, or null. */
    public String name(int entity) {
        return isAlive(entity) ? names.get(entity & INDEX_MASK) : null;
    }

    /** The live entity with this name, or {@link #NONE}. */
    public int findByName(String name) {
        int entity = entitiesByName.get(name, NONE);
        return isAlive(entity) ? entity : NONE;
    }

    /** Adds a tag to an entity (immediate; tags do not affect families). */
    public void tag(int entity, String tag) {
        requireAlive(entity, "tag");
        IntArray tagged = entitiesByTag.get(tag);
        if (tagged == null) {
            tagged = new IntArray(true, 8);
            entitiesByTag.put(tag, tagged);
        }
        if (!tagged.contains(entity)) {
            tagged.add(entity);
        }
    }

    public void untag(int entity, String tag) {
        IntArray tagged = entitiesByTag.get(tag);
        if (tagged != null) {
            tagged.removeValue(entity);
        }
    }

    public boolean hasTag(int entity, String tag) {
        IntArray tagged = entitiesByTag.get(tag);
        return tagged != null && isAlive(entity) && tagged.contains(entity);
    }

    /** Adds every live entity with this tag to {@code out}, in tagging order. */
    public void findByTag(String tag, IntArray out) {
        IntArray tagged = entitiesByTag.get(tag);
        if (tagged != null) {
            out.addAll(tagged);
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------------------------------------

    /**
     * Destroys every entity and removes every system (scene exit). Registered component types and families
     * remain usable; existing handles and references become stale.
     *
     * @throws IllegalStateException if called while a system runs
     */
    public void clear() {
        if (runningSystem) {
            throw new IllegalStateException("World.clear() cannot run inside a system");
        }
        while (liveEntities.size > 0) {
            applyDestroy(liveEntities.peek());
        }
        pending.clear();
        for (Array<EntitySystem> systems : systemsByPhase) {
            systems.clear();
        }
        systemClasses.clear();
        for (Family family : families) {
            family.clear();
        }
        entitiesByTag.clear();
        entitiesByName.clear();
        names.clear();
    }

    static String describe(int entity) {
        return entity == NONE ? "none" : "entity " + (entity & INDEX_MASK) + "#" + (entity >>> INDEX_BITS);
    }

    // ------------------------------------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------------------------------------

    private static int handle(int index, int generation) {
        return (generation << INDEX_BITS) | index;
    }

    private void requireAlive(int entity, String call) {
        if (!isAlive(entity)) {
            throw new IllegalArgumentException(call + "() on a dead or unknown entity (" + describe(entity) + ")");
        }
    }

    private Store store(Class<?> type) {
        Store store = stores.get(type);
        if (store == null) {
            store = new Store();
            stores.put(type, store);
        }
        return store;
    }

    private void runSystem(EntitySystem system, float delta) {
        runningSystem = true;
        try {
            system.begin();
            IntArray entities = system.family().entities();
            for (int i = 0; i < entities.size; i++) {
                system.process(entities.get(i), delta);
            }
            system.end();
        } finally {
            runningSystem = false;
        }
        applyPending();
    }

    private void storePreviousTransforms() {
        for (int i = 0; i < liveEntities.size; i++) {
            Transform transform = (Transform) transforms.get(liveEntities.get(i) & INDEX_MASK);
            if (transform != null) {
                transform.previous.set(transform.position);
                transform.previousRotation = transform.rotation;
            }
        }
    }

    private void queue(OpType type, int entity, Object component, Class<?> componentType) {
        Op op = opPool.obtain();
        op.type = type;
        op.entity = entity;
        op.component = component;
        op.componentType = componentType;
        pending.add(op);
    }

    private void applyPending() {
        for (int i = 0; i < pending.size; i++) {
            Op op = pending.get(i);
            if (isAlive(op.entity)) {
                switch (op.type) {
                    case DESTROY -> applyDestroy(op.entity);
                    case ADD -> applyAdd(op.entity, op.component);
                    case REMOVE -> applyRemove(op.entity, op.componentType);
                }
            }
            opPool.free(op);
        }
        pending.clear();
    }

    private void applyAdd(int entity, Object component) {
        int index = entity & INDEX_MASK;
        store(component.getClass()).set(index, component);
        updateFamilies(entity, index);
    }

    private void applyRemove(int entity, Class<?> type) {
        Store store = stores.get(type);
        int index = entity & INDEX_MASK;
        if (store != null && store.remove(index) != null) {
            updateFamilies(entity, index);
        }
    }

    private void applyDestroy(int entity) {
        int index = entity & INDEX_MASK;
        for (Store store : stores.values()) {
            store.remove(index);
        }
        for (Family family : families) {
            family.update(entity, index, false);
        }
        String name = names.remove(index);
        if (name != null && entitiesByName.get(name, NONE) == entity) {
            entitiesByName.remove(name, NONE);
        }
        for (IntArray tagged : entitiesByTag.values()) {
            tagged.removeValue(entity);
        }
        alive.clear(index);
        liveEntities.removeValue(entity);
        generations.set(index, (generations.get(index) + 1) & GENERATION_MASK);
        freeSlots.add(index);
    }

    private void updateFamilies(int entity, int index) {
        for (Family family : families) {
            family.update(entity, index, matches(family, index));
        }
    }

    private boolean matches(Family family, int index) {
        for (Class<?> type : family.required) {
            Store store = stores.get(type);
            if (store == null || store.get(index) == null) {
                return false;
            }
        }
        for (Class<?> type : family.excluded) {
            Store store = stores.get(type);
            if (store != null && store.get(index) != null) {
                return false;
            }
        }
        return true;
    }
}
