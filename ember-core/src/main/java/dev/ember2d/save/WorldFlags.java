package dev.ember2d.save;

import dev.ember2d.core.PublicApi;
import dev.ember2d.event.EventBus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The persistent state of the world in this play-through (F-013, I-430), available as {@code context.flags()}: bool,
 * int and String values by key, such as {@code chest.town.01.opened}. Setting a different value publishes
 * {@link FlagChanged}. Flags belong to a play-through, so they go in a save slot ({@link #save}, {@link #load}), not in
 * global data.
 */
@PublicApi
public final class WorldFlags {

    private final Map<String, Object> values = new LinkedHashMap<>();
    private final EventBus events;

    /** @param events receives {@link FlagChanged}; may be null */
    public WorldFlags(EventBus events) {
        this.events = events;
    }

    /** A bool flag, or the default when missing or of another type. */
    public boolean getBool(String key, boolean defaultValue) {
        return values.get(key) instanceof Boolean b ? b : defaultValue;
    }

    /** An int flag, or the default when missing or of another type. */
    public int getInt(String key, int defaultValue) {
        return values.get(key) instanceof Integer i ? i : defaultValue;
    }

    /** A String flag, or the default when missing or of another type. */
    public String getString(String key, String defaultValue) {
        return values.get(key) instanceof String s ? s : defaultValue;
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    /** The keys in the order they were first set (a read-only view). */
    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public void set(String key, boolean value) {
        put(key, value);
    }

    public void set(String key, int value) {
        put(key, value);
    }

    public void set(String key, String value) {
        if (value == null) {
            throw new IllegalArgumentException("flag " + key + ": a value is required (use remove)");
        }
        put(key, value);
    }

    /** Flips a bool flag (a missing flag counts as false); returns the new value. */
    public boolean toggle(String key) {
        boolean value = !getBool(key, false);
        put(key, value);
        return value;
    }

    /** Adds to an int flag (a missing flag counts as 0); returns the new value. */
    public int add(String key, int delta) {
        int value = getInt(key, 0) + delta;
        put(key, value);
        return value;
    }

    /** Removes a flag; returns whether it existed. */
    public boolean remove(String key) {
        boolean removed = values.remove(key) != null;
        if (removed) {
            changed(key);
        }
        return removed;
    }

    /** Removes every flag (a new game); publishes nothing. */
    public void clear() {
        values.clear();
    }

    /** Writes the flags into {@code out} under the child {@code flags}. */
    public void save(SaveData out) {
        SaveData flags = new SaveData();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            if (e.getValue() instanceof Boolean b) {
                flags.set(e.getKey(), b);
            } else if (e.getValue() instanceof Integer i) {
                flags.set(e.getKey(), i);
            } else {
                flags.set(e.getKey(), (String) e.getValue());
            }
        }
        out.set("flags", flags);
    }

    /** Replaces the flags with those saved by {@link #save}; a slot without flags clears them. Publishes nothing. */
    public void load(SaveData in) {
        values.clear();
        if (!in.has("flags")) {
            return;
        }
        SaveData saved = in.child("flags");
        for (String key : saved.keys()) {
            String text = saved.getString(key, null);
            if (text != null) {
                values.put(key, text);
            } else if (saved.getBool(key, false) == saved.getBool(key, true)) {
                values.put(key, saved.getBool(key, false));
            } else if (saved.getInt(key, 0) == saved.getInt(key, 1)) {
                values.put(key, saved.getInt(key, 0));
            }   // other kinds of value are not flags: skipped
        }
    }

    private void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("A flag needs a non-empty key");
        }
        if (!Objects.equals(values.get(key), value)) {
            values.put(key, value);
            changed(key);
        }
    }

    private void changed(String key) {
        if (events != null) {
            events.publish(new FlagChanged(key));
        }
    }
}
