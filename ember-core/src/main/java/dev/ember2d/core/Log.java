package dev.ember2d.core;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Logger for one category (for example {@code "assets"} or {@code "scene"}). Obtain it once and keep it in
 * a field: {@code private static final Log LOG = Log.get("assets");}. Levels and sinks are configured in
 * {@link LogConfig}. In per-frame code, guard costly messages with {@link #isEnabled(LogLevel)}.
 */
@PublicApi
public final class Log {

    private static final Map<String, Log> LOGS = new ConcurrentHashMap<>();

    private final String category;

    private Log(String category) {
        this.category = category;
    }

    /** Returns the logger for a category; the same instance for the same name. */
    public static Log get(String category) {
        Objects.requireNonNull(category, "category");
        return LOGS.computeIfAbsent(category, Log::new);
    }

    public String category() {
        return category;
    }

    /** Whether a message at this level would be written for this category. */
    public boolean isEnabled(LogLevel level) {
        return level != LogLevel.OFF && level.ordinal() <= LogConfig.levelOf(category).ordinal();
    }

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable error) {
        log(LogLevel.ERROR, message, error);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void warn(String message, Throwable error) {
        log(LogLevel.WARN, message, error);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    private void log(LogLevel level, String message, Throwable error) {
        if (isEnabled(level)) {
            LogConfig.dispatch(level, category, message, error);
        }
    }
}
