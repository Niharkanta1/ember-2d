package dev.ember2d.data;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import dev.ember2d.ecs.EntityRef;

/**
 * Sets one public component field from data (F-010): a {@link JsonValue} from a prefab file, or a Java value from a
 * Tiled property or code ({@code Integer}, {@code Float}, {@code Boolean}, {@code String}, {@code Color},
 * {@code Vector2}, {@code Rectangle}, {@code float[]}, {@link EntityRef}). Final {@code Color}, {@code Vector2} and
 * {@code Rectangle} fields are set in place. Conversion problems throw {@link IllegalArgumentException} with a short
 * reason; callers add file, object and field.
 */
final class FieldSetter {

    enum Kind { INT, LONG, FLOAT, DOUBLE, BOOLEAN, STRING, ENUM, COLOR, VECTOR2, RECTANGLE, FLOATS, ENTITY_REF }

    final String name;
    final Class<?> type;
    final Kind kind;
    private final Field field;
    private final boolean inPlace;

    private FieldSetter(Field field, Kind kind) {
        this.field = field;
        this.name = field.getName();
        this.type = field.getType();
        this.kind = kind;
        this.inPlace = field.isFinal();
    }

    /** A setter for the field, or null if data cannot set it (unsupported type, or final and not set in place). */
    static FieldSetter of(Field field) {
        if (field.isStatic() || !field.isPublic() || field.isSynthetic()) {
            return null;
        }
        Kind kind = kind(field.getType());
        if (kind == null) {
            return null;
        }
        boolean mutableValue = kind == Kind.COLOR || kind == Kind.VECTOR2 || kind == Kind.RECTANGLE;
        if (field.isFinal() && !mutableValue) {
            return null;
        }
        return new FieldSetter(field, kind);
    }

    /** True if data could set a field of this type (used to explain unsupported fields). */
    static boolean supported(Class<?> type) {
        return kind(type) != null;
    }

    private static Kind kind(Class<?> t) {
        if (t == int.class) {
            return Kind.INT;
        } else if (t == long.class) {
            return Kind.LONG;
        } else if (t == float.class) {
            return Kind.FLOAT;
        } else if (t == double.class) {
            return Kind.DOUBLE;
        } else if (t == boolean.class) {
            return Kind.BOOLEAN;
        } else if (t == String.class) {
            return Kind.STRING;
        } else if (t.isEnum()) {
            return Kind.ENUM;
        } else if (t == Color.class) {
            return Kind.COLOR;
        } else if (t == Vector2.class) {
            return Kind.VECTOR2;
        } else if (t == Rectangle.class) {
            return Kind.RECTANGLE;
        } else if (t == float[].class) {
            return Kind.FLOATS;
        } else if (t == EntityRef.class) {
            return Kind.ENTITY_REF;
        }
        return null;
    }

    /** The field's current value. */
    Object get(Object component) {
        try {
            return field.get(component);
        } catch (ReflectionException e) {
            throw new IllegalStateException("Cannot read " + name, e);
        }
    }

    /** Sets the field from a JSON or Java value. */
    void set(Object component, Object value) {
        if (value instanceof JsonValue json && json.isNull()) {
            value = null;
        }
        if (value == null) {
            if (type.isPrimitive() || inPlace) {
                throw new IllegalArgumentException("cannot be null (" + typeName() + ")");
            }
            write(component, null);
            return;
        }
        switch (kind) {
            case INT -> write(component, (int) integral(value, Integer.MIN_VALUE, Integer.MAX_VALUE));
            case LONG -> write(component, integral(value, Long.MIN_VALUE, Long.MAX_VALUE));
            case FLOAT -> write(component, (float) number(value));
            case DOUBLE -> write(component, number(value));
            case BOOLEAN -> write(component, bool(value));
            case STRING -> write(component, string(value));
            case ENUM -> write(component, enumValue(value));
            case COLOR -> color(component, value);
            case VECTOR2 -> vector(component, value);
            case RECTANGLE -> rectangle(component, value);
            case FLOATS -> write(component, floats(value));
            case ENTITY_REF -> {
                if (!(value instanceof EntityRef ref)) {
                    throw mismatch(value, "an entity reference (a Tiled object reference, or code)");
                }
                write(component, ref);
            }
        }
    }

    String typeName() {
        return type.isArray() ? type.getComponentType().getSimpleName() + "[]" : type.getSimpleName();
    }

    private void write(Object component, Object value) {
        try {
            field.set(component, value);
        } catch (ReflectionException e) {
            throw new IllegalStateException("Cannot write " + name, e);
        }
    }

    private Object current(Object component) {
        Object existing = get(component);
        if (existing == null) {
            throw new IllegalArgumentException("is null on a new instance; give it an initial value");
        }
        return existing;
    }

    private double number(Object value) {
        if (value instanceof JsonValue j && j.isNumber()) {
            return j.asDouble();
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw mismatch(value, "a number");
    }

    private long integral(Object value, long min, long max) {
        if (value instanceof JsonValue j && j.isLong()) {
            long v = j.asLong();
            if (v < min || v > max) {
                throw new IllegalArgumentException("value " + v + " is out of range for " + typeName());
            }
            return v;
        }
        double d = number(value);
        if (d != Math.rint(d) || d < min || d > max) {
            throw new IllegalArgumentException("expected a whole number for " + typeName() + ", got " + d);
        }
        return (long) d;
    }

    private boolean bool(Object value) {
        if (value instanceof JsonValue j && j.isBoolean()) {
            return j.asBoolean();
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw mismatch(value, "true or false");
    }

    private String string(Object value) {
        if (value instanceof JsonValue j && j.isString()) {
            return j.asString();
        }
        if (value instanceof String s) {
            return s;
        }
        throw mismatch(value, "a string");
    }

    private Object enumValue(Object value) {
        String text = string(value);
        Object[] constants = type.getEnumConstants();
        StringBuilder names = new StringBuilder();
        for (Object constant : constants) {
            String n = ((Enum<?>) constant).name();
            if (n.equals(text)) {
                return constant;
            }
            names.append(names.length() == 0 ? "" : ", ").append(n);
        }
        throw new IllegalArgumentException("\"" + text + "\" is not a " + typeName() + " (one of " + names + ")");
    }

    private void color(Object component, Object value) {
        Color c;
        if (value instanceof Color color) {
            c = color;
        } else if (value instanceof String || (value instanceof JsonValue j && j.isString())) {
            c = hex(string(value));
        } else if (value instanceof JsonValue j && j.isObject()) {
            c = new Color(member(j, "r", 0f), member(j, "g", 0f), member(j, "b", 0f), member(j, "a", 1f));
        } else {
            throw mismatch(value, "a colour (\"#RRGGBB\", \"#RRGGBBAA\" or {r,g,b,a})");
        }
        if (inPlace) {
            ((Color) current(component)).set(c);
        } else {
            write(component, new Color(c));
        }
    }

    private static Color hex(String text) {
        String h = text.startsWith("#") ? text.substring(1) : text;
        if (!h.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            throw new IllegalArgumentException("\"" + text + "\" is not a colour (expected \"#RRGGBB\" or \"#RRGGBBAA\")");
        }
        return Color.valueOf(h);
    }

    private void vector(Object component, Object value) {
        float x;
        float y;
        if (value instanceof Vector2 v) {
            x = v.x;
            y = v.y;
        } else if (value instanceof JsonValue j && j.isArray() && j.size == 2) {
            x = number(j.get(0), "x");
            y = number(j.get(1), "y");
        } else if (value instanceof JsonValue j && j.isObject()) {
            x = member(j, "x", 0f);
            y = member(j, "y", 0f);
        } else {
            throw mismatch(value, "a vector ([x, y] or {x, y})");
        }
        if (inPlace) {
            ((Vector2) current(component)).set(x, y);
        } else {
            write(component, new Vector2(x, y));
        }
    }

    private void rectangle(Object component, Object value) {
        Rectangle r;
        if (value instanceof Rectangle rect) {
            r = rect;
        } else if (value instanceof JsonValue j && j.isObject()) {
            r = new Rectangle(member(j, "x", 0f), member(j, "y", 0f), member(j, "width", 0f), member(j, "height", 0f));
        } else {
            throw mismatch(value, "a rectangle ({x, y, width, height})");
        }
        if (inPlace) {
            ((Rectangle) current(component)).set(r);
        } else {
            write(component, new Rectangle(r));
        }
    }

    private float[] floats(Object value) {
        if (value instanceof float[] array) {
            return array.clone();
        }
        if (value instanceof JsonValue j && j.isArray()) {
            float[] out = new float[j.size];
            int i = 0;
            for (JsonValue item = j.child; item != null; item = item.next, i++) {
                out[i] = number(item, "[" + i + "]");
            }
            return out;
        }
        throw mismatch(value, "a list of numbers");
    }

    private float number(JsonValue item, String what) {
        if (item == null || !item.isNumber()) {
            throw new IllegalArgumentException(what + " must be a number");
        }
        return item.asFloat();
    }

    private float member(JsonValue object, String key, float fallback) {
        for (JsonValue child = object.child; child != null; child = child.next) {
            if (!child.name.equals("x") && !child.name.equals("y") && !child.name.equals("width")
                    && !child.name.equals("height") && !child.name.equals("r") && !child.name.equals("g")
                    && !child.name.equals("b") && !child.name.equals("a")) {
                throw new IllegalArgumentException("unknown key \"" + child.name + "\"");
            }
        }
        JsonValue v = object.get(key);
        return v == null ? fallback : number(v, key);
    }

    private IllegalArgumentException mismatch(Object value, String expected) {
        return new IllegalArgumentException("expected " + expected + " for " + typeName() + ", got " + describe(value));
    }

    static String describe(Object value) {
        if (value instanceof JsonValue j) {
            return switch (j.type()) {
                case stringValue -> "string \"" + j.asString() + "\"";
                case doubleValue, longValue -> "number " + j.asString();
                case booleanValue -> j.asString();
                case array -> "a list";
                case object -> "an object";
                case nullValue -> "null";
            };
        }
        return value instanceof String s ? "string \"" + s + "\"" : value.getClass().getSimpleName() + " " + value;
    }
}
