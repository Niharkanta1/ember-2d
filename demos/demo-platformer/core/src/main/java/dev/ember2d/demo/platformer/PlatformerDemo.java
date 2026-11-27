package dev.ember2d.demo.platformer;

import dev.ember2d.app.EngineConfig;

/**
 * Platformer demo setup, shared by the desktop and Android launchers. Games configure Ember2D rather than
 * subclass its loop: the launcher passes this config to {@code new EmberGame(config)}.
 */
public final class PlatformerDemo {

    private PlatformerDemo() {
    }

    /** The demo's engine configuration. */
    public static EngineConfig config(boolean devMode) {
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 320;
        config.virtualHeight = 180;
        config.backgroundColor.set(0.08f, 0.06f, 0.10f, 1f);
        config.firstScene = MenuScene::new;   // main menu, then level 1 (I-220)
        config.inputBindings = "input";
        config.saveFolder = "demo-platformer";   // settings and best times survive restarts (I-230)
        config.components = new DemoComponents();
        config.tiledProject = "maps/platformer.tiled-project";   // class-typed properties (I-245)
        config.devMode = devMode;
        return config;
    }
}
