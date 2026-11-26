package dev.ember2d.collision;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.LongMap;

/**
 * A uniform grid of entities by box (F-006). Each entity is listed in every cell its box touches. Cell lists and
 * entries are reused, so steady-state updates and queries do not allocate.
 */
final class SpatialHash {

    /** The cells an entity is listed in. */
    private static final class Entry {
        int minX;
        int minY;
        int maxX;
        int maxY;
        int stamp;
    }

    private final float cellSize;
    private final LongMap<IntArray> cells = new LongMap<>();
    private final IntMap<Entry> entries = new IntMap<>();
    private final Array<Entry> freeEntries = new Array<>(false, 16);
    private final IntSet seen = new IntSet();
    private final IntArray removals = new IntArray();

    SpatialHash(float cellSize) {
        this.cellSize = cellSize;
    }

    int size() {
        return entries.size;
    }

    boolean contains(int entity) {
        return entries.containsKey(entity);
    }

    /** Inserts or moves an entity; {@code stamp} marks it as seen in the current sync. */
    void put(int entity, float x, float y, float width, float height, int stamp) {
        int minX = cell(x);
        int minY = cell(y);
        int maxX = cell(x + width);
        int maxY = cell(y + height);
        Entry e = entries.get(entity);
        if (e == null) {
            e = freeEntries.size > 0 ? freeEntries.pop() : new Entry();
            entries.put(entity, e);
            set(e, minX, minY, maxX, maxY);
            add(entity, e);
        } else if (e.minX != minX || e.minY != minY || e.maxX != maxX || e.maxY != maxY) {
            remove(entity, e);
            set(e, minX, minY, maxX, maxY);
            add(entity, e);
        }
        e.stamp = stamp;
    }

    void remove(int entity) {
        Entry e = entries.remove(entity);
        if (e != null) {
            remove(entity, e);
            freeEntries.add(e);
        }
    }

    /** Removes every entity not put with this stamp; adds their ids to {@code out}. */
    void removeUnstamped(int stamp, IntArray out) {
        removals.clear();
        for (IntMap.Entry<Entry> e : entries) {
            if (e.value.stamp != stamp) {
                removals.add(e.key);
            }
        }
        for (int i = 0; i < removals.size; i++) {
            remove(removals.get(i));
            out.add(removals.get(i));
        }
    }

    /** Adds every entity listed in a cell touched by the area to {@code out}, each once, sorted by id. */
    void query(float x, float y, float width, float height, IntArray out) {
        out.clear();
        seen.clear();
        int minX = cell(x);
        int minY = cell(y);
        int maxX = cell(x + width);
        int maxY = cell(y + height);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                IntArray list = cells.get(key(cx, cy));
                if (list == null) {
                    continue;
                }
                for (int i = 0; i < list.size; i++) {
                    if (seen.add(list.get(i))) {
                        out.add(list.get(i));
                    }
                }
            }
        }
        out.sort();
    }

    private int cell(float v) {
        return (int) Math.floor(v / cellSize);
    }

    private static void set(Entry e, int minX, int minY, int maxX, int maxY) {
        e.minX = minX;
        e.minY = minY;
        e.maxX = maxX;
        e.maxY = maxY;
    }

    private void add(int entity, Entry e) {
        for (int cy = e.minY; cy <= e.maxY; cy++) {
            for (int cx = e.minX; cx <= e.maxX; cx++) {
                long k = key(cx, cy);
                IntArray list = cells.get(k);
                if (list == null) {
                    list = new IntArray(4);
                    cells.put(k, list);
                }
                list.add(entity);
            }
        }
    }

    private void remove(int entity, Entry e) {
        for (int cy = e.minY; cy <= e.maxY; cy++) {
            for (int cx = e.minX; cx <= e.maxX; cx++) {
                IntArray list = cells.get(key(cx, cy));
                if (list != null) {
                    list.removeValue(entity);   // the empty list stays for reuse
                }
            }
        }
    }

    private static long key(int cx, int cy) {
        return ((long) cx << 32) ^ (cy & 0xffffffffL);
    }
}
