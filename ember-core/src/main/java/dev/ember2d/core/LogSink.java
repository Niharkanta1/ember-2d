package dev.ember2d.core;

/**
 * Destination for log messages (console, file, in-game console). Sinks are registered with
 * {@link LogConfig#addSink(LogSink)} and may be called from the asset-loading thread, so implementations
 * must be thread-safe. A sink that throws does not affect other sinks.
 */
@PublicApi
public interface LogSink {

    /**
     * Writes one message that passed the level filter.
     *
     * @param level never {@link LogLevel#OFF}
     * @param error optional, may be {@code null}
     */
    void write(LogLevel level, String category, String message, Throwable error);
}
