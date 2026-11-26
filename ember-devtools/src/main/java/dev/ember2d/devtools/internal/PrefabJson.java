package dev.ember2d.devtools.internal;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.FieldInfo;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * "Copy as prefab JSON" (F-018, I-160): an entity's registered components with the field values that differ from a new
 * component, in prefab format (F-010). Components the registry does not know are left out, and so are the
 * {@code Transform} fields that are the spawn position.
 */
public final class PrefabJson {

    private static final Set<String> SPAWN_FIELDS = Set.of("position", "previous", "previousRotation");

    private PrefabJson() {
    }

    public static String of(World world, int entity, ComponentRegistry registry) {
        Map<Class<?>, String> names = new HashMap<>();
        List<Class<?>> types = registry.types();
        List<String> nameList = new ArrayList<>(registry.names());
        for (int i = 0; i < types.size(); i++) {
            names.put(types.get(i), nameList.get(i));
        }
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("version", new JsonValue(1));
        JsonValue components = new JsonValue(JsonValue.ValueType.object);
        root.addChild("components", components);
        for (Class<?> type : types) {
            Object component = world.get(entity, type);
            if (component == null) {
                continue;
            }
            String name = names.get(type);
            JsonValue fields = new JsonValue(JsonValue.ValueType.object);
            for (FieldInfo info : registry.fields(name)) {
                if (type == Transform.class && SPAWN_FIELDS.contains(info.name())) {
                    continue;
                }
                Object value = read(component, info.name());
                if (same(value, info.defaultValue())) {
                    continue;
                }
                JsonValue json = json(value);
                if (json != null) {
                    fields.addChild(info.name(), json);
                }
            }
            components.addChild(name, fields);
        }
        return root.prettyPrint(JsonWriter.OutputType.json, 100);
    }

    private static Object read(Object component, String field) {
        try {
            return component.getClass().getField(field).get(component);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + component.getClass().getSimpleName() + "." + field, e);
        }
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof float[] x && b instanceof float[] y) {
            return Arrays.equals(x, y);
        }
        return Objects.equals(a, b);
    }

    /** The JSON for a value, or null for a type prefabs cannot hold here (entity references). */
    static JsonValue json(Object value) {
        if (value == null) {
            return new JsonValue(JsonValue.ValueType.nullValue);
        } else if (value instanceof Boolean b) {
            return new JsonValue(b);
        } else if (value instanceof Integer || value instanceof Long) {
            return new JsonValue(((Number) value).longValue());
        } else if (value instanceof Float || value instanceof Double) {
            return new JsonValue(((Number) value).doubleValue());
        } else if (value instanceof String s) {
            return new JsonValue(s);
        } else if (value instanceof Enum<?> e) {
            return new JsonValue(e.name());
        } else if (value instanceof Color c) {
            return new JsonValue("#" + c);
        } else if (value instanceof Vector2 v) {
            return array(v.x, v.y);
        } else if (value instanceof Rectangle r) {
            JsonValue o = new JsonValue(JsonValue.ValueType.object);
            o.addChild("x", new JsonValue(r.x));
            o.addChild("y", new JsonValue(r.y));
            o.addChild("width", new JsonValue(r.width));
            o.addChild("height", new JsonValue(r.height));
            return o;
        } else if (value instanceof float[] a) {
            return array(a);
        }
        return null;
    }

    private static JsonValue array(float... values) {
        JsonValue a = new JsonValue(JsonValue.ValueType.array);
        for (float v : values) {
            a.addChild(new JsonValue(v));
        }
        return a;
    }
}
