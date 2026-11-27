package dev.ember2d.demo.platformer;

import dev.ember2d.app.EngineConfig;

/**
 * Platformer demo setup, shared by the desktop and Android launchers. Games configure Ember2D rather than
 * subclass its loop: the launcher passes this config to {@code new EmberGame(config)}.
 */
public final class PlatformerDemo {

    private PlatformerDemo() {
    }

    private static String version = "dev";

    /** The demo's engine configuration, with the version "dev". */
    public static EngineConfig config(boolean devMode) {
        return config(devMode, "dev");
    }

    /**
     * The demo's engine configuration.
     *
     * @param version the game version shown in the main menu (the jar manifest, or the Android version name)
     */
    public static EngineConfig config(boolean devMode, String version) {
        PlatformerDemo.version = version == null || version.isBlank() ? "dev" : version;
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 320;
        config.virtualHeight = 180;
        config.backgroundColor.set(0.08f, 0.06f, 0.10f, 1f);
        config.firstScene = MenuScene::new;   // main menu, then the levels (I-220, I-250)
        config.inputBindings = "input";
        config.saveFolder = "demo-platformer";   // settings and best times survive restarts (I-230)
        config.components = new DemoComponents();
        config.tiledProject = "maps/platformer.tiled-project";   // class-typed properties (I-245)
        config.devMode = devMode;
        return config;
    }

    /** The version given to {@link #config(boolean, String)}. */
    static String version() {
        return version;
    }
}
