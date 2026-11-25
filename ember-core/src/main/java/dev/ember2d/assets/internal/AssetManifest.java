package dev.ember2d.assets.internal;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.SerializationException;
import dev.ember2d.assets.AssetLoadException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The parsed and validated asset manifest (F-008). */
public final class AssetManifest {

    public static final int VERSION = 1;

    /** Manifest type names and the libGDX class each loads as. */
    public enum Type {
        TEXTURE("texture", Texture.class),
        ATLAS("atlas", TextureAtlas.class),
        FONT("font", BitmapFont.class),
        SOUND("sound", Sound.class),
        MUSIC("music", Music.class),
        JSON("json", JsonValue.class);

        final String manifestName;
        final Class<?> assetClass;

        Type(String manifestName, Class<?> assetClass) {
            this.manifestName = manifestName;
            this.assetClass = assetClass;
        }

        public Class<?> assetClass() {
            return assetClass;
        }

        static Type byName(String name) {
            for (Type type : values()) {
                if (type.manifestName.equals(name)) {
                    return type;
                }
            }
            return null;
        }

        static String names() {
            return Arrays.stream(values()).map(t -> t.manifestName).toList().toString();
        }
    }

    /** One manifest entry. */
    public record Entry(String id, String path, Type type, List<String> groups, boolean optional) {
    }

    private final String source;
    private final Map<String, Entry> entries;
    private final Map<String, List<Entry>> groups;

    private AssetManifest(String source, Map<String, Entry> entries) {
        this.source = source;
        this.entries = Collections.unmodifiableMap(entries);
        Map<String, List<Entry>> byGroup = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            for (String group : entry.groups()) {
                byGroup.computeIfAbsent(group, g -> new ArrayList<>()).add(entry);
            }
        }
        byGroup.replaceAll((group, list) -> List.copyOf(list));
        this.groups = Collections.unmodifiableMap(byGroup);
    }

    /** An empty manifest (no assets). */
    public static AssetManifest empty(String source) {
        return new AssetManifest(source, new LinkedHashMap<>());
    }

    /**
     * Parses and validates manifest JSON.
     *
     * @param source the file name used in error messages
     * @throws AssetLoadException listing every problem
     */
    public static AssetManifest parse(String json, String source) {
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (SerializationException e) {
            throw new AssetLoadException("Invalid asset manifest " + source, List.of("not valid JSON: " + e.getMessage()));
        }
        List<String> problems = new ArrayList<>();
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (root == null || !root.isObject()) {
            throw new AssetLoadException("Invalid asset manifest " + source, List.of("the root must be a JSON object"));
        }
        JsonValue version = root.get("version");
        if (version == null || !version.isNumber() || version.asInt() != VERSION) {
            problems.add("\"version\" must be " + VERSION + " (was " + (version == null ? "missing" : version.toJson(JsonWriter.OutputType.minimal)) + ")");
        }
        JsonValue assets = root.get("assets");
        if (assets == null || !assets.isArray()) {
            problems.add("\"assets\" must be an array");
        } else {
            int index = 0;
            for (JsonValue item = assets.child; item != null; item = item.next, index++) {
                parseEntry(item, index, entries, problems);
            }
        }
        if (!problems.isEmpty()) {
            throw new AssetLoadException("Invalid asset manifest " + source, problems);
        }
        return new AssetManifest(source, entries);
    }

    private static void parseEntry(JsonValue item, int index, Map<String, Entry> entries, List<String> problems) {
        if (!item.isObject()) {
            problems.add("assets[" + index + "] must be an object");
            return;
        }
        String id = text(item, "id");
        String where = "assets[" + index + "]" + (id != null ? " (\"" + id + "\")" : "");
        String path = text(item, "path");
        String typeName = text(item, "type");
        if (id == null) {
            problems.add(where + ": \"id\" is required");
        }
        if (path == null) {
            problems.add(where + ": \"path\" is required");
        }
        Type type = null;
        if (typeName == null) {
            problems.add(where + ": \"type\" is required");
        } else {
            type = Type.byName(typeName);
            if (type == null) {
                problems.add(where + ": unknown \"type\" \"" + typeName + "\"; valid types: " + Type.names());
            }
        }
        List<String> groups = groups(item.get("group"));
        if (groups == null) {
            problems.add(where + ": \"group\" must be a name or a non-empty list of names");
        }
        JsonValue optionalValue = item.get("optional");
        if (optionalValue != null && !optionalValue.isBoolean()) {
            problems.add(where + ": \"optional\" must be true or false");
        }
        if (id != null && entries.containsKey(id)) {
            problems.add(where + ": duplicate id \"" + id + "\"");
            return;
        }
        if (id != null && path != null && type != null && groups != null) {
            entries.put(id, new Entry(id, path, type, groups, optionalValue != null && optionalValue.asBoolean()));
        }
    }

    private static String text(JsonValue item, String field) {
        JsonValue value = item.get(field);
        return value != null && value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static List<String> groups(JsonValue value) {
        if (value == null) {
            return null;
        }
        if (value.isString() && !value.asString().isBlank()) {
            return List.of(value.asString());
        }
        if (value.isArray() && value.size > 0) {
            Set<String> names = new LinkedHashSet<>();
            for (JsonValue name = value.child; name != null; name = name.next) {
                if (!name.isString() || name.asString().isBlank()) {
                    return null;
                }
                names.add(name.asString());
            }
            return List.copyOf(names);
        }
        return null;
    }

    public String source() {
        return source;
    }

    public Entry find(String id) {
        return entries.get(id);
    }

    public Iterable<Entry> entries() {
        return entries.values();
    }

    /** Entries of a group, or null for an unknown group. */
    public List<Entry> group(String name) {
        return groups.get(name);
    }

    public Set<String> groupNames() {
        return groups.keySet();
    }

    /** Up to {@code limit} IDs closest to {@code id} by edit distance. */
    public List<String> closestIds(String id, int limit) {
        List<String> ids = new ArrayList<>(entries.keySet());
        ids.sort((a, b) -> Integer.compare(distance(id, a), distance(id, b)));
        return ids.subList(0, Math.min(limit, ids.size()));
    }

    static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
