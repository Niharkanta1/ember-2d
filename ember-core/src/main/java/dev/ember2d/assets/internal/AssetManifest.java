package dev.ember2d.assets.internal;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
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
        JSON("json", JsonValue.class),
        /** A Tiled map, {@code .tmx} or {@code .tmj} (F-009). */
        MAP("map", TiledMap.class),
        /** A prefab JSON file; its ID is the prefab ID (F-010). */
        PREFAB("prefab", JsonValue.class),
        /** An animation clip set JSON file (F-007). */
        CLIPS("clips", JsonValue.class),
        /** An animation state machine definition (F-007). */
        ASM("asm", JsonValue.class),
        /** A TrueType or OpenType font rendered to a bitmap font when loaded (F-004, I-140). */
        FREETYPE("freetype", BitmapFont.class);

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

    /** One manifest entry; {@code font} holds the font options of {@code font} and {@code freetype} entries. */
    public record Entry(String id, String path, Type type, List<String> groups, boolean optional, FontOptions font) {

        public Entry(String id, String path, Type type, List<String> groups, boolean optional) {
            this(id, path, type, groups, optional, null);
        }

        /**
         * The {@code AssetManager} key: the path, except for FreeType fonts, where several entries may render the same
         * file at different sizes (the key ends in {@code .ttf} so the FreeType loader handles it).
         */
        public String key() {
            return type == Type.FREETYPE ? "ember-freetype/" + id + ".ttf" : path;
        }
    }

    /**
     * Font options of a manifest entry (F-004, I-140). For {@code font} entries only {@code markup} applies. Colours
     * are hex strings ({@code #RRGGBB} or {@code #RRGGBBAA}); {@code characters} null means libGDX's default set.
     */
    public record FontOptions(int size, boolean mono, String color, float borderWidth, String borderColor,
            int shadowOffsetX, int shadowOffsetY, String shadowColor, String characters, boolean markup) {

        /** Defaults: 16 px, anti-aliased, white, no border or shadow, default characters, no markup. */
        public static final FontOptions DEFAULTS = new FontOptions(16, false, "#ffffff", 0f, "#000000", 0, 0,
                "#00000080", null, false);
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
        FontOptions font = fontOptions(item.get("font"), type, path, where, problems);
        if (type == Type.MAP && path != null && !path.endsWith(".tmx") && !path.endsWith(".tmj")) {
            problems.add(where + ": a map must be a .tmx or .tmj file (was " + path + ")");
        }
        if (id != null && entries.containsKey(id)) {
            problems.add(where + ": duplicate id \"" + id + "\"");
            return;
        }
        if (id != null && path != null && type != null && groups != null) {
            entries.put(id, new Entry(id, path, type, groups, optionalValue != null && optionalValue.asBoolean(), font));
        }
    }

    private static final List<String> FONT_FIELDS = List.of("size", "mono", "color", "borderWidth", "borderColor",
            "shadowOffsetX", "shadowOffsetY", "shadowColor", "characters", "markup");

    private static FontOptions fontOptions(JsonValue json, Type type, String path, String where,
            List<String> problems) {
        if (type == Type.FREETYPE && path != null && !path.endsWith(".ttf") && !path.endsWith(".otf")) {
            problems.add(where + ": a freetype font must be a .ttf or .otf file (was " + path + ")");
        }
        if (json == null) {
            return type == Type.FREETYPE || type == Type.FONT ? FontOptions.DEFAULTS : null;
        }
        if (type != Type.FREETYPE && type != Type.FONT) {
            problems.add(where + ": \"font\" options are only for font and freetype entries");
            return null;
        }
        if (!json.isObject()) {
            problems.add(where + ": \"font\" must be an object");
            return FontOptions.DEFAULTS;
        }
        FontOptions d = FontOptions.DEFAULTS;
        for (JsonValue f = json.child; f != null; f = f.next) {
            if (!FONT_FIELDS.contains(f.name)) {
                problems.add(where + ": font." + f.name + ": unknown field (expected " + String.join(", ", FONT_FIELDS)
                        + ")");
            } else if (type == Type.FONT && !f.name.equals("markup")) {
                problems.add(where + ": font." + f.name + ": only \"markup\" applies to a bitmap font");
            }
        }
        int size = json.has("size") ? whole(json.get("size"), 1, where + ": font.size", problems, d.size()) : d.size();
        boolean mono = bool(json.get("mono"), where + ": font.mono", problems, d.mono());
        String color = colour(json.get("color"), where + ": font.color", problems, d.color());
        float border = d.borderWidth();
        JsonValue bw = json.get("borderWidth");
        if (bw != null) {
            if (!bw.isNumber() || bw.asFloat() < 0f) {
                problems.add(where + ": font.borderWidth: must be a number >= 0");
            } else {
                border = bw.asFloat();
            }
        }
        String borderColor = colour(json.get("borderColor"), where + ": font.borderColor", problems, d.borderColor());
        int sx = json.has("shadowOffsetX")
                ? whole(json.get("shadowOffsetX"), Integer.MIN_VALUE, where + ": font.shadowOffsetX", problems, 0) : 0;
        int sy = json.has("shadowOffsetY")
                ? whole(json.get("shadowOffsetY"), Integer.MIN_VALUE, where + ": font.shadowOffsetY", problems, 0) : 0;
        String shadowColor = colour(json.get("shadowColor"), where + ": font.shadowColor", problems, d.shadowColor());
        String characters = null;
        JsonValue ch = json.get("characters");
        if (ch != null) {
            if (!ch.isString() || ch.asString().isEmpty()) {
                problems.add(where + ": font.characters: must be \"default\" or the characters to include");
            } else if (!ch.asString().equals("default")) {
                characters = ch.asString();
            }
        }
        boolean markup = bool(json.get("markup"), where + ": font.markup", problems, d.markup());
        return new FontOptions(size, mono, color, border, borderColor, sx, sy, shadowColor, characters, markup);
    }

    private static int whole(JsonValue v, int min, String where, List<String> problems, int fallback) {
        if (!v.isLong() || v.asLong() < min) {
            problems.add(where + ": must be a whole number" + (min == 1 ? " > 0" : ""));
            return fallback;
        }
        return v.asInt();
    }

    private static boolean bool(JsonValue v, String where, List<String> problems, boolean fallback) {
        if (v == null) {
            return fallback;
        }
        if (!v.isBoolean()) {
            problems.add(where + ": must be true or false");
            return fallback;
        }
        return v.asBoolean();
    }

    private static String colour(JsonValue v, String where, List<String> problems, String fallback) {
        if (v == null) {
            return fallback;
        }
        String s = v.isString() ? v.asString() : "";
        if (!s.matches("#?[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            problems.add(where + ": must be a colour \"#RRGGBB\" or \"#RRGGBBAA\"");
            return fallback;
        }
        return s;
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
