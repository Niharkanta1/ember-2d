package dev.ember2d.i18n;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.event.EventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Localised text (F-025), available as {@code context.strings()}: one string table per language, the JSON asset
 * {@code strings.<language>}:
 * <pre>{@code { "version": 1, "language": "de", "strings": { "menu.play": "Spielen", "hud.score": "Punkte {0}" } } }</pre>
 * {@link #get} uses the current language, then the default language, then returns {@code [key]}. A game without any
 * {@code strings.*} assets gets its keys back unchanged.
 */
@PublicApi
public final class Strings {

    private static final Log LOG = Log.get("i18n");
    private static final String PREFIX = "strings.";

    private final Assets assets;
    private final EventBus events;
    private final String defaultLanguage;
    private final boolean devMode;
    private final Map<String, Map<String, String>> tables = new HashMap<>();
    private final Set<String> reportedMissing = new HashSet<>();
    private String language;
    private int generation = Integer.MIN_VALUE;

    /** @param events receives {@link LanguageChanged}; may be null */
    public Strings(Assets assets, EventBus events, String defaultLanguage, boolean devMode) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.events = events;
        this.defaultLanguage = Objects.requireNonNull(defaultLanguage, "defaultLanguage");
        this.devMode = devMode;
        this.language = defaultLanguage;
    }

    /** The current language code. */
    public String language() {
        return language;
    }

    /** The languages with a {@code strings.<code>} asset in the manifest, in manifest order. */
    public List<String> languages() {
        List<String> out = new ArrayList<>();
        for (String id : assets.ids()) {
            if (id.startsWith(PREFIX)) {
                out.add(id.substring(PREFIX.length()));
            }
        }
        return out;
    }

    /**
     * Switches the language and publishes {@link LanguageChanged}.
     *
     * @throws IllegalArgumentException for a language without a string table (lists the known ones)
     */
    public void setLanguage(String code) {
        if (!assets.contains(PREFIX + code)) {
            throw new IllegalArgumentException("No string table for language \"" + code + "\"; languages: "
                    + languages());
        }
        if (!code.equals(language)) {
            language = code;
            if (events != null) {
                events.publish(new LanguageChanged(code));
            }
        }
    }

    /** The text for a key: the current language, then the default language, then {@code [key]}. */
    public String get(String key) {
        if (!assets.contains(PREFIX + language) && !assets.contains(PREFIX + defaultLanguage)) {
            return key;   // a game without string tables
        }
        String text = lookup(language, key);
        if (text == null && !language.equals(defaultLanguage)) {
            text = lookup(defaultLanguage, key);
        }
        if (text == null) {
            if (devMode && reportedMissing.add(language + "/" + key)) {
                LOG.warn("Missing text \"" + key + "\" for language " + language);
            }
            return "[" + key + "]";
        }
        return text;
    }

    /** Whether the current or the default language has the key. */
    public boolean has(String key) {
        return lookup(language, key) != null || lookup(defaultLanguage, key) != null;
    }

    /** {@link #get} with {@code {0}}, {@code {1}}... replaced by the arguments; {@code {{} writes a "{". */
    public String format(String key, Object... args) {
        String text = get(key);
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                out.append('{');
                i++;
                continue;
            }
            if (c == '{') {
                int close = text.indexOf('}', i);
                if (close > i + 1) {
                    String number = text.substring(i + 1, close);
                    if (digits(number)) {
                        int n = Integer.parseInt(number);
                        if (n < args.length) {
                            out.append(args[n]);
                            i = close;
                            continue;
                        }
                    }
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    /** {@code baseId + "." + language} when the manifest lists it (a translated story or image), else {@code baseId}. */
    public String localised(String baseId) {
        String id = baseId + "." + language;
        return assets.contains(id) ? id : baseId;
    }

    /** True for a non-empty run of ASCII digits (String.chars() needs API 24). */
    private static boolean digits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return !text.isEmpty();
    }

    private String lookup(String code, String key) {
        if (assets.generation() != generation) {
            generation = assets.generation();
            tables.clear();   // hot reload: read the tables again
        }
        Map<String, String> table = tables.get(code);
        if (table == null) {
            if (!assets.contains(PREFIX + code)) {
                return null;
            }
            table = parse(code);
            tables.put(code, table);
        }
        return table.get(key);
    }

    private Map<String, String> parse(String code) {
        String id = PREFIX + code;
        String file = assets.path(id);
        JsonValue json = assets.get(id, JsonValue.class);
        List<String> problems = new ArrayList<>();
        if (json.getInt("version", 0) != 1) {
            problems.add("\"version\" must be 1");
        }
        String declared = json.getString("language", code);
        if (!declared.equals(code)) {
            problems.add("\"language\" is \"" + declared + "\" but the asset is " + id);
        }
        Map<String, String> table = new HashMap<>();
        JsonValue strings = json.get("strings");
        if (strings == null || !strings.isObject()) {
            problems.add("\"strings\" must be an object");
        } else {
            for (JsonValue s = strings.child; s != null; s = s.next) {
                if (!s.isString()) {
                    problems.add("\"" + s.name + "\" must be a string");
                } else {
                    table.put(s.name, s.asString());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bad string table " + file + ":\n  " + String.join("\n  ", problems));
        }
        return Collections.unmodifiableMap(table);
    }
}
