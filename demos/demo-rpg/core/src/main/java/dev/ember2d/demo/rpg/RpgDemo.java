package dev.ember2d.demo.rpg;

import dev.ember2d.app.EngineConfig;

/**
 * RPG demo setup (I-450), shared by the desktop and Android launchers: a small town and a cellar with an Ink
 * conversation, a locked door, a chest that stays open, and a cutscene staged with kit-novel, built on kit-common,
 * kit-topdown and kit-novel through their public API only.
 */
public final class RpgDemo {

    private static String version = "dev";

    private RpgDemo() {
    }

    /** The demo's engine configuration, with the version "dev". */
    public static EngineConfig config(boolean devMode) {
        return config(devMode, "dev");
    }

    /** The demo's engine configuration; {@code version} is shown in the main menu. */
    public static EngineConfig config(boolean devMode, String version) {
        RpgDemo.version = version == null || version.isBlank() ? "dev" : version;
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 320;
        config.virtualHeight = 180;
        config.backgroundColor.set(0.06f, 0.05f, 0.07f, 1f);
        config.firstScene = MenuScene::new;
        config.inputBindings = "input";
        config.saveFolder = "demo-rpg";
        config.components = new RpgComponents();
        config.devMode = devMode;
        return config;
    }

    static String version() {
        return version;
    }
}
