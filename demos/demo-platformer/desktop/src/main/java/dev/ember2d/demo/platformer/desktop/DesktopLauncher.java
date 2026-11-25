package dev.ember2d.demo.platformer.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.ember2d.demo.platformer.PlatformerDemo;
import dev.ember2d.desktop.StartupHelper;

/** Launches the platformer demo on desktop (LWJGL3). */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return; // macOS needs -XstartOnFirstThread; also works around non-ASCII user paths on Windows.
        }
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Ember2D - Platformer Demo");
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1280, 720);
        new Lwjgl3Application(new PlatformerDemo(), configuration);
    }
}
