package dev.ember2d.demo.platformer.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.ember2d.app.EmberGame;
import dev.ember2d.app.EngineConfig;
import dev.ember2d.app.TouchControlsMode;
import dev.ember2d.core.Log;
import dev.ember2d.demo.platformer.PlatformerDemo;
import dev.ember2d.desktop.CrashLog;
import dev.ember2d.desktop.DesktopLogging;
import dev.ember2d.desktop.DesktopWindow;
import dev.ember2d.desktop.StartupHelper;
import java.util.List;

/** Launches the platformer demo on desktop (LWJGL3). */
public final class DesktopLauncher {

    private static final String GAME_NAME = "platformer-demo";

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return; // macOS needs -XstartOnFirstThread; also works around non-ASCII user paths on Windows.
        }
        boolean devMode = DesktopLogging.devModeFromSystemProperty();
        DesktopLogging.configure(devMode, GAME_NAME);
        String version = version();
        if (!devMode) {
            CrashLog.install(GAME_NAME, version);
        }
        Log.get("launcher").info("Ember2D Platformer Demo " + version + (devMode ? " (dev mode)" : ""));
        if (List.of(args).contains("--crash-test")) {
            throw new IllegalStateException("Crash test requested with --crash-test");
        }

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Ember2D - Platformer Demo " + version);
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1280, 720);
        try {
            EngineConfig config = PlatformerDemo.config(devMode);
            if (List.of(args).contains("--touch-controls")) {
                config.touchControls = TouchControlsMode.ALWAYS;   // try the on-screen controls with the mouse
            }
            EmberGame game = new EmberGame(config);
            configuration.setWindowListener(DesktopWindow.focusListener(game));
            new Lwjgl3Application(game, configuration);
        } finally {
            Log.get("launcher").info("Exited");
            DesktopLogging.shutdown();
        }
    }

    /** The Gradle version written into the jar manifest, or "dev" when running from compiled classes. */
    private static String version() {
        String version = DesktopLauncher.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
