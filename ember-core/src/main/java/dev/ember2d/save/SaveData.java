package dev.ember2d.save;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.SerializationException;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An ordered key/value tree that is saved as JSON (F-013): booleans, ints, finite floats, strings, nested trees
 * ({@link #child}) and lists of trees ({@link #list}). Getters take a default, which they return when the key is
 * missing or holds another kind of value; numbers convert between int and float. Game objects go in through a
 * {@link SaveCodec}. Not thread-safe.
 */
@PublicApi
public final class SaveData {

    private final Map<String, Object> values = new LinkedHashMap<>();

    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** Removes a key; returns whether it was there. */
    public boolean remove(String key) {
        return values.remove(key) != null;
    }

    /** The keys in insertion order (a read-only view). */
    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void clear() {
        values.clear();
    }

    public boolean getBool(String key, boolean defaultValue) {
        return values.get(key) instanceof Boolean b ? b : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        Object v = values.get(key);
        if (v instanceof Integer i) {
            return i;
        }
        return v instanceof Float f ? Math.round(f) : defaultValue;
    }

    public float getFloat(String key, float defaultValue) {
        Object v = values.get(key);
        if (v instanceof Float f) {
            return f;
        }
        return v instanceof Integer i ? i : defaultValue;
    }

    public String getString(String key, String defaultValue) {
        return values.get(key) instanceof String s ? s : defaultValue;
    }

    public SaveData set(String key, boolean value) {
        values.put(checkKey(key), value);
        return this;
    }

    public SaveData set(String key, int value) {
        values.put(checkKey(key), value);
        return this;
    }

    /** @throws IllegalArgumentException for NaN or infinity (JSON has no such numbers) */
    public SaveData set(String key, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(key + ": must be a finite number (was " + value + ")");
        }
        values.put(checkKey(key), value);
        return this;
    }

    public SaveData set(String key, String value) {
        values.put(checkKey(key), Objects.requireNonNull(value, "value"));
        return this;
    }

    /** Stores a deep copy of a tree under a key (replacing what was there). */
    public SaveData set(String key, SaveData value) {
        values.put(checkKey(key), Objects.requireNonNull(value, "value").copy());
        return this;
    }

    /**
     * The nested tree under a key, created if missing.
     *
     * @throws IllegalStateException if the key holds another kind of value
     */
    public SaveData child(String key) {
        Object v = values.get(key);
        if (v == null) {
            SaveData child = new SaveData();
            values.put(checkKey(key), child);
            return child;
        }
        if (v instanceof SaveData child) {
            return child;
        }
        throw new IllegalStateException(key + " holds " + kind(v) + ", not an object");
    }

    /**
     * The live list of trees under a key, created if missing.
     *
     * @throws IllegalStateException if the key holds another kind of value
     */
    @SuppressWarnings("unchecked")
    public List<SaveData> list(String key) {
        Object v = values.get(key);
        if (v == null) {
            List<SaveData> list = new ArrayList<>();
            values.put(checkKey(key), list);
            return list;
        }
        if (v instanceof List<?> list) {
            return (List<SaveData>) list;
        }
        throw new IllegalStateException(key + " holds " + kind(v) + ", not a list");
    }

    /** Writes a game object through its codec as a nested tree (replacing what was there). */
    public <T> SaveData put(String key, T value, SaveCodec<T> codec) {
        SaveData child = new SaveData();
        codec.write(Objects.requireNonNull(value, "value"), child);
        values.put(checkKey(key), child);
        return this;
    }

    /** Reads a game object through its codec, or the default when the key holds no tree. */
    public <T> T get(String key, SaveCodec<T> codec, T defaultValue) {
        return values.get(key) instanceof SaveData child ? codec.read(child) : defaultValue;
    }

    /** A deep copy. */
    public SaveData copy() {
        return parse(toJson());
    }

    /** Pretty-printed JSON with keys in insertion order. */
    public String toJson() {
        StringBuilder b = new StringBuilder(128);
        write(b, this, 0);
        return b.append('\n').toString();
    }

    /**
     * Parses JSON written by {@link #toJson()} (or by hand): the root must be an object; lists hold objects only;
     * whole numbers become ints when they fit.
     *
     * @throws IllegalArgumentException with the reason when the text is not such JSON
     */
    public static SaveData parse(String json) {
        JsonValue root;
        try {
            root = new JsonReader().parse(Objects.requireNonNull(json, "json"));
        } catch (SerializationException e) {
            throw new IllegalArgumentException("not valid JSON: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("the root must be a JSON object");
        }
        return read(root, "");
    }

    private static SaveData read(JsonValue object, String path) {
        SaveData data = new SaveData();
        for (JsonValue v = object.child; v != null; v = v.next) {
            String at = path + v.name;
            if (v.name == null || v.name.isEmpty()) {
                throw new IllegalArgumentException(path + "(empty key): keys must not be empty");
            }
            if (v.isObject()) {
                data.values.put(v.name, read(v, at + "."));
            } else if (v.isArray()) {
                List<SaveData> list = new ArrayList<>();
                int i = 0;
                for (JsonValue item = v.child; item != null; item = item.next, i++) {
                    if (!item.isObject()) {
                        throw new IllegalArgumentException(at + "[" + i + "]: lists hold objects only");
                    }
                    list.add(read(item, at + "[" + i + "]."));
                }
                data.values.put(v.name, list);
            } else if (v.isBoolean()) {
                data.values.put(v.name, v.asBoolean());
            } else if (v.isLong()) {
                long l = v.asLong();
                data.values.put(v.name, l == (int) l ? (Object) (int) l : (Object) (float) l);
            } else if (v.isDouble()) {
                data.values.put(v.name, v.asFloat());
            } else if (v.isString()) {
                data.values.put(v.name, v.asString());
            } else {
                throw new IllegalArgumentException(at + ": null is not supported");
            }
        }
        return data;
    }

    private static void write(StringBuilder b, SaveData data, int indent) {
        if (data.values.isEmpty()) {
            b.append("{}");
            return;
        }
        b.append("{\n");
        int n = 0;
        for (Map.Entry<String, Object> e : data.values.entrySet()) {
            pad(b, indent + 1);
            quote(b, e.getKey());
            b.append(": ");
            Object v = e.getValue();
            if (v instanceof SaveData child) {
                write(b, child, indent + 1);
            } else if (v instanceof List<?> list) {
                if (list.isEmpty()) {
                    b.append("[]");
                } else {
                    b.append("[\n");
                    for (int i = 0; i < list.size(); i++) {
                        pad(b, indent + 2);
                        write(b, (SaveData) list.get(i), indent + 2);
                        b.append(i + 1 < list.size() ? ",\n" : "\n");
                    }
                    pad(b, indent + 1);
                    b.append(']');
                }
            } else if (v instanceof String s) {
                quote(b, s);
            } else {
                b.append(v);   // Boolean, Integer, Float (Float.toString always has a '.' or an exponent)
            }
            b.append(++n < data.values.size() ? ",\n" : "\n");
        }
        pad(b, indent);
        b.append('}');
    }

    private static void pad(StringBuilder b, int indent) {
        for (int i = 0; i < indent; i++) {
            b.append("  ");
        }
    }

    private static void quote(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        b.append('"');
    }

    private static String checkKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("A key must be a non-empty string");
        }
        return key;
    }

    private static String kind(Object v) {
        if (v instanceof SaveData) {
            return "an object";
        }
        if (v instanceof List) {
            return "a list";
        }
        return v instanceof String ? "a string" : v instanceof Boolean ? "a boolean" : "a number";
    }

    @Override
    public String toString() {
        return toJson();
    }
}
