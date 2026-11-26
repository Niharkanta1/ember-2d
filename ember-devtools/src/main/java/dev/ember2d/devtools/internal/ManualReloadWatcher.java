package dev.ember2d.devtools.internal;

import dev.ember2d.assets.AssetWatcher;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * File changes plus the Assets panel's "Reload" requests (F-018, I-160): both go through the engine's normal hot
 * reload path (F-008).
 */
public final class ManualReloadWatcher implements AssetWatcher {

    private final AssetWatcher files;
    private final Set<String> requested = new LinkedHashSet<>();   // guarded by this

    public ManualReloadWatcher(AssetWatcher files) {
        this.files = Objects.requireNonNull(files, "files");
    }

    /** Reloads the file at this path (relative to the assets folder) on the next frame. */
    public synchronized void request(String path) {
        requested.add(path.replace('\\', '/'));
    }

    @Override
    public void drainChanges(Consumer<String> changedPath) {
        files.drainChanges(changedPath);
        List<String> manual;
        synchronized (this) {
            if (requested.isEmpty()) {
                return;
            }
            manual = new ArrayList<>(requested);
            requested.clear();
        }
        manual.forEach(changedPath);
    }

    @Override
    public void close() {
        files.close();
    }
}
