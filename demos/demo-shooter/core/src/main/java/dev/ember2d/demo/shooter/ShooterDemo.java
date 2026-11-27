package dev.ember2d.demo.shooter;

import dev.ember2d.app.EngineConfig;

/**
 * Shooter demo setup (I-340), shared by the desktop and Android launchers: a twin-stick dungeon shooter with three
 * waves and a bullet-hell boss, built on kit-common, kit-bullets and kit-topdown through their public API only.
 */
public final class ShooterDemo {

    private static String version = "dev";

    private ShooterDemo() {
    }

    /** The demo's engine configuration, with the version "dev". */
    public static EngineConfig config(boolean devMode) {
        return config(devMode, "dev");
    }

    /** The demo's engine configuration; {@code version} is shown in the main menu. */
    public static EngineConfig config(boolean devMode, String version) {
        ShooterDemo.version = version == null || version.isBlank() ? "dev" : version;
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 320;
        config.virtualHeight = 180;
        config.backgroundColor.set(0.05f, 0.04f, 0.07f, 1f);
        config.firstScene = MenuScene::new;
        config.inputBindings = "input";
        config.saveFolder = "demo-shooter";   // the best score survives restarts
        config.components = new ShooterComponents();
        config.devMode = devMode;
        return config;
    }

    static String version() {
        return version;
    }
}
