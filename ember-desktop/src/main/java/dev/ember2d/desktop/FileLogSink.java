package dev.ember2d.desktop;

import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import dev.ember2d.core.PublicApi;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Appends timestamped log lines to a file. Used for the desktop dev log; flushes every line so the file is
 * complete even after a crash. Thread-safe.
 */
@PublicApi
public final class FileLogSink implements LogSink, Closeable {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Path file;
    private final PrintWriter out;

    private FileLogSink(Path file, BufferedWriter writer) {
        this.file = file;
        this.out = new PrintWriter(writer);
    }

    /** Opens (creating folders as needed) and appends to the given file. */
    public static FileLogSink open(Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return new FileLogSink(file, Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot open log file " + file.toAbsolutePath(), e);
        }
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized void write(LogLevel level, String category, String message, Throwable error) {
        out.printf("%s %-5s [%s] %s%n", LocalDateTime.now().format(TIMESTAMP), level, category, message);
        if (error != null) {
            error.printStackTrace(out);
        }
        out.flush();
    }

    @Override
    public synchronized void close() {
        out.close();
    }
}
