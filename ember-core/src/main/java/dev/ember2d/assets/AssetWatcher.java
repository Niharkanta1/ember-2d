package dev.ember2d.assets;

import dev.ember2d.core.PublicApi;
import java.util.function.Consumer;

/**
 * Reports changed asset files for hot reload (dev builds, F-008). Set as {@code EngineConfig.assetWatcher}; the
 * desktop implementation is {@code dev.ember2d.devtools.DirectoryWatcher}. The engine drains it once per frame on the
 * render thread and closes it at shutdown.
 */
@PublicApi
public interface AssetWatcher extends AutoCloseable {

    /** Hands over every change reported since the last call: paths relative to the assets folder, with '/'. */
    void drainChanges(Consumer<String> changedPath);

    @Override
    void close();
}
