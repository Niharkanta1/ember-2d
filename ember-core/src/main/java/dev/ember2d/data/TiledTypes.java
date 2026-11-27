package dev.ember2d.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonValue.ValueType;
import com.badlogic.gdx.utils.JsonWriter.OutputType;
import com.badlogic.gdx.utils.SerializationException;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import dev.ember2d.core.PublicApi;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes the custom types of a Tiled project file from a game's components and prefabs (F-009, I-245), so Tiled offers
 * the right fields when a designer picks an object's class. Run at development time:
 * {@code java dev.ember2d.data.TiledTypes com.example.MyComponents assets assets/maps/game.tiled-project}.
 *
 * <p>Per registered component: a class usable as a property, one member per settable field with its default. Per
 * enum used by a field: an enum type. Per prefab: a class usable on objects, one class-typed member per component
 * holding the prefab's values. Other types and keys of the project file are kept; generated types keep their ids.
 */
@PublicApi
public final class TiledTypes {

    /**
     * Not generated: an entity's {@code Transform} comes from the object's own position and rotation, and class
     * defaults written into every object would overwrite them.
     */
    private static final String PLACEMENT = "Transform";
    /** Not generated either: set from a flipped tile object. */
    private static final java.util.Set<String> FROM_OBJECT = java.util.Set.of("Sprite.flipX", "Sprite.flipY");

    private TiledTypes() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException("Usage: TiledTypes <ComponentSetup class> <assets dir> "
                    + "<.tiled-project file>");
        }
        ComponentRegistry registry = new ComponentRegistry();
        setup(args[0]).register(registry);
        File project = new File(args[2]);
        Map<String, JsonValue> prefabs = readPrefabs(args[1]);
        String existing = project.isFile() ? read(project) : null;
        List<String> warnings = new ArrayList<>();
        String updated = update(registry, prefabs, existing, warnings);
        File dir = project.getAbsoluteFile().getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(project), StandardCharsets.UTF_8)) {
            w.write(updated);
        }
        for (String warning : warnings) {
            System.out.println("warning: " + warning);
        }
        System.out.println("Wrote " + registry.names().size() + " component type(s) and " + prefabs.size()
                + " prefab type(s) to " + project);
    }

    /**
     * The project JSON with the generated types merged in.
     *
     * @param prefabs prefab ID to its parsed file (in any order; written sorted by ID)
     * @param existingProject the current project JSON, or null to start a new one
     * @param warnings receives the types that look stale (not generated now, but a component or prefab name earlier)
     * @throws PrefabException listing every bad prefab
     */
    public static String update(ComponentRegistry registry, Map<String, JsonValue> prefabs, String existingProject,
            List<String> warnings) {
        JsonValue root = existingProject == null ? newProject() : parse(existingProject, "the project file");
        List<JsonValue> generated = new ArrayList<>();
        Map<String, Class<?>> enums = new LinkedHashMap<>();
        for (String name : registry.names()) {
            if (!name.equals(PLACEMENT)) {
                generated.add(componentType(registry, name, enums));
            }
        }
        for (Map.Entry<String, Class<?>> e : enums.entrySet()) {
            generated.add(enumType(e.getKey(), e.getValue()));
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonValue> e : new TreeMap<>(prefabs).entrySet()) {
            generated.add(prefabType(registry, e.getKey(), e.getValue(), problems));
        }
        if (!problems.isEmpty()) {
            throw new PrefabException("Tiled types: " + problems.size() + " problem(s) in the prefabs", problems);
        }
        merge(root, generated, warnings);
        return root.prettyPrint(OutputType.json, 0) + "\n";
    }

    // ---- types ----

    private static JsonValue componentType(ComponentRegistry registry, String name, Map<String, Class<?>> enums) {
        ComponentRegistry.Type type = registry.type(name);
        Object instance = type.create();
        JsonValue members = new JsonValue(ValueType.array);
        for (FieldSetter field : type.fields.values()) {
            if (!inTiled(name, field, instance)) {
                continue;
            }
            JsonValue m = object();
            m.addChild("name", new JsonValue(field.name));
            switch (field.kind) {
                case INT, LONG -> {
                    m.addChild("type", new JsonValue("int"));
                    m.addChild("value", new JsonValue(((Number) field.get(instance)).longValue()));
                }
                case FLOAT, DOUBLE -> {
                    m.addChild("type", new JsonValue("float"));
                    m.addChild("value", number(((Number) field.get(instance)).doubleValue()));
                }
                case BOOLEAN -> {
                    m.addChild("type", new JsonValue("bool"));
                    m.addChild("value", new JsonValue((Boolean) field.get(instance)));
                }
                case STRING -> {
                    m.addChild("type", new JsonValue("string"));
                    m.addChild("value", new JsonValue((String) field.get(instance)));
                }
                case ENUM -> {
                    m.addChild("type", new JsonValue("string"));
                    m.addChild("propertyType", new JsonValue(enumName(field.type, enums)));
                    m.addChild("value", new JsonValue(((Enum<?>) field.get(instance)).name()));
                }
                case COLOR -> {
                    m.addChild("type", new JsonValue("color"));
                    m.addChild("value", new JsonValue(tiledColor((Color) field.get(instance))));
                }
                default -> throw new IllegalStateException(field.kind.name());   // excluded by inTiled
            }
            members.addChild(m);
        }
        return classType(name, "property", members);
    }

    /**
     * Whether a field becomes a class member. libGDX's loader writes every member of an object's class into the
     * object's properties (as its default), so members must equal what spawning gives anyway: fields that only object
     * references can set, and fields that are null on a new instance, are left out (they are still set with plain
     * {@code Component.field} properties).
     */
    private static boolean inTiled(String component, FieldSetter field, Object instance) {
        if (FROM_OBJECT.contains(component + "." + field.name)) {
            return false;
        }
        return switch (field.kind) {
            case VECTOR2, RECTANGLE, FLOATS, ENTITY_REF -> false;
            case STRING, ENUM, COLOR -> field.get(instance) != null;
            default -> true;
        };
    }

    private static String enumName(Class<?> type, Map<String, Class<?>> enums) {
        String simple = type.getSimpleName();
        Class<?> known = enums.get(simple);
        if (known == null) {
            enums.put(simple, type);
            return simple;
        }
        if (known == type) {
            return simple;
        }
        enums.put(type.getName(), type);   // two enums with one simple name: the second uses its full name
        return type.getName();
    }

    private static JsonValue enumType(String name, Class<?> type) {
        JsonValue t = object();
        t.addChild("id", new JsonValue(0L));
        t.addChild("name", new JsonValue(name));
        t.addChild("type", new JsonValue("enum"));
        t.addChild("storageType", new JsonValue("string"));
        JsonValue values = new JsonValue(ValueType.array);
        for (Object constant : type.getEnumConstants()) {
            values.addChild(new JsonValue(((Enum<?>) constant).name()));
        }
        t.addChild("values", values);
        t.addChild("valuesAsFlags", new JsonValue(false));
        return t;
    }

    private static JsonValue prefabType(ComponentRegistry registry, String id, JsonValue prefab,
            List<String> problems) {
        JsonValue members = new JsonValue(ValueType.array);
        JsonValue components = prefab == null ? null : prefab.get("components");
        if (components == null || !components.isObject()) {
            problems.add(id + ": \"components\" must be an object");
            return classType(id, "object", members);
        }
        for (JsonValue comp = components.child; comp != null; comp = comp.next) {
            ComponentRegistry.Type type = registry.find(comp.name);
            if (type == null) {
                problems.add(id + ": unknown component \"" + comp.name + "\"; registered: " + registry.names());
                continue;
            }
            if (comp.name.equals(PLACEMENT)) {
                continue;   // position, rotation: from the object itself
            }
            JsonValue values = object();
            if (!comp.isObject()) {
                problems.add(id + ": " + comp.name + " must be an object");
                continue;
            }
            Object instance = type.create();
            Object defaults = type.create();
            boolean anyMember = false;
            for (FieldSetter field : type.fields.values()) {
                anyMember |= inTiled(comp.name, field, defaults);
            }
            for (JsonValue f = comp.child; f != null; f = f.next) {
                FieldSetter field = type.fields.get(f.name);
                if (field == null) {
                    String why = type.unsupported.containsKey(f.name) ? " (not settable from data: "
                            + type.unsupported.get(f.name) + ")" : "";
                    problems.add(id + ": " + comp.name + " has no field \"" + f.name + "\"" + why);
                    continue;
                }
                try {
                    field.set(instance, f);   // the same check as spawning
                } catch (RuntimeException e) {
                    problems.add(id + ": " + comp.name + "." + f.name + ": " + e.getMessage());
                    continue;
                }
                JsonValue v = tiledValue(field, field.get(instance));
                if (v != null && inTiled(comp.name, field, defaults)) {
                    values.addChild(f.name, v);
                }
            }
            if (!anyMember) {
                continue;   // libGDX skips classes without members and would fail on a member of that class
            }
            JsonValue m = object();
            m.addChild("name", new JsonValue(comp.name));
            m.addChild("type", new JsonValue("class"));
            m.addChild("propertyType", new JsonValue(comp.name));
            m.addChild("value", values);
            members.addChild(m);
        }
        return classType(id, "object", members);
    }

    /** A prefab value as Tiled stores it, or null for values only object references can give. */
    private static JsonValue tiledValue(FieldSetter field, Object value) {
        if (value == null) {
            return field.kind == FieldSetter.Kind.STRING || field.kind == FieldSetter.Kind.COLOR
                    ? new JsonValue("") : null;
        }
        return switch (field.kind) {
            case INT, LONG -> new JsonValue(((Number) value).longValue());
            case FLOAT, DOUBLE -> number(((Number) value).doubleValue());
            case BOOLEAN -> new JsonValue((Boolean) value);
            case STRING -> new JsonValue((String) value);
            case ENUM -> new JsonValue(((Enum<?>) value).name());
            case COLOR -> new JsonValue(tiledColor((Color) value));
            case VECTOR2, RECTANGLE, FLOATS, ENTITY_REF -> null;
        };
    }

    private static JsonValue classType(String name, String useAs, JsonValue members) {
        JsonValue t = object();
        t.addChild("id", new JsonValue(0L));
        t.addChild("name", new JsonValue(name));
        t.addChild("type", new JsonValue("class"));
        JsonValue use = new JsonValue(ValueType.array);
        use.addChild(new JsonValue(useAs));
        t.addChild("useAs", use);
        t.addChild("color", new JsonValue("object".equals(useAs) ? "#ff4a9de0" : "#ffa0a0a4"));
        t.addChild("drawFill", new JsonValue(true));
        t.addChild("members", members);
        return t;
    }

    /** Tiled's colour format: {@code #AARRGGBB}. */
    static String tiledColor(Color c) {
        int argb = Color.argb8888(c.a, c.r, c.g, c.b);
        return String.format(java.util.Locale.ROOT, "#%08x", argb);
    }

    private static JsonValue number(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? new JsonValue((long) v) : new JsonValue(v);
    }

    // ---- merging ----

    private static void merge(JsonValue root, List<JsonValue> generated, List<String> warnings) {
        JsonValue types = root.get("propertyTypes");
        if (types == null) {
            types = new JsonValue(ValueType.array);
            root.addChild("propertyTypes", types);
        }
        Map<String, JsonValue> byName = new LinkedHashMap<>();
        for (JsonValue t : generated) {
            byName.put(t.getString("name"), t);
        }
        long maxId = 0;
        for (JsonValue t = types.child; t != null; t = t.next) {
            maxId = Math.max(maxId, t.getLong("id", 0));
        }
        List<JsonValue> result = new ArrayList<>();
        for (JsonValue t = types.child; t != null; t = t.next) {
            String name = t.getString("name", "");
            JsonValue replacement = byName.remove(name);
            if (replacement != null) {
                setId(replacement, t.has("id") ? t.getLong("id") : ++maxId);
                result.add(replacement);
            } else {
                if (looksGenerated(t)) {
                    warnings.add("type \"" + name + "\" is no longer a component or prefab; delete it in Tiled if "
                            + "it is stale");
                }
                result.add(t);
            }
        }
        for (JsonValue t : byName.values()) {
            setId(t, ++maxId);
            result.add(t);
        }
        JsonValue fresh = new JsonValue(ValueType.array);
        for (JsonValue t : result) {
            t.parent = null;
            t.next = null;
            t.prev = null;
            fresh.addChild(t);
        }
        root.remove("propertyTypes");
        root.addChild("propertyTypes", fresh);
    }

    /** Classes with the generator's colours: a component (property) or prefab (object) class made earlier. */
    private static boolean looksGenerated(JsonValue t) {
        String color = t.getString("color", "");
        return "class".equals(t.getString("type", "")) && ("#ff4a9de0".equals(color) || "#ffa0a0a4".equals(color));
    }

    private static void setId(JsonValue type, long id) {
        type.get("id").set(id, null);
    }

    private static JsonValue newProject() {
        JsonValue root = object();
        root.addChild("automappingRulesFile", new JsonValue(""));
        root.addChild("commands", new JsonValue(ValueType.array));
        root.addChild("extensionsPath", new JsonValue("extensions"));
        JsonValue folders = new JsonValue(ValueType.array);
        folders.addChild(new JsonValue("."));
        root.addChild("folders", folders);
        root.addChild("propertyTypes", new JsonValue(ValueType.array));
        return root;
    }

    private static JsonValue object() {
        return new JsonValue(ValueType.object);
    }

    // ---- files ----

    /**
     * Every {@code prefab} entry of {@code <assetsDir>/manifest.json}, parsed, by prefab ID.
     *
     * @throws PrefabException listing every missing or unreadable prefab file
     */
    public static Map<String, JsonValue> readPrefabs(String assetsDir) throws IOException {
        File assets = new File(assetsDir);
        List<String> problems = new ArrayList<>();
        File manifestFile = new File(assets, "manifest.json");
        JsonValue manifest = parse(read(manifestFile), manifestFile.getPath());
        Map<String, JsonValue> prefabs = new LinkedHashMap<>();
        JsonValue entries = manifest.get("assets");
        if (entries == null) {
            return prefabs;
        }
        for (JsonValue e = entries.child; e != null; e = e.next) {
            if (!"prefab".equals(e.getString("type", ""))) {
                continue;
            }
            String id = e.getString("id", "?");
            File file = new File(assets, e.getString("path", ""));
            if (!file.isFile()) {
                problems.add(id + ": file " + file.getPath() + " is missing");
                continue;
            }
            try {
                prefabs.put(id, parse(read(file), file.getPath()));
            } catch (IllegalArgumentException ex) {
                problems.add(id + ": " + ex.getMessage());
            }
        }
        if (!problems.isEmpty()) {
            throw new PrefabException("Tiled types: " + problems.size() + " problem(s) in the prefabs", problems);
        }
        return prefabs;
    }

    private static JsonValue parse(String text, String what) {
        try {
            JsonValue v = new JsonReader().parse(text);
            if (v == null || !v.isObject()) {
                throw new IllegalArgumentException(what + " must hold a JSON object");
            }
            return v;
        } catch (SerializationException e) {
            throw new IllegalArgumentException(what + " is not valid JSON: " + e.getMessage(), e);
        }
    }

    private static String read(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static ComponentSetup setup(String className) {
        try {
            Class<?> type = ClassReflection.forName(className);
            Object setup = ClassReflection.newInstance(type);
            if (!(setup instanceof ComponentSetup s)) {
                throw new IllegalArgumentException(className + " does not implement " + ComponentSetup.class.getName());
            }
            return s;
        } catch (ReflectionException e) {
            throw new IllegalArgumentException("Cannot create " + className + " (needs a public no-argument "
                    + "constructor)", e);
        }
    }
}
