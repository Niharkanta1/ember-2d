package dev.ember2d.demo.novel;

import dev.ember2d.app.EngineConfig;

/**
 * Novel demo setup (I-470), shared by the desktop and Android launchers: one chapter of a visual novel with two
 * characters and a choice that changes the ending, built on kit-novel through its public API only.
 */
public final class NovelDemo {

    private static String version = "dev";

    private NovelDemo() {
    }

    /** The demo's engine configuration, with the version "dev". */
    public static EngineConfig config(boolean devMode) {
        return config(devMode, "dev");
    }

    /** The demo's engine configuration; {@code version} is shown on the title screen. */
    public static EngineConfig config(boolean devMode, String version) {
        NovelDemo.version = version == null || version.isBlank() ? "dev" : version;
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 320;
        config.virtualHeight = 180;
        config.backgroundColor.set(0.08f, 0.06f, 0.1f, 1f);
        config.firstScene = TitleScene::new;
        config.inputBindings = "input";
        config.saveFolder = "demo-novel";
        config.components = new NovelComponents();
        config.devMode = devMode;
        return config;
    }

    static String version() {
        return version;
    }
}
