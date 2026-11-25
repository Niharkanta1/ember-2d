package dev.ember2d.core;

/** Severity of a log message, from most to least severe. {@link #OFF} as a threshold disables a category. */
@PublicApi
public enum LogLevel {
    OFF, ERROR, WARN, INFO, DEBUG
}
