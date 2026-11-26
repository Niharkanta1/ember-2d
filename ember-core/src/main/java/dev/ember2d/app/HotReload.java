package dev.ember2d.app;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.AssetReloaded;
import dev.ember2d.assets.AssetWatcher;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.Log;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.internal.DefaultInput;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Hot reload driver (dev builds, F-008, I-150): once per frame, on the render thread, takes the changed files from the
 * {@link AssetWatcher}, reloads the affected assets, publishes {@link AssetReloaded}, applies changed input bindings
 * and restarts the current scene after a map change that the scene did not handle.
 */
final class HotReload {

    private static final Log LOG = Log.get("hotreload");

    private final AssetWatcher watcher;
    private final GdxAssets assets;
    private final SceneManager scenes;
    private final DefaultEventBus events;
    private final DefaultInput input;
    private final String inputBindings;
    private final Set<String> changed = new LinkedHashSet<>();

    HotReload(AssetWatcher watcher, GdxAssets assets, SceneManager scenes, DefaultEventBus events, DefaultInput input,
            String inputBindings) {
        this.watcher = watcher;
        this.assets = assets;
        this.scenes = scenes;
        this.events = events;
        this.input = input;
        this.inputBindings = inputBindings;
    }

    /** Returns true when something was reloaded (the frame was long). */
    boolean step() {
        watcher.drainChanges(changed::add);
        if (changed.isEmpty()) {
            return false;
        }
        Set<AssetManifest.Entry> entries = new LinkedHashSet<>();
        for (String path : changed) {
            if (path.equals(assets.manifest().source())) {
                LOG.warn(path + " changed; restart the game to apply manifest changes");
                continue;
            }
            List<AssetManifest.Entry> affected = assets.entriesForChange(path);
            if (affected.isEmpty()) {
                LOG.debug(path + " changed; no loaded asset uses it");
            }
            entries.addAll(affected);
        }
        changed.clear();
        if (entries.isEmpty()) {
            return false;
        }
        List<AssetReloaded> reloaded = assets.reload(entries);
        boolean restart = false;
        for (AssetReloaded event : reloaded) {
            events.queue(event);
            if (event.id().equals(inputBindings)) {
                applyBindings(event);
            }
            Scene scene = scenes.current();
            boolean handled = scene != null && scene.onAssetReloaded(event);
            restart |= !handled && event.type().equals(AssetManifest.Type.MAP.manifestName());
        }
        if (!reloaded.isEmpty() && scenes.isBroken()) {
            restart = true;   // a scene whose restart failed tries again after any fix
        }
        if (restart && scenes.canRestart()) {
            LOG.info("Restarting the current scene for the changed map");
            scenes.restart();
        }
        return !reloaded.isEmpty();
    }

    private void applyBindings(AssetReloaded event) {
        try {
            input.setBindings(InputBindings.parse(assets.get(event.id(), JsonValue.class), event.path()));
        } catch (RuntimeException e) {
            LOG.error("Hot reload of " + event.id() + ": invalid input bindings; keeping the old ones: "
                    + e.getMessage());
        }
    }
}
