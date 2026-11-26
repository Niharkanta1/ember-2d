package dev.ember2d.devtools.internal;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads and writes a component's public instance fields by type for the inspector (F-018, I-160). Reflection is
 * allowed in {@code ember-devtools} (ARCHITECTURE rule 10).
 */
public final class FieldAccess {

    /** How a field is shown and edited. */
    public enum Kind { INT, LONG, FLOAT, DOUBLE, BOOLEAN, STRING, ENUM, COLOR, VECTOR2, RECTANGLE, OTHER }

    /**
     * One public field of a component.
     *
     * @param editable whether the inspector may change it: a settable field of a supported kind ({@link Kind#OTHER}
     *                 is read-only; {@code Color}, {@code Vector2} and {@code Rectangle} are edited in place, even when
     *                 the field is final)
     */
    public record FieldView(String name, Kind kind, Field field, boolean editable) {

        public Object get(Object component) {
            try {
                return field.get(component);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read " + field, e);
            }
        }

        /** Sets a scalar, String or enum value; mutable value types are changed through {@link #get}. */
        public void set(Object component, Object value) {
            if (!editable || Modifier.isFinal(field.getModifiers())) {
                throw new IllegalStateException("Field " + name + " cannot be set");
            }
            try {
                field.set(component, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot write " + field, e);
            }
        }
    }

    private FieldAccess() {
    }

    /**
     * The public instance fields of a component, in declaration order (superclasses first).
     *
     * @param settable field names the component registry can set, or null when the component is not registered (then
     *                 every supported public field is editable)
     */
    public static List<FieldView> fields(Object component, Set<String> settable) {
        List<Class<?>> chain = new ArrayList<>();
        for (Class<?> c = component.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            chain.add(0, c);
        }
        List<FieldView> out = new ArrayList<>();
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!Modifier.isPublic(mod) || Modifier.isStatic(mod) || f.isSynthetic()) {
                    continue;
                }
                Kind kind = kind(f.getType());
                boolean mutableValue = kind == Kind.COLOR || kind == Kind.VECTOR2 || kind == Kind.RECTANGLE;
                boolean editable = kind != Kind.OTHER && (mutableValue || !Modifier.isFinal(mod))
                        && (settable == null || settable.contains(f.getName()));
                out.add(new FieldView(f.getName(), kind, f, editable));
            }
        }
        return out;
    }

    static Kind kind(Class<?> t) {
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
        }
        return Kind.OTHER;
    }
}
