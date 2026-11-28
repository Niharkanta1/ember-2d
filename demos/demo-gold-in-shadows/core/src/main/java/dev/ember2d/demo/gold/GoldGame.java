package dev.ember2d.demo.gold;

import dev.ember2d.app.EngineConfig;

/** Gold in Shadows: the engine configuration, shared by the desktop and Android launchers. */
public final class GoldGame {

    private static String version = "dev";

    private GoldGame() {
    }

    /** The game's engine configuration, with the version "dev". */
    public static EngineConfig config(boolean devMode) {
        return config(devMode, "dev");
    }

    /** The game's engine configuration; {@code version} comes from the launcher. */
    public static EngineConfig config(boolean devMode, String version) {
        GoldGame.version = version == null || version.isBlank() ? "dev" : version;
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 160;    // the original's resolution, integer-scaled
        config.virtualHeight = 144;
        config.backgroundColor.set(0x18 / 255f, 0x18 / 255f, 0x30 / 255f, 1f);   // the palette's DARK
        config.firstScene = MainScene::new;
        config.inputBindings = "input";
        config.saveFolder = "demo-gold-in-shadows";
        config.components = new GoldComponents();
        config.devMode = devMode;
        return config;
    }

    static String version() {
        return version;
    }
}
