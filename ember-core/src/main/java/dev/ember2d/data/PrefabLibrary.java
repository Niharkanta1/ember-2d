package dev.ember2d.data;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * JSON prefabs (F-010), available as {@code context.prefabs()}. A prefab is a manifest entry of type {@code prefab}
 * whose ID is the prefab ID; it must be loaded (with its asset group) before use. Its file is parsed, migrated to the
 * current version and validated on first use, then cached.
 *
 * <pre>{@code { "version": 1, "components": { "Health": { "max": 3 }, "Sprite": { "regionId": "tex.slime" } } } }</pre>
 *
 * Spawning creates the entity, a {@code Transform} at the given position (prefab values for rotation and scale
 * apply), every prefab component with the prefab's values, then the overrides; then it publishes
 * {@link EntitySpawned} (immediately, so scene-scoped subscribers see it).
 */
@PublicApi
public final class PrefabLibrary {

    /** One component of a parsed prefab: its type and the JSON values to apply. */
    private record ComponentData(ComponentRegistry.Type type, JsonValue values) {
    }

    /** A parsed prefab. */
    record Prefab(String id, String file, List<ComponentData> components) {
        boolean has(String component) {
            for (ComponentData c : components) {
                if (c.type().name.equals(component)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** An entity being spawned: its components by name, for overrides. */
    static final class Instance {
        final int entity;
        final Prefab prefab;
        final Map<String, Object> components = new LinkedHashMap<>();
        final Map<String, ComponentRegistry.Type> types = new LinkedHashMap<>();

        Instance(int entity, Prefab prefab) {
            this.entity = entity;
            this.prefab = prefab;
        }
    }

    private final ComponentRegistry registry;
    private final Assets assets;
    private final EventBus events;
    private final Map<String, Prefab> cache = new HashMap<>();
    private final Map<Integer, UnaryOperator<JsonValue>> migrations = new HashMap<>();
    private int version = 1;

    public PrefabLibrary(ComponentRegistry registry, Assets assets, EventBus events) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** The component registry prefabs are built from. */
    public ComponentRegistry registry() {
        return registry;
    }

    /** Sets the game's prefab data version (default 1); older files are migrated, newer files rejected. */
    public void setVersion(int version) {
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1 (was " + version + ")");
        }
        this.version = version;
        cache.clear();
    }

    /** Registers the step from {@code fromVersion} to {@code fromVersion + 1}; it may change and return the JSON. */
    public void addMigration(int fromVersion, UnaryOperator<JsonValue> migration) {
        migrations.put(fromVersion, Objects.requireNonNull(migration, "migration"));
        cache.clear();
    }

    /** True if the manifest lists the prefab. */
    public boolean contains(String prefab) {
        return assets.contains(prefab);
    }

    /**
     * Spawns a prefab with its {@code Transform} at (x, y).
     *
     * @throws PrefabException if the prefab is invalid
     * @throws IllegalArgumentException if the manifest does not list it
     */
    public int spawn(World world, String prefab, float x, float y) {
        return spawn(world, prefab, x, y, Map.of());
    }

    /**
     * Spawns a prefab with overrides keyed {@code Component.field} or a bare field name (unique among the prefab's
     * components); values as for Tiled properties ({@code Integer}, {@code Float}, {@code Boolean}, {@code String},
     * {@code Color}, {@code Vector2}, {@code Rectangle}, {@code float[]}, {@code EntityRef}).
     *
     * @throws PrefabException if the prefab or an override is invalid (the entity is not created)
     */
    public int spawn(World world, String prefab, float x, float y, Map<String, Object> overrides) {
        Instance instance = create(world, prefab, x, y);
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            override(instance, e.getKey(), e.getValue(), problems, "override \"" + e.getKey() + "\"");
        }
        if (!problems.isEmpty()) {
            world.destroy(instance.entity);
            throw new PrefabException("Cannot spawn prefab \"" + prefab + "\"", problems);
        }
        finish(world, instance);
        return instance.entity;
    }

    // ---- used by MapSpawner (two passes) ----

    /** Pass 1: creates the entity with its prefab components and its Transform at (x, y). */
    Instance create(World world, String prefabId, float x, float y) {
        Prefab prefab = prefab(prefabId);
        int entity = world.create();
        Instance instance = new Instance(entity, prefab);
        boolean hasTransform = false;
        for (ComponentData data : prefab.components()) {
            Object component = data.type().create();
            apply(data, component);
            if (component instanceof Transform t) {
                t.teleport(x, y);
                t.previousRotation = t.rotation;
                hasTransform = true;
            }
            instance.components.put(data.type().name, component);
            instance.types.put(data.type().name, data.type());
        }
        if (!hasTransform) {
            Transform t = new Transform();
            t.teleport(x, y);
            instance.components.put("Transform", t);
            instance.types.put("Transform", registry.type("Transform"));
        }
        for (Object component : instance.components.values()) {
            world.add(entity, component);
        }
        return instance;
    }

    /** Sets one override; problems are added with {@code where} in front. Returns true if it was set. */
    boolean override(Instance instance, String key, Object value, List<String> problems, String where) {
        String component;
        String field;
        int dot = key.indexOf('.');
        if (dot > 0) {
            component = key.substring(0, dot);
            field = key.substring(dot + 1);
            if (!instance.components.containsKey(component)) {
                problems.add(where + ": prefab \"" + instance.prefab.id() + "\" has no component \"" + component
                        + "\" (it has " + instance.components.keySet() + ")");
                return false;
            }
        } else {
            field = key;
            List<String> owners = new ArrayList<>();
            for (Map.Entry<String, ComponentRegistry.Type> e : instance.types.entrySet()) {
                if (e.getValue().fields.containsKey(key)) {
                    owners.add(e.getKey());
                }
            }
            if (owners.isEmpty()) {
                problems.add(where + ": no component of prefab \"" + instance.prefab.id() + "\" has a field \"" + key
                        + "\" (components " + instance.components.keySet() + ")");
                return false;
            }
            if (owners.size() > 1) {
                problems.add(where + ": field \"" + key + "\" is ambiguous; write one of "
                        + owners.stream().map(o -> o + "." + key).toList());
                return false;
            }
            component = owners.get(0);
        }
        ComponentRegistry.Type type = instance.types.get(component);
        FieldSetter setter = type.fields.get(field);
        if (setter == null) {
            problems.add(where + ": " + unknownField(type, field));
            return false;
        }
        try {
            setter.set(instance.components.get(component), value);
            if (setter.kind == FieldSetter.Kind.VECTOR2 && field.equals("position")
                    && instance.components.get(component) instanceof Transform t) {
                t.previous.set(t.position);   // a moved spawn must not interpolate from the old position
            }
            return true;
        } catch (IllegalArgumentException e) {
            problems.add(where + ": " + component + "." + field + ": " + e.getMessage());
            return false;
        }
    }

    /** The field setter a key resolves to on this instance, or null (used to convert object references). */
    FieldSetter setterFor(Instance instance, String key) {
        int dot = key.indexOf('.');
        if (dot > 0) {
            ComponentRegistry.Type type = instance.types.get(key.substring(0, dot));
            return type == null ? null : type.fields.get(key.substring(dot + 1));
        }
        FieldSetter found = null;
        for (ComponentRegistry.Type type : instance.types.values()) {
            FieldSetter s = type.fields.get(key);
            if (s != null) {
                if (found != null) {
                    return null;
                }
                found = s;
            }
        }
        return found;
    }

    /** Last step: publishes {@link EntitySpawned}. */
    void finish(World world, Instance instance) {
        events.publish(new EntitySpawned(instance.entity, instance.prefab.id()));
    }

    // ---- parsing ----

    Prefab prefab(String id) {
        Prefab cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        if (!assets.contains(id)) {
            throw new IllegalArgumentException("Unknown prefab \"" + id + "\": not in the asset manifest");
        }
        String file = assets.path(id);
        JsonValue loaded = assets.get(id, JsonValue.class);
        JsonValue root = new JsonReader().parse(loaded.toJson(JsonWriter.OutputType.json));   // migrations may edit it
        List<String> problems = new ArrayList<>();
        root = migrate(root, file, problems);
        List<ComponentData> components = new ArrayList<>();
        if (root != null) {
            for (JsonValue field = root.child; field != null; field = field.next) {
                if (!field.name.equals("version") && !field.name.equals("components")) {
                    problems.add(file + ": unknown field \"" + field.name + "\" (expected version, components)");
                }
            }
            JsonValue comps = root.get("components");
            if (comps == null || !comps.isObject()) {
                problems.add(file + ": \"components\" must be an object of component name to field values");
            } else {
                for (JsonValue c = comps.child; c != null; c = c.next) {
                    ComponentData data = component(file, c, problems);
                    if (data != null) {
                        components.add(data);
                    }
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new PrefabException("Invalid prefab \"" + id + "\"", problems);
        }
        Prefab prefab = new Prefab(id, file, List.copyOf(components));
        cache.put(id, prefab);
        return prefab;
    }

    private JsonValue migrate(JsonValue root, String file, List<String> problems) {
        if (!root.isObject()) {
            problems.add(file + ": must be a JSON object");
            return null;
        }
        JsonValue v = root.get("version");
        if (v == null || !v.isLong()) {
            problems.add(file + ": \"version\" is required (a whole number; current is " + version + ")");
            return null;
        }
        int fileVersion = v.asInt();
        if (fileVersion > version) {
            problems.add(file + ": version " + fileVersion + " is newer than the game's prefab version " + version);
            return null;
        }
        while (fileVersion < version) {
            UnaryOperator<JsonValue> step = migrations.get(fileVersion);
            if (step == null) {
                problems.add(file + ": no migration from version " + fileVersion + " to " + (fileVersion + 1));
                return null;
            }
            root = step.apply(root);
            fileVersion++;
            JsonValue vv = root.get("version");
            if (vv != null) {
                vv.set(fileVersion, null);
            } else {
                root.addChild("version", new JsonValue(fileVersion));
            }
        }
        return root;
    }

    private ComponentData component(String file, JsonValue c, List<String> problems) {
        ComponentRegistry.Type type = registry.find(c.name);
        if (type == null) {
            problems.add(file + ": unknown component \"" + c.name + "\" (registered: " + registry.names() + ")");
            return null;
        }
        if (!c.isObject()) {
            problems.add(file + ": " + c.name + ": must be an object of field values");
            return null;
        }
        Object probe = type.create();
        int before = problems.size();
        for (JsonValue f = c.child; f != null; f = f.next) {
            FieldSetter setter = type.fields.get(f.name);
            if (setter == null) {
                problems.add(file + ": " + c.name + ": " + unknownField(type, f.name));
                continue;
            }
            try {
                setter.set(probe, f);
            } catch (IllegalArgumentException e) {
                problems.add(file + ": " + c.name + "." + f.name + ": " + e.getMessage());
            }
        }
        return problems.size() == before ? new ComponentData(type, c) : null;
    }

    private static void apply(ComponentData data, Object component) {
        for (JsonValue f = data.values().child; f != null; f = f.next) {
            data.type().fields.get(f.name).set(component, f);
        }
    }

    private static String unknownField(ComponentRegistry.Type type, String field) {
        String unsupported = type.unsupported.get(field);
        if (unsupported != null) {
            return "field \"" + field + "\" of " + type.name + " has type " + unsupported
                    + ", which cannot be set from data";
        }
        return "unknown field \"" + field + "\" of " + type.name + " (fields: " + type.fields.keySet() + ")";
    }
}
