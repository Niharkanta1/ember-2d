package dev.ember2d.kit.topdown.rpg;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The item definitions of a game (F-017), parsed from a JSON asset:
 * <pre>{@code { "version": 1, "items": { "potion": { "name": "Potion", "icon": "atlas.rpg/potion", "stack": 9,
 *   "tags": ["consumable"] } } } }</pre>
 * {@code name} is required; {@code icon} (a region ID) is optional; {@code stack} defaults to 1; {@code tags} to none.
 */
@PublicApi
public final class ItemCatalog {

    /** One item definition. */
    public record Item(String id, String name, String icon, int stack, List<String> tags) {
    }

    private static final Set<String> FIELDS = Set.of("name", "icon", "stack", "tags");

    private final Map<String, Item> items;

    private ItemCatalog(Map<String, Item> items) {
        this.items = Collections.unmodifiableMap(items);
    }

    /**
     * @throws IllegalArgumentException listing every problem, each naming the file and the item
     */
    public static ItemCatalog parse(JsonValue json, String file) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException(file + ": must hold a JSON object");
        }
        List<String> problems = new ArrayList<>();
        if (json.getInt("version", 0) != 1) {
            problems.add(file + ": \"version\" must be 1");
        }
        Map<String, Item> items = new LinkedHashMap<>();
        JsonValue all = json.get("items");
        if (all == null || !all.isObject()) {
            problems.add(file + ": \"items\" must be an object");
        } else {
            for (JsonValue it = all.child; it != null; it = it.next) {
                String where = file + ": item \"" + it.name + "\"";
                for (JsonValue f = it.child; f != null; f = f.next) {
                    if (!FIELDS.contains(f.name)) {
                        problems.add(where + ": unknown field \"" + f.name + "\" (fields: " + FIELDS + ")");
                    }
                }
                String name = it.getString("name", "");
                if (name.isEmpty()) {
                    problems.add(where + ": \"name\" is required");
                }
                JsonValue stackValue = it.get("stack");
                int stack = 1;
                if (stackValue != null) {
                    if (!stackValue.isNumber() || stackValue.asInt() < 1) {
                        problems.add(where + ": \"stack\" must be a whole number >= 1");
                    } else {
                        stack = stackValue.asInt();
                    }
                }
                List<String> tags = new ArrayList<>();
                JsonValue t = it.get("tags");
                if (t != null) {
                    if (!t.isArray()) {
                        problems.add(where + ": \"tags\" must be a list of strings");
                    } else {
                        for (JsonValue tag = t.child; tag != null; tag = tag.next) {
                            tags.add(tag.asString());
                        }
                    }
                }
                items.put(it.name, new Item(it.name, name, it.getString("icon", null), stack, List.copyOf(tags)));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("Bad items:\n  " + String.join("\n  ", problems));
        }
        return new ItemCatalog(items);
    }

    public boolean contains(String id) {
        return items.containsKey(id);
    }

    /**
     * @throws IllegalArgumentException for an unknown item (lists the known ones)
     */
    public Item get(String id) {
        Item item = items.get(id);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item \"" + id + "\"; known: " + items.keySet());
        }
        return item;
    }

    /** Every item, in file order. */
    public Map<String, Item> all() {
        return items;
    }
}
