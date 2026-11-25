package dev.ember2d.ecs;

import com.badlogic.gdx.utils.Bits;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.PublicApi;
import java.util.Arrays;

/**
 * The entities that have all required component types and none of the excluded ones (F-003). Families are
 * cached per world and kept up to date as components are added and removed; entities stay in the order they
 * joined.
 */
@PublicApi
public final class Family {

    private final World world;
    final Class<?>[] required;
    final Class<?>[] excluded;
    private final IntArray entities = new IntArray(true, 64);
    private final Bits members = new Bits();

    Family(World world, Class<?>[] required, Class<?>[] excluded) {
        this.world = world;
        this.required = required;
        this.excluded = excluded;
    }

    /** The member entity handles, in the order they joined. Read-only: do not modify the returned array. */
    public IntArray entities() {
        return entities;
    }

    public int size() {
        return entities.size;
    }

    /** The cached family with these types excluded in addition. */
    public Family exclude(Class<?>... types) {
        Class<?>[] combined = Arrays.copyOf(excluded, excluded.length + types.length);
        System.arraycopy(types, 0, combined, excluded.length, types.length);
        return world.family(required, combined);
    }

    boolean sameTypes(Class<?>[] otherRequired, Class<?>[] otherExcluded) {
        return sameSet(required, otherRequired) && sameSet(excluded, otherExcluded);
    }

    /** Re-checks one entity after a structural change. */
    void update(int handle, int index, boolean matches) {
        boolean member = members.get(index);
        if (matches && !member) {
            members.set(index);
            entities.add(handle);
        } else if (!matches && member) {
            members.clear(index);
            entities.removeValue(handle);
        }
    }

    void clear() {
        entities.clear();
        members.clear();
    }

    @Override
    public String toString() {
        return "Family[required=" + names(required) + ", excluded=" + names(excluded) + ", size=" + entities.size + "]";
    }

    private static boolean sameSet(Class<?>[] a, Class<?>[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (Class<?> type : a) {
            boolean found = false;
            for (Class<?> other : b) {
                if (type == other) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static String names(Class<?>[] types) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < types.length; i++) {
            text.append(i == 0 ? "" : ", ").append(types[i].getSimpleName());
        }
        return text.append(']').toString();
    }
}
