package dev.ember2d.kit.novel;

import dev.ember2d.core.PublicApi;
import dev.ember2d.save.SaveData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * What the reader has seen across play-throughs (F-023): read lines (for skip mode) and unlocked CGs (for a gallery).
 * Kept in a {@link SaveData} the game takes from {@code saves().global()}; flush the store to write it.
 */
@PublicApi
public final class NovelMemory {

    private final SaveData data;

    /** @param global usually {@code context.saves().global().child("novel")} */
    public NovelMemory(SaveData global) {
        this.data = Objects.requireNonNull(global, "global");
    }

    /** A memory kept nowhere (tests, scenes that do not track reading). */
    public static NovelMemory none() {
        return new NovelMemory(new SaveData());
    }

    public boolean isRead(String speaker, String text) {
        return data.child("read").has(key(speaker, text));
    }

    public void markRead(String speaker, String text) {
        data.child("read").set(key(speaker, text), true);
    }

    public void unlockCg(String region) {
        data.child("cg").set(region, true);
    }

    public boolean isUnlocked(String region) {
        return data.child("cg").has(region);
    }

    /** Unlocked CGs in the order they were first seen. */
    public List<String> unlockedCgs() {
        return new ArrayList<>(data.child("cg").keys());
    }

    private static String key(String speaker, String text) {
        return Integer.toHexString(Objects.hash(speaker == null ? "" : speaker, text));
    }
}
