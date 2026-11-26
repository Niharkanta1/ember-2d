package dev.ember2d.demo.platformer.desktop;

import dev.ember2d.devtools.DirectoryWatcher;
import java.nio.file.Path;

/**
 * Dev launcher ({@code gradlew run}, I-150): the desktop launcher plus hot reload. Reads the assets from the folder in
 * the {@code ember.assetsDir} system property (the demo's source assets, set by the Gradle {@code run} task) and
 * watches it, so saved maps, JSON, images and fonts show up in the running game. Not part of release builds.
 */
public final class DevLauncher {

    private DevLauncher() {
    }

    public static void main(String[] args) {
        DesktopLauncher.launch(args, config -> {
            String dir = System.getProperty("ember.assetsDir");
            if (dir != null && !dir.isBlank()) {
                config.assetsDir = dir;
                config.assetWatcher = new DirectoryWatcher(Path.of(dir));
            }
        });
    }
}
