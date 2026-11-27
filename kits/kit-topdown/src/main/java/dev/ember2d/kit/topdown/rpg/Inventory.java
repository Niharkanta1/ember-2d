package dev.ember2d.kit.topdown.rpg;

import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.save.SaveData;
import java.util.ArrayList;
import java.util.List;

/**
 * Items carried by an entity (F-017): up to {@link #capacity} slots, each one item with a count up to its stack size.
 * Adding fills existing stacks first, then empty slots; removing is all-or-nothing and takes from the last stacks first.
 * The contents are runtime state; {@link #save} and {@link #load} put them in a save slot.
 */
@PublicApi
public final class Inventory {

    private static final Log LOG = Log.get("rpg");

    /** How many slots. */
    public int capacity = 12;

    private final List<String> items = new ArrayList<>();
    private final List<Integer> counts = new ArrayList<>();

    /**
     * Adds up to {@code count} of an item; returns how many fitted.
     *
     * @throws IllegalArgumentException for an item the catalog does not list, or a negative count
     */
    public int add(ItemCatalog catalog, String item, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0 (was " + count + ")");
        }
        int stack = catalog.get(item).stack();
        int left = count;
        for (int i = 0; i < items.size() && left > 0; i++) {
            if (items.get(i).equals(item) && counts.get(i) < stack) {
                int put = Math.min(left, stack - counts.get(i));
                counts.set(i, counts.get(i) + put);
                left -= put;
            }
        }
        while (left > 0 && items.size() < capacity) {
            int put = Math.min(left, stack);
            items.add(item);
            counts.add(put);
            left -= put;
        }
        return count - left;
    }

    /** Removes {@code count} of an item when there are that many; returns false (and changes nothing) otherwise. */
    public boolean remove(String item, int count) {
        if (count < 0 || count(item) < count) {
            return false;
        }
        int left = count;
        for (int i = items.size() - 1; i >= 0 && left > 0; i--) {
            if (items.get(i).equals(item)) {
                int take = Math.min(left, counts.get(i));
                left -= take;
                if (take == counts.get(i)) {
                    items.remove(i);
                    counts.remove(i);
                } else {
                    counts.set(i, counts.get(i) - take);
                }
            }
        }
        return true;
    }

    /** How many of an item, over all slots. */
    public int count(String item) {
        int total = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).equals(item)) {
                total += counts.get(i);
            }
        }
        return total;
    }

    public boolean has(String item, int count) {
        return count(item) >= count;
    }

    /** Whether any carried item has the tag. */
    public boolean hasTag(ItemCatalog catalog, String tag) {
        for (String item : items) {
            if (catalog.contains(item) && catalog.get(item).tags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    /** Slots in use. */
    public int size() {
        return items.size();
    }

    public String item(int slot) {
        return items.get(slot);
    }

    public int count(int slot) {
        return counts.get(slot);
    }

    public void clear() {
        items.clear();
        counts.clear();
    }

    /** Writes the slots into {@code out} (a list "slots" of {item, count}). */
    public void save(SaveData out) {
        List<SaveData> slots = out.list("slots");
        slots.clear();
        for (int i = 0; i < items.size(); i++) {
            slots.add(new SaveData().set("item", items.get(i)).set("count", counts.get(i)));
        }
    }

    /** Replaces the slots from {@link #save} data; unknown items are skipped and stacks clamped (logged). */
    public void load(SaveData in, ItemCatalog catalog) {
        clear();
        for (SaveData slot : in.list("slots")) {
            String item = slot.getString("item", "");
            int count = slot.getInt("count", 0);
            if (!catalog.contains(item)) {
                LOG.warn("Saved inventory: unknown item \"" + item + "\" skipped");
                continue;
            }
            int stack = catalog.get(item).stack();
            if (count > stack) {
                LOG.warn("Saved inventory: " + count + " x " + item + " is over the stack size " + stack + "; clamped");
            }
            if (count > 0 && items.size() < capacity) {
                items.add(item);
                counts.add(Math.min(count, stack));
            }
        }
    }
}
