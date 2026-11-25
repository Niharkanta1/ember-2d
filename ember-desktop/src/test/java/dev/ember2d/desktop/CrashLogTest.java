package dev.ember2d.desktop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrashLogTest {

    @TempDir
    Path tempDir;

    @Test
    void writesVersionEnvironmentAndStackTrace() throws IOException {
        Path file = CrashLog.write(new IllegalStateException("render failed"), tempDir.resolve("game"), "1.2.3");

        assertThat(file.getFileName().toString()).matches("crash-\\d{8}-\\d{6}\\.log");
        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .contains("Version: 1.2.3")
                .contains("OS: ")
                .contains("Java: ")
                .contains("java.lang.IllegalStateException: render failed")
                .contains("at dev.ember2d.desktop.CrashLogTest");
    }

    @Test
    void secondCrashInSameSecondGetsItsOwnFile() {
        Path first = CrashLog.write(new RuntimeException("one"), tempDir, "1");
        Path second = CrashLog.write(new RuntimeException("two"), tempDir, "1");

        assertThat(second).isNotEqualTo(first);
        assertThat(second).exists();
    }

    @Test
    void appDataDirectoryPerOperatingSystem() {
        Path home = Path.of("/home/player");

        assertThat(CrashLog.appDataDirectory("game", "Windows 11", Map.of("APPDATA", "C:/Users/p/AppData/Roaming"), home))
                .isEqualTo(Path.of("C:/Users/p/AppData/Roaming", "game"));
        assertThat(CrashLog.appDataDirectory("game", "Windows 11", Map.of(), home))
                .isEqualTo(home.resolve("AppData/Roaming/game"));
        assertThat(CrashLog.appDataDirectory("game", "Mac OS X", Map.of(), home))
                .isEqualTo(home.resolve("Library/Application Support/game"));
        assertThat(CrashLog.appDataDirectory("game", "Linux", Map.of("XDG_DATA_HOME", "/data"), home))
                .isEqualTo(Path.of("/data", "game"));
        assertThat(CrashLog.appDataDirectory("game", "Linux", Map.of(), home))
                .isEqualTo(home.resolve(".local/share/game"));
    }
}
