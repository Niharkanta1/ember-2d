package dev.ember2d.kit.bullets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bullet types of a game (F-024), parsed from a JSON asset:
 * <pre>{@code { "version": 1, "types": { "orb_red": { "region": "tex.orb", "radius": 0.15, "damage": 1 } } } }</pre>
 * Fields: {@code region} (required), {@code radius} 0.15, {@code damage} 1, {@code lifetime} 10, {@code walls} false,
 * {@code pierce} false, {@code graze} true, {@code rotate} false, {@code scale} 1, {@code rotationOffset} 0 (degrees),
 * {@code tint} "#FFFFFF" ({@code "#RRGGBB"} or {@code "#RRGGBBAA"}), {@code fogged} false.
 */
@PublicApi
public final class BulletTypes {

    private static final Set<String> FIELDS = Set.of("region", "radius", "damage", "lifetime", "walls", "pierce",
            "graze", "rotate", "scale", "rotationOffset", "tint", "fogged");

    private final List<BulletType> types;
    private final Map<String, Integer> byName = new LinkedHashMap<>();

    private BulletTypes(List<BulletType> types) {
        this.types = List.copyOf(types);
        for (int i = 0; i < types.size(); i++) {
            byName.put(types.get(i).name, i);
        }
    }

    /**
     * Parses a bullet types file.
     *
     * @throws IllegalArgumentException listing every problem, each naming the file and the type
     */
    public static BulletTypes parse(JsonValue json, String file) {
        List<String> problems = new ArrayList<>();
        List<BulletType> types = new ArrayList<>();
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException(file + ": must hold a JSON object");
        }
        if (json.getInt("version", 0) != 1) {
            problems.add(file + ": \"version\" must be 1");
        }
        JsonValue all = json.get("types");
        if (all == null || !all.isObject() || all.child == null) {
            problems.add(file + ": \"types\" must be an object with at least one type");
        } else {
            for (JsonValue t = all.child; t != null; t = t.next) {
                String where = file + ": bullet \"" + t.name + "\"";
                for (JsonValue f = t.child; f != null; f = f.next) {
                    if (!FIELDS.contains(f.name)) {
                        problems.add(where + ": unknown field \"" + f.name + "\" (fields: " + FIELDS + ")");
                    }
                }
                String region = t.getString("region", "");
                if (region.isEmpty()) {
                    problems.add(where + ": \"region\" is required");
                }
                float radius = number(t, "radius", 0.15f, where, problems);
                float lifetime = number(t, "lifetime", 10f, where, problems);
                float scale = number(t, "scale", 1f, where, problems);
                int damage = (int) number(t, "damage", 1f, where, problems);
                if (radius <= 0f || lifetime <= 0f || scale <= 0f || damage < 0) {
                    problems.add(where + ": radius, lifetime and scale must be > 0 and damage >= 0");
                }
                float rotationOffset = number(t, "rotationOffset", 0f, where, problems);
                Color tint = Color.WHITE;
                String tintText = t.getString("tint", null);
                if (tintText != null) {
                    String hex = tintText.startsWith("#") ? tintText.substring(1) : tintText;
                    if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        problems.add(where + ": \"tint\" must look like \"#RRGGBB\" or \"#RRGGBBAA\"");
                    } else {
                        tint = Color.valueOf(hex);
                    }
                }
                types.add(new BulletType(t.name, region, radius, damage, lifetime, t.getBoolean("walls", false),
                        t.getBoolean("pierce", false), t.getBoolean("graze", true), t.getBoolean("rotate", false),
                        scale, rotationOffset, tint, t.getBoolean("fogged", false)));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bad bullet types:\n  " + String.join("\n  ", problems));
        }
        return new BulletTypes(types);
    }

    private static float number(JsonValue t, String name, float fallback, String where, List<String> problems) {
        JsonValue v = t.get(name);
        if (v == null) {
            return fallback;
        }
        if (!v.isNumber()) {
            problems.add(where + ": \"" + name + "\" must be a number");
            return fallback;
        }
        return v.asFloat();
    }

    /**
     * The index of a type, used by {@link BulletWorld#spawn}.
     *
     * @throws IllegalArgumentException for an unknown name (lists the known ones)
     */
    public int index(String name) {
        Integer i = byName.get(name);
        if (i == null) {
            throw new IllegalArgumentException("Unknown bullet type \"" + name + "\"; known: " + byName.keySet());
        }
        return i;
    }

    public boolean contains(String name) {
        return byName.containsKey(name);
    }

    public BulletType get(int index) {
        return types.get(index);
    }

    public int size() {
        return types.size();
    }
}
