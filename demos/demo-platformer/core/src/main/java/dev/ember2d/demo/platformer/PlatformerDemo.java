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
        config.devMode = devMode;
        return config;
    }
}
