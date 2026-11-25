package dev.ember2d.desktop;

import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a crash log when a release build dies from an uncaught exception (F-020). The file goes to a
 * per-game folder in the user's application data directory, so players can send it in.
 */
@PublicApi
public final class CrashLog {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CrashLog() {
    }

    /** Installs a default uncaught-exception handler that writes a crash log, then defers to the previous one. */
    public static void install(String gameName, String version) {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                Path file = write(error, appDataDirectory(gameName), version);
                Log.get("crash").error("Crash log written to " + file.toAbsolutePath(), error);
            } catch (RuntimeException writeFailure) {
                error.addSuppressed(writeFailure);
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                System.err.print("Exception in thread \"" + thread.getName() + "\" ");
                error.printStackTrace();
            }
        });
    }

    /** Writes {@code crash-<timestamp>.log} with version, OS, Java version and stack trace; returns the file. */
    public static Path write(Throwable error, Path directory, String version) {
        StringWriter stackTrace = new StringWriter();
        error.printStackTrace(new PrintWriter(stackTrace));
        String content = "Version: " + version + System.lineSeparator()
                + "Time: " + LocalDateTime.now() + System.lineSeparator()
                + "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")" + System.lineSeparator()
                + "Java: " + System.getProperty("java.vendor") + " " + System.getProperty("java.version")
                + System.lineSeparator() + System.lineSeparator()
                + stackTrace;
        try {
            Files.createDirectories(directory);
            Path file = uniqueFile(directory, "crash-" + LocalDateTime.now().format(FILE_TIME));
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write crash log to " + directory.toAbsolutePath(), e);
        }
    }

    /** The per-game application data folder for the current OS and user. */
    public static Path appDataDirectory(String gameName) {
        return appDataDirectory(gameName, System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")));
    }

    static Path appDataDirectory(String gameName, String osName, Map<String, String> env, Path home) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = env.get("APPDATA");
            Path base = appData != null && !appData.isBlank() ? Path.of(appData) : home.resolve("AppData/Roaming");
            return base.resolve(gameName);
        }
        if (os.contains("mac")) {
            return home.resolve("Library/Application Support").resolve(gameName);
        }
        String dataHome = env.get("XDG_DATA_HOME");
        Path base = dataHome != null && !dataHome.isBlank() ? Path.of(dataHome) : home.resolve(".local/share");
        return base.resolve(gameName);
    }

    private static Path uniqueFile(Path directory, String baseName) {
        Path file = directory.resolve(baseName + ".log");
        for (int i = 2; Files.exists(file); i++) {
            file = directory.resolve(baseName + "-" + i + ".log");
        }
        return file;
    }
}
