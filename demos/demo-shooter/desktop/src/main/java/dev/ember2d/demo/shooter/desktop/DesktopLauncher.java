package dev.ember2d.demo.shooter.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.ember2d.app.EmberGame;
import dev.ember2d.app.EngineConfig;
import dev.ember2d.app.TouchControlsMode;
import dev.ember2d.core.Log;
import dev.ember2d.demo.shooter.ShooterDemo;
import dev.ember2d.desktop.CrashLog;
import dev.ember2d.desktop.DesktopLogging;
import dev.ember2d.desktop.DesktopWindow;
import dev.ember2d.desktop.StartupHelper;
import dev.ember2d.ui.Insets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Launches the shooter demo on desktop (LWJGL3). */
public final class DesktopLauncher {

    private static final String GAME_NAME = "shooter-demo";

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        launch(args, config -> { }, game -> game);
    }

    /**
     * Starts the game. In a dev run {@code devSetup} may change the config (the dev launcher adds hot reload) and
     * {@code devWrap} may wrap the game (the dev launcher adds the dev tools).
     */
    static void launch(String[] args, Consumer<EngineConfig> devSetup,
            Function<EmberGame, ApplicationListener> devWrap) {
        if (StartupHelper.startNewJvmIfRequired()) {
            return; // macOS needs -XstartOnFirstThread; also works around non-ASCII user paths on Windows.
        }
        boolean devMode = DesktopLogging.devModeFromSystemProperty();
        DesktopLogging.configure(devMode, GAME_NAME);
        String version = version();
        if (!devMode) {
            CrashLog.install(GAME_NAME, version);
        }
        Log.get("launcher").info("Ember2D Shooter Demo " + version + (devMode ? " (dev mode)" : ""));
        if (List.of(args).contains("--crash-test")) {
            throw new IllegalStateException("Crash test requested with --crash-test");
        }

        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Ember2D - Shooter Demo " + version);
        configuration.useVsync(true);
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        configuration.setWindowedMode(1280, 720);
        try {
            EngineConfig config = ShooterDemo.config(devMode, version);
            for (String arg : args) {
                if (devMode && arg.startsWith("--safe-insets=")) {
                    config.simulatedSafeInsets = insets(arg.substring("--safe-insets=".length()));   // I-225
                }
            }
            if (List.of(args).contains("--touch-controls")) {
                config.touchControls = TouchControlsMode.ALWAYS;   // try the on-screen controls with the mouse
            }
            if (devMode) {
                devSetup.accept(config);
            }
            EmberGame game = new EmberGame(config);
            configuration.setWindowListener(DesktopWindow.focusListener(game));
            new Lwjgl3Application(devMode ? devWrap.apply(game) : game, configuration);
        } finally {
            Log.get("launcher").info("Exited");
            DesktopLogging.shutdown();
        }
    }

    /** "L,T,R,B" in window pixels. */
    private static Insets insets(String text) {
        String[] p = text.split(",");
        if (p.length != 4) {
            throw new IllegalArgumentException("--safe-insets needs L,T,R,B in pixels (was " + text + ")");
        }
        return new Insets(Float.parseFloat(p[0].trim()), Float.parseFloat(p[1].trim()), Float.parseFloat(p[2].trim()),
                Float.parseFloat(p[3].trim()));
    }

    /** The Gradle version written into the jar manifest, or "dev" when running from compiled classes. */
    private static String version() {
        String version = DesktopLauncher.class.getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }
}
