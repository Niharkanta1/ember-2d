package dev.ember2d.input;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import dev.ember2d.core.PublicApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The player's changes to the on-screen controls (F-022, I-225), applied over the {@code touchControls} defaults of
 * the bindings file with {@code Input.setTouchLayout}. Controls are named {@code joystick}, {@code dpad}, or by a
 * button's action. Positions use the same dp coordinates as the bindings file (buttons from the right edge, a negative
 * y from the top). Saved and restored as JSON by the settings store (I-230).
 */
@PublicApi
public final class TouchLayout {

    public static final float MIN_SCALE = 0.6f;
    public static final float MAX_SCALE = 1.6f;
    public static final float MIN_OPACITY = 0.15f;
    public static final float MAX_OPACITY = 0.8f;

    /** One control's changes; NaN position = the default. */
    public static final class Entry {
        float x = Float.NaN;
        float y = Float.NaN;
        float scale = 1f;
        boolean visible = true;

        public float x() {
            return x;
        }

        public float y() {
            return y;
        }

        public float scale() {
            return scale;
        }

        public boolean visible() {
            return visible;
        }

        public boolean moved() {
            return !Float.isNaN(x);
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private float opacity = Float.NaN;

    /** No changes: every control as in the bindings file. */
    public static TouchLayout defaults() {
        return new TouchLayout();
    }

    /** Moves a control (dp, in the bindings file's coordinates for that control). */
    public TouchLayout move(String control, float xDp, float yDp) {
        Entry e = entry(control);
        e.x = xDp;
        e.y = yDp;
        return this;
    }

    /** Sizes a control relative to its default, clamped to {@value #MIN_SCALE}..{@value #MAX_SCALE}. */
    public TouchLayout scale(String control, float factor) {
        entry(control).scale = MathUtils.clamp(factor, MIN_SCALE, MAX_SCALE);
        return this;
    }

    public TouchLayout setVisible(String control, boolean visible) {
        entry(control).visible = visible;
        return this;
    }

    /** The controls' opacity, clamped to {@value #MIN_OPACITY}..{@value #MAX_OPACITY}. */
    public TouchLayout setOpacity(float opacity) {
        this.opacity = MathUtils.clamp(opacity, MIN_OPACITY, MAX_OPACITY);
        return this;
    }

    /** The opacity, or {@code fallback} when not changed. */
    public float opacity(float fallback) {
        return Float.isNaN(opacity) ? fallback : opacity;
    }

    /** The changes to a control, or null when it has none. */
    public Entry get(String control) {
        return entries.get(control);
    }

    public float scaleOf(String control) {
        Entry e = entries.get(control);
        return e == null ? 1f : e.scale;
    }

    public boolean visibleOf(String control) {
        Entry e = entries.get(control);
        return e == null || e.visible;
    }

    /** An independent copy. */
    public TouchLayout copy() {
        return parse(toJson());
    }

    private Entry entry(String control) {
        return entries.computeIfAbsent(Objects.requireNonNull(control, "control"), k -> new Entry());
    }

    public String toJson() {
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("version", new JsonValue(1));
        if (!Float.isNaN(opacity)) {
            root.addChild("opacity", new JsonValue(opacity));
        }
        JsonValue controls = new JsonValue(JsonValue.ValueType.object);
        for (Map.Entry<String, Entry> m : entries.entrySet()) {
            Entry e = m.getValue();
            JsonValue c = new JsonValue(JsonValue.ValueType.object);
            if (e.moved()) {
                c.addChild("x", new JsonValue(e.x));
                c.addChild("y", new JsonValue(e.y));
            }
            if (e.scale != 1f) {
                c.addChild("scale", new JsonValue(e.scale));
            }
            if (!e.visible) {
                c.addChild("visible", new JsonValue(false));
            }
            controls.addChild(m.getKey(), c);
        }
        root.addChild("controls", controls);
        return root.toJson(JsonWriter.OutputType.json);
    }

    /**
     * Reads a layout written by {@link #toJson()}.
     *
     * @throws IllegalArgumentException if the text is not a layout
     */
    public static TouchLayout parse(String json) {
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a touch layout: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject() || root.getInt("version", 0) != 1) {
            throw new IllegalArgumentException("Not a touch layout (expected {\"version\": 1, ...})");
        }
        TouchLayout layout = new TouchLayout();
        if (root.has("opacity")) {
            layout.setOpacity(root.getFloat("opacity"));
        }
        JsonValue controls = root.get("controls");
        if (controls != null) {
            for (JsonValue c = controls.child; c != null; c = c.next) {
                if (c.has("x") && c.has("y")) {
                    layout.move(c.name, c.getFloat("x"), c.getFloat("y"));
                }
                if (c.has("scale")) {
                    layout.scale(c.name, c.getFloat("scale"));
                }
                if (c.has("visible")) {
                    layout.setVisible(c.name, c.getBoolean("visible"));
                }
            }
        }
        return layout;
    }
}
