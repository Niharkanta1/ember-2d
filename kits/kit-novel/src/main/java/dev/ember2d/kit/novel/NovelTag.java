package dev.ember2d.kit.novel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One staging tag (F-023): {@code name: arg arg key=value}. Brackets are ignored, and a number right after
 * {@code transition=x} is its time ({@code [transition=fade 1.0]}).
 */
final class NovelTag {

    /** The staging commands; other tags are left to the game. */
    static final Set<String> COMMANDS = Set.of("bg", "show", "expr", "hide", "music", "stopmusic", "sfx", "voice", "cg",
            "hidecg", "wait", "shake", "flash");
    /** Dialogue tags read by DialogueRunner, not staging. */
    static final Set<String> DIALOGUE = Set.of("speaker", "mood");

    final String name;
    final List<String> args;
    final Map<String, String> options;

    private NovelTag(String name, List<String> args, Map<String, String> options) {
        this.name = name;
        this.args = args;
        this.options = options;
    }

    /**
     * The command in a tag, or null for a tag that is not staging (a dialogue tag, or a plain word the game reads).
     *
     * @throws IllegalArgumentException for an unknown {@code name:} tag
     */
    static NovelTag parse(String tag) {
        String text = tag.trim();
        int colon = text.indexOf(':');
        String name = (colon < 0 ? text : text.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
        if (DIALOGUE.contains(name)) {
            return null;
        }
        if (!COMMANDS.contains(name)) {
            if (colon < 0 || name.contains(" ")) {
                return null;   // a plain tag for the game
            }
            throw new IllegalArgumentException("unknown staging tag \"" + name + ":\" (tags: " + COMMANDS + ")");
        }
        String rest = colon < 0 ? "" : text.substring(colon + 1);
        rest = rest.replace('[', ' ').replace(']', ' ').trim();
        List<String> args = new ArrayList<>();
        Map<String, String> options = new LinkedHashMap<>();
        String lastOption = null;
        for (String word : rest.isEmpty() ? new String[0] : rest.split("\\s+")) {
            int eq = word.indexOf('=');
            if (eq > 0) {
                lastOption = word.substring(0, eq).toLowerCase(Locale.ROOT);
                options.put(lastOption, word.substring(eq + 1));
            } else if ("transition".equals(lastOption) && isNumber(word)) {
                options.put("time", word);
                lastOption = null;
            } else {
                args.add(word);
                lastOption = null;
            }
        }
        return new NovelTag(name, Collections.unmodifiableList(args), Collections.unmodifiableMap(options));
    }

    String arg(int i) {
        return i < args.size() ? args.get(i) : null;
    }

    /** A number option, or the default; a bad number throws IllegalArgumentException. */
    float number(String option, float defaultValue) {
        String v = options.get(option);
        return v == null ? defaultValue : parseNumber(option, v);
    }

    static float parseNumber(String what, String text) {
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text + "\" is not a number (" + what + ")");
        }
    }

    private static boolean isNumber(String word) {
        try {
            Float.parseFloat(word);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
