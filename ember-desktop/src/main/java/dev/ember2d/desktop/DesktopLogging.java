package dev.ember2d.desktop;

import dev.ember2d.core.Log;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.PublicApi;
import java.nio.file.Path;

/**
 * Logging setup for desktop launchers.
 *
 * <p>Dev mode (system property {@code ember.devMode=true}, set by the Gradle {@code run} task) logs at
 * {@link LogLevel#DEBUG} and also writes {@code <logDirectory>/<gameName>-dev.log}. Release builds log at
 * {@link LogLevel#INFO} to the console only. Phase 0 stand-in until {@code EngineConfig.devMode} (I-010).
 */
@PublicApi
public final class DesktopLogging {

    /** System property that switches dev mode on. */
    public static final String DEV_MODE_PROPERTY = "ember.devMode";

    private static FileLogSink devFileSink;

    private DesktopLogging() {
    }

    /** Whether the JVM was started with {@code -Dember.devMode=true}. */
    public static boolean devModeFromSystemProperty() {
        return Boolean.getBoolean(DEV_MODE_PROPERTY);
    }

    /** Configures levels and, in dev mode, the dev log file in {@code logs/} under the working directory. */
    public static void configure(boolean devMode, String gameName) {
        configure(devMode, gameName, Path.of("logs"));
    }

    /** Configures levels and, in dev mode, the dev log file in the given directory. */
    public static synchronized void configure(boolean devMode, String gameName, Path logDirectory) {
        shutdown();
        LogConfig.setDefaultLevel(devMode ? LogLevel.DEBUG : LogLevel.INFO);
        if (devMode) {
            devFileSink = FileLogSink.open(logDirectory.resolve(gameName + "-dev.log"));
            LogConfig.addSink(devFileSink);
            Log.get("ember").debug("Dev log file: " + devFileSink.file().toAbsolutePath());
        }
    }

    /** Detaches and closes the dev log file, if any. Call after the application has exited. */
    public static synchronized void shutdown() {
        if (devFileSink != null) {
            LogConfig.removeSink(devFileSink);
            devFileSink.close();
            devFileSink = null;
        }
    }
}
