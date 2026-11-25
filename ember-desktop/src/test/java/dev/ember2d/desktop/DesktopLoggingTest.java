package dev.ember2d.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ember2d.core.Log;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopLoggingTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        DesktopLogging.shutdown();
        LogConfig.resetToDefaults();
    }

    @Test
    void devModeLogsDebugToFile() throws IOException {
        DesktopLogging.configure(true, "test-game", tempDir);
        Log.get("assets").debug("loaded manifest");
        Log.get("assets").error("broken", new IllegalStateException("bad file"));
        DesktopLogging.shutdown();

        String content = Files.readString(tempDir.resolve("test-game-dev.log"), StandardCharsets.UTF_8);
        assertThat(LogConfig.defaultLevel()).isEqualTo(LogLevel.DEBUG);
        assertThat(content)
                .containsPattern("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} DEBUG \\[assets] loaded manifest")
                .contains("ERROR [assets] broken")
                .contains("java.lang.IllegalStateException: bad file");
    }

    @Test
    void releaseModeLogsInfoAndWritesNoFile() throws IOException {
        DesktopLogging.configure(false, "test-game", tempDir);
        Log.get("assets").info("release");

        assertThat(LogConfig.defaultLevel()).isEqualTo(LogLevel.INFO);
        try (var files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void fileSinkAppendsAcrossOpens() throws IOException {
        Path file = tempDir.resolve("nested/dir/app.log");
        try (FileLogSink first = FileLogSink.open(file)) {
            first.write(LogLevel.INFO, "a", "one", null);
        }
        try (FileLogSink second = FileLogSink.open(file)) {
            second.write(LogLevel.WARN, "b", "two", null);
        }

        assertThat(Files.readAllLines(file, StandardCharsets.UTF_8))
                .hasSize(2)
                .anySatisfy(line -> assertThat(line).endsWith("INFO  [a] one"))
                .anySatisfy(line -> assertThat(line).endsWith("WARN  [b] two"));
    }
}
