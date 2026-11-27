package dev.ember2d.kit.bullets;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bullet patterns by asset ID (F-024): JSON assets of type {@code json}, parsed on first use and cached; a hot reload of
 * the assets (dev builds) parses them again.
 * <pre>{@code { "version": 1, "emit": { "type": "ring", "bullet": "orb_red", "count": 12, "speed": 4,
 *   "repeat": 40, "interval": 0.1, "angleStep": 7 } } }</pre>
 * {@code emit} is one emitter or a list; see {@link Emitter} and F-024 for the fields.
 */
@PublicApi
public final class BulletPatterns {

    private static final Set<String> FIELDS = Set.of("type", "bullet", "count", "speed", "angle", "spread", "repeat",
            "interval", "delay", "angleStep", "speedStep", "accel", "turn", "amplitude", "frequency", "offset", "child",
            "childAfter");

    private final Assets assets;
    private final BulletTypes types;
    private final Map<String, Pattern> cache = new HashMap<>();
    private int generation;

    public BulletPatterns(Assets assets, BulletTypes types) {
        this.assets = assets;
        this.types = types;
        this.generation = assets.generation();
    }

    /**
     * A pattern by asset ID.
     *
     * @throws IllegalArgumentException for an unknown ID, or a bad file (lists every problem, naming the file)
     */
    public Pattern get(String id) {
        if (assets.generation() != generation) {
            generation = assets.generation();
            cache.clear();
        }
        Pattern p = cache.get(id);
        if (p == null) {
            if (!assets.contains(id)) {
                throw new IllegalArgumentException("Unknown bullet pattern \"" + id + "\": not in the asset manifest");
            }
            p = parse(id, assets.get(id, JsonValue.class), assets.path(id), types);
            cache.put(id, p);
        }
        return p;
    }

    /** Parses one pattern file (also for tests). */
    public static Pattern parse(String id, JsonValue json, String file, BulletTypes types) {
        List<String> problems = new ArrayList<>();
        List<Emitter> emitters = new ArrayList<>();
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException(file + ": must hold a JSON object");
        }
        if (json.getInt("version", 0) != 1) {
            problems.add(file + ": \"version\" must be 1");
        }
        JsonValue emit = json.get("emit");
        if (emit == null) {
            problems.add(file + ": \"emit\" is required (an emitter or a list of emitters)");
        } else if (emit.isArray()) {
            int i = 0;
            for (JsonValue e = emit.child; e != null; e = e.next, i++) {
                Emitter parsed = emitter(e, file + ": emit[" + i + "]", types, problems);
                if (parsed != null) {
                    emitters.add(parsed);
                }
            }
        } else {
            Emitter parsed = emitter(emit, file + ": emit", types, problems);
            if (parsed != null) {
                emitters.add(parsed);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bad bullet pattern \"" + id + "\":\n  " + String.join("\n  ", problems));
        }
        return new Pattern(id, emitters);
    }

    private static Emitter emitter(JsonValue e, String where, BulletTypes types, List<String> problems) {
        if (!e.isObject()) {
            problems.add(where + ": must be an object");
            return null;
        }
        int before = problems.size();
        for (JsonValue f = e.child; f != null; f = f.next) {
            if (!FIELDS.contains(f.name)) {
                problems.add(where + ": unknown field \"" + f.name + "\" (fields: " + FIELDS + ")");
            }
        }
        Emitter.Type type = null;
        String typeName = e.getString("type", "");
        try {
            type = Emitter.Type.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            problems.add(where + ": unknown type \"" + typeName + "\" (single, ring, spiral, arc, aimed, random, wave)");
        }
        String bulletName = e.getString("bullet", "");
        int bullet = -1;
        if (!types.contains(bulletName)) {
            problems.add(where + ": unknown bullet \"" + bulletName + "\"");
        } else {
            bullet = types.index(bulletName);
        }
        int count = (int) num(e, "count", 1f, where, problems);
        int repeat = (int) num(e, "repeat", 1f, where, problems);
        float interval = num(e, "interval", 0.1f, where, problems);
        float delay = num(e, "delay", 0f, where, problems);
        if (count < 1 || repeat < 1 || interval < 0f || delay < 0f) {
            problems.add(where + ": count and repeat must be >= 1, interval and delay >= 0");
        }
        Emitter child = null;
        JsonValue c = e.get("child");
        if (c != null) {
            child = emitter(c, where + ".child", types, problems);
        }
        float childAfter = num(e, "childAfter", 1f, where, problems);
        float speed = num(e, "speed", 5f, where, problems);
        float angle = num(e, "angle", 270f, where, problems);
        float spread = num(e, "spread", 30f, where, problems);
        float angleStep = num(e, "angleStep", 0f, where, problems);
        float speedStep = num(e, "speedStep", 0f, where, problems);
        float accel = num(e, "accel", 0f, where, problems);
        float turn = num(e, "turn", 0f, where, problems);
        float amplitude = num(e, "amplitude", 30f, where, problems);
        float frequency = num(e, "frequency", 8f, where, problems);
        float offset = num(e, "offset", 0f, where, problems);
        if (problems.size() != before) {
            return null;
        }
        return new Emitter(type, bullet, count, speed, angle, spread, repeat, interval, delay, angleStep, speedStep,
                accel, turn, amplitude, frequency, offset, child, childAfter);
    }

    private static float num(JsonValue e, String name, float fallback, String where, List<String> problems) {
        JsonValue v = e.get(name);
        if (v == null) {
            return fallback;
        }
        if (!v.isNumber()) {
            problems.add(where + ": \"" + name + "\" must be a number");
            return fallback;
        }
        return v.asFloat();
    }
}
