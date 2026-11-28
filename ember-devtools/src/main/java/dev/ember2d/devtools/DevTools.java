package dev.ember2d.devtools;

import com.badlogic.gdx.ApplicationListener;
import dev.ember2d.app.EmberGame;
import dev.ember2d.assets.AssetWatcher;
import dev.ember2d.core.PublicApi;
import dev.ember2d.devtools.internal.DevToolsApp;
import dev.ember2d.devtools.internal.ManualReloadWatcher;

/**
 * In-game developer tools for desktop dev builds (F-018, I-160): Dear ImGui panels over the running game, toggled
 * with F1 (F12 saves a screenshot): entities, inspector with live editing, animation state, time controls, frame times, debug draw, event log,
 * assets with reload and hot reload status. Never part of release or Android builds.
 *
 * <pre>{@code
 * config.assetsDir = dir;
 * config.assetWatcher = DevTools.watch(new DirectoryWatcher(Path.of(dir)));   // optional: Reload buttons
 * new Lwjgl3Application(DevTools.wrap(new EmberGame(config)), windowConfig);
 * }</pre>
 */
@PublicApi
public final class DevTools {

    private DevTools() {
    }

    /** The game with the dev tools drawn after each of its frames, in the same window (LWJGL3 backend only). */
    public static ApplicationListener wrap(EmberGame game) {
        return new DevToolsApp(game);
    }

    /** A watcher that also delivers the Assets panel's "Reload" requests; set it as {@code EngineConfig.assetWatcher}. */
    public static AssetWatcher watch(AssetWatcher files) {
        return new ManualReloadWatcher(files);
    }
}
