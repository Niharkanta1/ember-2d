package dev.ember2d.kit.novel;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The cast of a story and the stage positions (F-023), parsed from a JSON asset:
 * <pre>{@code { "version": 1, "positions": { "left": 0.22 },
 *   "characters": { "alice": { "name": "Alice", "color": "#FF8FA3", "voice": "voice.alice_", "position": "left",
 *     "expressions": { "happy": "atlas.novel/alice_happy" }, "default": "happy" } } } }</pre>
 * Positions are fractions of the screen width; {@code left} 0.22, {@code center} 0.5 and {@code right} 0.78 are
 * built in and can be overridden.
 */
@PublicApi
public final class NovelCharacters {

    /** One character. */
    public record Character(String id, String name, Color color, String voice, String position,
            Map<String, String> expressions, String defaultExpression) {
    }

    private static final Set<String> FIELDS = Set.of("name", "color", "voice", "position", "expressions", "default");

    private final Map<String, Character> characters;
    private final Map<String, Float> positions;

    private NovelCharacters(Map<String, Character> characters, Map<String, Float> positions) {
        this.characters = Collections.unmodifiableMap(characters);
        this.positions = Collections.unmodifiableMap(positions);
    }

    /** An empty cast with the built-in positions (stories without characters). */
    public static NovelCharacters none() {
        return new NovelCharacters(new LinkedHashMap<>(), builtInPositions());
    }

    private static Map<String, Float> builtInPositions() {
        Map<String, Float> p = new LinkedHashMap<>();
        p.put("left", 0.22f);
        p.put("center", 0.5f);
        p.put("right", 0.78f);
        return p;
    }

    /**
     * @throws IllegalArgumentException listing every problem, each naming the file and the character
     */
    public static NovelCharacters parse(JsonValue json, String file) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException(file + ": must hold a JSON object");
        }
        List<String> problems = new ArrayList<>();
        if (json.getInt("version", 0) != 1) {
            problems.add(file + ": \"version\" must be 1");
        }
        Map<String, Float> positions = builtInPositions();
        JsonValue p = json.get("positions");
        if (p != null) {
            for (JsonValue v = p.child; v != null; v = v.next) {
                if (!v.isNumber()) {
                    problems.add(file + ": position \"" + v.name + "\" must be a number (a fraction of the width)");
                } else {
                    positions.put(v.name, v.asFloat());
                }
            }
        }
        Map<String, Character> characters = new LinkedHashMap<>();
        JsonValue all = json.get("characters");
        if (all != null) {
            for (JsonValue c = all.child; c != null; c = c.next) {
                String where = file + ": character \"" + c.name + "\"";
                for (JsonValue f = c.child; f != null; f = f.next) {
                    if (!FIELDS.contains(f.name)) {
                        problems.add(where + ": unknown field \"" + f.name + "\" (fields: " + FIELDS + ")");
                    }
                }
                Color color = new Color(Color.WHITE);
                String hex = c.getString("color", null);
                if (hex != null) {
                    String h = hex.startsWith("#") ? hex.substring(1) : hex;
                    if (!h.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
                        problems.add(where + ": \"color\" must be #RRGGBB or #RRGGBBAA");
                    } else {
                        color = Color.valueOf(h);
                    }
                }
                Map<String, String> expressions = new LinkedHashMap<>();
                JsonValue e = c.get("expressions");
                if (e != null) {
                    for (JsonValue x = e.child; x != null; x = x.next) {
                        expressions.put(x.name, x.asString());
                    }
                }
                String def = c.getString("default", expressions.isEmpty() ? null : expressions.keySet().iterator().next());
                if (def != null && !expressions.containsKey(def)) {
                    problems.add(where + ": default expression \"" + def + "\" is not in its expressions");
                }
                String position = c.getString("position", "center");
                if (!positions.containsKey(position)) {
                    problems.add(where + ": unknown position \"" + position + "\" (positions: " + positions.keySet()
                            + ")");
                }
                characters.put(c.name, new Character(c.name, c.getString("name", c.name), color,
                        c.getString("voice", null), position, Collections.unmodifiableMap(expressions), def));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bad characters:\n  " + String.join("\n  ", problems));
        }
        return new NovelCharacters(characters, positions);
    }

    /** A character by ID, or null. */
    public Character get(String id) {
        return characters.get(id);
    }

    /** The character a speaker name means: its ID (any case) or its display name; null if none. */
    public Character forSpeaker(String speaker) {
        if (speaker == null || speaker.isEmpty()) {
            return null;
        }
        Character byId = characters.get(speaker.toLowerCase(Locale.ROOT));
        if (byId != null) {
            return byId;
        }
        for (Character c : characters.values()) {
            if (c.id().equalsIgnoreCase(speaker) || c.name().equalsIgnoreCase(speaker)) {
                return c;
            }
        }
        return null;
    }

    /** A position's fraction of the width, or null for an unknown position. */
    public Float position(String name) {
        return positions.get(name);
    }

    public Map<String, Character> all() {
        return characters;
    }
}
