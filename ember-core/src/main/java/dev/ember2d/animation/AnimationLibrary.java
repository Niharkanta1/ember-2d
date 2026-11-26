package dev.ember2d.animation;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Clip sets by asset ID (F-007): a clip set is a manifest entry of type {@code clips}; it is parsed and validated on
 * first use, then cached. Each clip takes its frames from an atlas sequence, an Aseprite JSON export (array or hash)
 * or a sprite sheet; see the I-130 section of F-007 for the format.
 */
@PublicApi
public final class AnimationLibrary {

    private static final List<String> ROOT_FIELDS = List.of("version", "defaults", "clips");
    private static final List<String> CLIP_FIELDS = List.of("atlas", "regions", "aseprite", "tag", "texture", "sheet",
            "frameWidth", "frameHeight", "frames", "frameDuration", "mode", "events");

    private final Assets assets;
    private final Map<String, ClipSet> cache = new HashMap<>();

    public AnimationLibrary(Assets assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    /**
     * The clip set loaded for a manifest ID.
     *
     * @throws AnimationException listing every problem
     * @throws IllegalArgumentException if the manifest does not list the ID
     */
    public ClipSet clipSet(String id) {
        ClipSet cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        if (!assets.contains(id)) {
            throw new IllegalArgumentException("Unknown clip set \"" + id + "\": not in the asset manifest");
        }
        String file = assets.path(id);
        List<String> problems = new ArrayList<>();
        Map<String, Clip> clips = parse(file, assets.get(id, JsonValue.class), problems);
        if (!problems.isEmpty()) {
            throw new AnimationException("Invalid clip set \"" + id + "\"", problems);
        }
        ClipSet set = new ClipSet(id, clips);
        cache.put(id, set);
        return set;
    }

    /** Adds a clip set built in memory (tests). */
    void put(ClipSet set) {
        cache.put(set.id(), set);
    }

    private Map<String, Clip> parse(String file, JsonValue root, List<String> problems) {
        Map<String, Clip> clips = new LinkedHashMap<>();
        if (root == null || !root.isObject()) {
            problems.add(file + ": must be a JSON object");
            return clips;
        }
        unknown(root, ROOT_FIELDS, file + ": ", problems);
        JsonValue version = root.get("version");
        if (version == null || !version.isLong() || version.asInt() != 1) {
            problems.add(file + ": version: required, must be 1");
        }
        float defaultDuration = 0.1f;
        JsonValue defaults = root.get("defaults");
        if (defaults != null) {
            unknown(defaults, List.of("frameDuration"), file + ": defaults.", problems);
            defaultDuration = positive(defaults, "frameDuration", 0.1f, file + ": defaults.frameDuration", problems);
        }
        JsonValue list = root.get("clips");
        if (list == null || !list.isObject() || list.size == 0) {
            problems.add(file + ": clips: required, an object of clip name to clip");
            return clips;
        }
        for (JsonValue c = list.child; c != null; c = c.next) {
            String where = file + ": clips." + c.name;
            if (!c.isObject()) {
                problems.add(where + ": must be an object");
                continue;
            }
            int before = problems.size();
            unknown(c, CLIP_FIELDS, where + ".", problems);
            Clip clip = clip(c, where, defaultDuration, problems);
            if (clip != null && problems.size() == before) {
                clips.put(c.name, clip);
            }
        }
        return clips;
    }

    /** Frames, durations and mode collected from a source before events and overrides. */
    private static final class Frames {
        final List<TextureRegion> regions = new ArrayList<>();
        final List<Float> durations = new ArrayList<>();   // null entries: use the clip's duration
        PlayMode mode;                                       // null: LOOP unless the clip says otherwise
        final Map<Integer, String> events = new HashMap<>();
    }

    private Clip clip(JsonValue c, String where, float defaultDuration, List<String> problems) {
        int sources = (c.has("atlas") ? 1 : 0) + (c.has("aseprite") ? 1 : 0) + (c.has("sheet") ? 1 : 0);
        if (sources != 1) {
            problems.add(where + ": needs exactly one frame source: atlas, aseprite or sheet");
            return null;
        }
        Frames f = new Frames();
        if (c.has("atlas")) {
            atlas(c, where, f, problems);
        } else if (c.has("sheet")) {
            sheet(c, where, f, problems);
        } else {
            aseprite(c, where, f, problems);
        }
        if (f.regions.isEmpty()) {
            if (problems.isEmpty() || !problems.get(problems.size() - 1).startsWith(where)) {
                problems.add(where + ": the clip has no frames");
            }
            return null;
        }
        int n = f.regions.size();
        Float override = c.has("frameDuration") ? positive(c, "frameDuration", defaultDuration, where + ".frameDuration",
                problems) : null;
        float[] durations = new float[n];
        for (int i = 0; i < n; i++) {
            Float d = f.durations.isEmpty() ? null : f.durations.get(i);
            durations[i] = override != null ? override : d != null ? d : defaultDuration;
        }
        PlayMode mode = f.mode != null ? f.mode : PlayMode.LOOP;
        if (c.has("mode")) {
            String m = c.getString("mode", "");
            switch (m) {
                case "loop" -> mode = PlayMode.LOOP;
                case "once" -> mode = PlayMode.ONCE;
                case "pingpong" -> mode = PlayMode.PINGPONG;
                default -> problems.add(where + ".mode: must be \"loop\", \"once\" or \"pingpong\" (was \"" + m + "\")");
            }
        }
        String[] events = new String[n];
        for (Map.Entry<Integer, String> e : f.events.entrySet()) {
            if (e.getKey() < n) {
                events[e.getKey()] = e.getValue();
            }
        }
        JsonValue ev = c.get("events");
        if (ev != null) {
            if (!ev.isObject()) {
                problems.add(where + ".events: must be an object of frame index to event name");
            } else {
                for (JsonValue e = ev.child; e != null; e = e.next) {
                    int index;
                    try {
                        index = Integer.parseInt(e.name);
                    } catch (NumberFormatException x) {
                        problems.add(where + ".events." + e.name + ": the key must be a frame index");
                        continue;
                    }
                    if (index < 0 || index >= n) {
                        problems.add(where + ".events." + e.name + ": frame " + index + " is out of range (0-"
                                + (n - 1) + ")");
                    } else if (!e.isString() || e.asString().isBlank()) {
                        problems.add(where + ".events." + e.name + ": must be an event name");
                    } else {
                        events[index] = e.asString();
                    }
                }
            }
        }
        return new Clip(nameOf(where), f.regions.toArray(new TextureRegion[0]), durations, events, mode);
    }

    private void atlas(JsonValue c, String where, Frames f, List<String> problems) {
        String atlasId = c.getString("atlas", null);
        String regions = c.getString("regions", null);
        if (regions == null) {
            problems.add(where + ".regions: required with atlas (the region name)");
            return;
        }
        TextureAtlas atlas = asset(atlasId, TextureAtlas.class, where + ".atlas", problems);
        if (atlas == null) {
            return;
        }
        Array<TextureAtlas.AtlasRegion> found = atlas.findRegions(regions);
        if (found.isEmpty()) {
            problems.add(where + ".regions: no regions named \"" + regions + "\" in atlas \"" + atlasId + "\"");
            return;
        }
        for (TextureAtlas.AtlasRegion r : found) {
            f.regions.add(r);
        }
    }

    private void sheet(JsonValue c, String where, Frames f, List<String> problems) {
        Texture texture = asset(c.getString("sheet", null), Texture.class, where + ".sheet", problems);
        int fw = (int) positive(c, "frameWidth", 0f, where + ".frameWidth", problems);
        int fh = (int) positive(c, "frameHeight", 0f, where + ".frameHeight", problems);
        if (!c.has("frameWidth") || !c.has("frameHeight")) {
            problems.add(where + ": frameWidth and frameHeight are required with sheet");
            return;
        }
        if (texture == null || fw <= 0 || fh <= 0) {
            return;
        }
        int cols = texture.getWidth() / fw;
        int rows = texture.getHeight() / fh;
        if (cols == 0 || rows == 0) {
            problems.add(where + ": frames of " + fw + "x" + fh + " do not fit the " + texture.getWidth() + "x"
                    + texture.getHeight() + " texture");
            return;
        }
        int first = 0;
        int last = cols * rows - 1;
        JsonValue range = c.get("frames");
        if (range != null) {
            if (!range.isArray() || range.size != 2 || !range.get(0).isLong() || !range.get(1).isLong()) {
                problems.add(where + ".frames: must be [first, last] frame indexes");
                return;
            }
            first = range.get(0).asInt();
            last = range.get(1).asInt();
            if (first < 0 || last >= cols * rows || first > last) {
                problems.add(where + ".frames: [" + first + ", " + last + "] is out of range (the sheet has frames 0-"
                        + (cols * rows - 1) + ")");
                return;
            }
        }
        for (int i = first; i <= last; i++) {
            f.regions.add(new TextureRegion(texture, (i % cols) * fw, (i / cols) * fh, fw, fh));
        }
    }

    private void aseprite(JsonValue c, String where, Frames f, List<String> problems) {
        JsonValue ase = asset(c.getString("aseprite", null), JsonValue.class, where + ".aseprite", problems);
        Texture texture = asset(c.getString("texture", null), Texture.class, where + ".texture", problems);
        String tagName = c.getString("tag", null);
        if (tagName == null) {
            problems.add(where + ".tag: required with aseprite (a frame tag name)");
        }
        if (ase == null || texture == null || tagName == null) {
            return;
        }
        JsonValue frames = ase.get("frames");
        JsonValue meta = ase.get("meta");
        JsonValue tags = meta == null ? null : meta.get("frameTags");
        if (frames == null || tags == null) {
            problems.add(where + ".aseprite: not an Aseprite JSON export (needs frames and meta.frameTags)");
            return;
        }
        List<JsonValue> all = new ArrayList<>();
        for (JsonValue fr = frames.child; fr != null; fr = fr.next) {   // json-array and json-hash keep file order
            all.add(fr);
        }
        JsonValue tag = null;
        for (JsonValue t = tags.child; t != null; t = t.next) {
            if (tagName.equals(t.getString("name", null))) {
                tag = t;
            }
        }
        if (tag == null) {
            List<String> names = new ArrayList<>();
            for (JsonValue t = tags.child; t != null; t = t.next) {
                names.add(t.getString("name", "?"));
            }
            problems.add(where + ".tag: no frame tag \"" + tagName + "\" (tags: " + names + ")");
            return;
        }
        int from = tag.getInt("from", 0);
        int to = tag.getInt("to", -1);
        if (from < 0 || to >= all.size() || from > to) {
            problems.add(where + ".tag: tag \"" + tagName + "\" covers frames " + from + "-" + to + ", but the export has "
                    + all.size());
            return;
        }
        String direction = tag.getString("direction", "forward");
        boolean reverse = direction.equals("reverse") || direction.equals("pingpong_reverse");
        for (int i = 0; i <= to - from; i++) {
            JsonValue fr = all.get(reverse ? to - i : from + i);
            if (fr.getBoolean("rotated", false)) {
                problems.add(where + ": frame " + (from + i) + " is rotated; export without rotation");
                return;
            }
            JsonValue rect = fr.get("frame");
            JsonValue src = fr.get("spriteSourceSize");
            JsonValue size = fr.get("sourceSize");
            int x = rect.getInt("x");
            int y = rect.getInt("y");
            int w = rect.getInt("w");
            int h = rect.getInt("h");
            if (fr.getBoolean("trimmed", false) && src != null && size != null) {
                TextureAtlas.AtlasRegion r = new TextureAtlas.AtlasRegion(texture, x, y, w, h);
                r.offsetX = src.getInt("x");
                r.originalWidth = size.getInt("w");
                r.originalHeight = size.getInt("h");
                r.offsetY = r.originalHeight - (src.getInt("y") + h);   // libGDX offsets are from the bottom
                f.regions.add(r);
            } else {
                f.regions.add(new TextureRegion(texture, x, y, w, h));
            }
            f.durations.add(fr.getInt("duration", 100) / 1000f);
        }
        if (direction.startsWith("pingpong")) {
            f.mode = PlayMode.PINGPONG;
        } else if ("1".equals(tag.getString("repeat", null))) {
            f.mode = PlayMode.ONCE;
        }
        String data = tag.getString("data", null);
        if (data != null) {
            String text = data.trim();
            if (text.toLowerCase(Locale.ROOT).startsWith("events:")) {
                for (String part : text.substring("events:".length()).split(",")) {
                    String[] kv = part.trim().split("=", 2);
                    try {
                        if (kv.length == 2 && !kv[1].trim().isEmpty()) {
                            f.events.put(Integer.parseInt(kv[0].trim()), kv[1].trim());
                            continue;
                        }
                    } catch (NumberFormatException ignored) {
                        // reported below
                    }
                    if (!part.isBlank()) {
                        problems.add(where + ".tag: user data \"" + data + "\": expected \"events: 2=step, 6=step\"");
                        return;
                    }
                }
            }
        }
    }

    private <T> T asset(String id, Class<T> type, String where, List<String> problems) {
        if (id == null) {
            problems.add(where + ": required (an asset ID)");
            return null;
        }
        if (!assets.contains(id)) {
            problems.add(where + ": unknown asset \"" + id + "\"");
            return null;
        }
        try {
            return assets.get(id, type);
        } catch (RuntimeException e) {
            problems.add(where + ": asset \"" + id + "\" is not a " + type.getSimpleName() + " (" + e.getMessage() + ")");
            return null;
        }
    }

    private static float positive(JsonValue json, String key, float fallback, String where, List<String> problems) {
        JsonValue v = json.get(key);
        if (v == null) {
            return fallback;
        }
        if (!v.isNumber() || !(v.asFloat() > 0f)) {
            problems.add(where + ": must be a number > 0");
            return fallback;
        }
        return v.asFloat();
    }

    private static void unknown(JsonValue json, List<String> allowed, String prefix, List<String> problems) {
        for (JsonValue f = json.child; f != null; f = f.next) {
            if (!allowed.contains(f.name)) {
                problems.add(prefix + f.name + ": unknown field (expected " + String.join(", ", allowed) + ")");
            }
        }
    }

    private static String nameOf(String where) {
        return where.substring(where.lastIndexOf("clips.") + "clips.".length());
    }
}
