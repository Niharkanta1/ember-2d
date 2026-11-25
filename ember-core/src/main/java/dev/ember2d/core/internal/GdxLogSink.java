package dev.ember2d.core.internal;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import java.io.PrintStream;

/**
 * Writes to the libGDX application logger (console on desktop, logcat on Android). Ember2D does the level
 * filtering, so the libGDX log level is opened up to debug. Falls back to standard error when no libGDX
 * application is running (unit tests, early startup).
 */
public final class GdxLogSink implements LogSink {

    @Override
    public void write(LogLevel level, String category, String message, Throwable error) {
        Application app = Gdx.app;
        if (app == null) {
            PrintStream out = System.err;
            out.println(level + " [" + category + "] " + message);
            if (error != null) {
                error.printStackTrace(out);
            }
            return;
        }
        if (app.getLogLevel() != Application.LOG_DEBUG) {
            app.setLogLevel(Application.LOG_DEBUG);
        }
        switch (level) {
            case ERROR -> {
                if (error == null) {
                    app.error(category, message);
                } else {
                    app.error(category, message, error);
                }
            }
            case WARN -> {
                if (error == null) {
                    app.log(category, "[WARN] " + message);
                } else {
                    app.log(category, "[WARN] " + message, error);
                }
            }
            case INFO -> app.log(category, message);
            case DEBUG -> app.debug(category, message);
            case OFF -> {
                // never dispatched
            }
        }
    }
}
