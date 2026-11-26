package dev.ember2d.input.internal;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.ObjectIntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Binding names to physical input codes (F-005). Keys use libGDX's key names in upper case with spaces and {@code -}
 * turned into {@code _} ({@code SPACE}, {@code L_SHIFT}, {@code NUMPAD_1}); mouse buttons are {@code MOUSE_LEFT},
 * {@code MOUSE_RIGHT}, {@code MOUSE_MIDDLE}, {@code MOUSE_BACK}, {@code MOUSE_FORWARD}. Built from
 * {@link Keys#toString(int)}, so no reflection.
 *
 * <p>Physical codes: libGDX key codes {@code 0..Keys.MAX_KEYCODE}, then mouse buttons from {@link #MOUSE_BASE}.
 */
public final class InputNames {

    /** The physical code of mouse button 0 ({@link Buttons#LEFT}). */
    public static final int MOUSE_BASE = Keys.MAX_KEYCODE + 1;
    /** Number of physical codes. */
    public static final int COUNT = MOUSE_BASE + 5;

    private static final String[] MOUSE_NAMES = {"MOUSE_LEFT", "MOUSE_RIGHT", "MOUSE_MIDDLE", "MOUSE_BACK",
        "MOUSE_FORWARD"};
    private static final ObjectIntMap<String> CODES = new ObjectIntMap<>();
    private static final String[] NAMES = new String[COUNT];

    static {
        for (int code = 0; code <= Keys.MAX_KEYCODE; code++) {
            String label = Keys.toString(code);
            if (label == null || label.isEmpty() || "Unknown".equals(label)) {
                continue;
            }
            String name = normalise(label);
            if (!CODES.containsKey(name)) {   // first code wins if libGDX ever reuses a label
                CODES.put(name, code);
                NAMES[code] = name;
            }
        }
        for (int button = 0; button < MOUSE_NAMES.length; button++) {
            CODES.put(MOUSE_NAMES[button], MOUSE_BASE + button);
            NAMES[MOUSE_BASE + button] = MOUSE_NAMES[button];
        }
    }

    private InputNames() {
    }

    /** The physical code for a binding name, or -1 if the name is unknown. */
    public static int code(String name) {
        return name == null ? -1 : CODES.get(name, -1);
    }

    /** The binding name of a physical code, or null if it has none. */
    public static String name(int code) {
        return code >= 0 && code < COUNT ? NAMES[code] : null;
    }

    /** Every binding name, sorted. */
    public static List<String> all() {
        List<String> names = new ArrayList<>();
        for (String name : NAMES) {
            if (name != null) {
                names.add(name);
            }
        }
        Collections.sort(names);
        return names;
    }

    static String normalise(String label) {
        return label.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
