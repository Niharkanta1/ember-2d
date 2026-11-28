package dev.ember2d.tools.assets;

import com.badlogic.gdx.utils.JsonValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Writes JSON in the compact one-line style of the demos' manifests ({@code {"id": "x", "type": "json"}}). */
final class Json {

    private Json() {
    }

    /** A parsed value, keeping numbers exactly as they were written. */
    static String write(JsonValue v) {
        switch (v.type()) {
            case object: {
                List<String> parts = new ArrayList<>();
                for (JsonValue c = v.child; c != null; c = c.next) {
                    parts.add(quote(c.name) + ": " + write(c));
                }
                return "{" + String.join(", ", parts) + "}";
            }
            case array: {
                List<String> parts = new ArrayList<>();
                for (JsonValue c = v.child; c != null; c = c.next) {
                    parts.add(write(c));
                }
                return "[" + String.join(", ", parts) + "]";
            }
            case stringValue:
                return quote(v.asString());
            case nullValue:
                return "null";
            default:
                return v.asString();   // numbers and booleans as written
        }
    }

    /** Maps, lists, strings, numbers and booleans. */
    static String write(Object o) {
        if (o instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                parts.add(quote(String.valueOf(e.getKey())) + ": " + write(e.getValue()));
            }
            return "{" + String.join(", ", parts) + "}";
        }
        if (o instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) {
                parts.add(write(item));
            }
            return "[" + String.join(", ", parts) + "]";
        }
        if (o instanceof String s) {
            return quote(s);
        }
        if (o instanceof JsonValue v) {
            return write(v);
        }
        return String.valueOf(o);
    }

    static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
