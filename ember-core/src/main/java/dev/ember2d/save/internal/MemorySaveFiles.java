package dev.ember2d.save.internal;

import java.util.HashMap;
import java.util.Map;

/** Saves kept in memory: tests, and games without {@code EngineConfig.saveFolder}. */
public final class MemorySaveFiles implements SaveFiles {

    private final Map<String, String> files = new HashMap<>();

    @Override
    public String read(String name) {
        return files.get(name);
    }

    @Override
    public boolean exists(String name) {
        return files.containsKey(name);
    }

    @Override
    public void write(String name, String text) {
        String old = files.put(name, text);
        if (old != null) {
            files.put(name + ".bak", old);
        }
    }

    @Override
    public boolean delete(String name) {
        return files.remove(name) != null;
    }

    @Override
    public String describe() {
        return "memory (not kept)";
    }
}
