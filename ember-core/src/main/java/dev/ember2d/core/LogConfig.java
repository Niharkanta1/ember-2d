package dev.ember2d.core;

import dev.ember2d.core.internal.GdxLogSink;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global logging configuration: the default level, per-category levels and the sinks messages go to.
 *
 * <p>Logging is the one deliberate exception to "no static service locator" (ARCHITECTURE.md): it is
 * needed before {@code EngineContext} exists and in code that never sees a context. Configure it once at
 * startup. By default, messages at {@link LogLevel#INFO} and above go to the libGDX application logger
 * (console on desktop, logcat on Android), or to standard error when no libGDX application is running.
 */
@PublicApi
public final class LogConfig {

    private static final LogSink CONSOLE = new GdxLogSink();
    private static final Map<String, LogLevel> CATEGORY_LEVELS = new ConcurrentHashMap<>();
    private static final List<LogSink> SINKS = new CopyOnWriteArrayList<>(List.of(CONSOLE));
    private static volatile LogLevel defaultLevel = LogLevel.INFO;

    private LogConfig() {
    }

    /** Sets the threshold for every category without its own level. */
    public static void setDefaultLevel(LogLevel level) {
        defaultLevel = Objects.requireNonNull(level, "level");
    }

    public static LogLevel defaultLevel() {
        return defaultLevel;
    }

    /** Sets the threshold for one category, overriding the default level. */
    public static void setLevel(String category, LogLevel level) {
        CATEGORY_LEVELS.put(Objects.requireNonNull(category, "category"), Objects.requireNonNull(level, "level"));
    }

    /** Removes a category's own level so it follows the default level again. */
    public static void clearLevel(String category) {
        CATEGORY_LEVELS.remove(category);
    }

    /** The threshold that applies to a category. */
    public static LogLevel levelOf(String category) {
        return CATEGORY_LEVELS.getOrDefault(category, defaultLevel);
    }

    public static void addSink(LogSink sink) {
        SINKS.add(Objects.requireNonNull(sink, "sink"));
    }

    public static void removeSink(LogSink sink) {
        SINKS.remove(sink);
    }

    /** The built-in sink that writes to the libGDX application logger. */
    public static LogSink consoleSink() {
        return CONSOLE;
    }

    /** Restores the defaults: level INFO, no category levels, only the console sink. Mainly for tests. */
    public static void resetToDefaults() {
        defaultLevel = LogLevel.INFO;
        CATEGORY_LEVELS.clear();
        SINKS.clear();
        SINKS.add(CONSOLE);
    }

    static void dispatch(LogLevel level, String category, String message, Throwable error) {
        for (LogSink sink : SINKS) {
            try {
                sink.write(level, category, message, error);
            } catch (RuntimeException sinkFailure) {
                System.err.println("Ember2D: log sink " + sink.getClass().getName() + " failed: " + sinkFailure);
            }
        }
    }
}
