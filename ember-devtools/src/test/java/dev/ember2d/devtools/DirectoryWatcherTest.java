package dev.ember2d.devtools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** The hot reload watcher (F-008, I-150) on a real temporary folder. */
class DirectoryWatcherTest {

    @TempDir
    Path dir;

    /** Drains until something arrives or the time is up. */
    private static List<String> await(DirectoryWatcher watcher, long millis) throws InterruptedException {
        List<String> got = new ArrayList<>();
        long end = System.currentTimeMillis() + millis;
        while (got.isEmpty() && System.currentTimeMillis() < end) {
            watcher.drainChanges(got::add);
            Thread.sleep(20);
        }
        return got;
    }

    @Test
    @Timeout(20)
    void aChangeInASubfolderArrivesOnceAfterTheQuietPeriod() throws IOException, InterruptedException {
        Files.createDirectories(dir.resolve("maps"));
        Files.writeString(dir.resolve("maps/level.tmx"), "a");
        try (DirectoryWatcher watcher = new DirectoryWatcher(dir, 100)) {
            Files.writeString(dir.resolve("maps/level.tmx"), "b");

            List<String> got = await(watcher, 10_000);
            assertThat(got).containsExactly("maps/level.tmx");
            Thread.sleep(300);
            List<String> again = new ArrayList<>();
            watcher.drainChanges(again::add);
            assertThat(again).as("reported once").isEmpty();
        }
    }

    @Test
    @Timeout(20)
    void quickWritesArriveAsOneBatchAndNothingBeforeTheQuietPeriod() throws IOException, InterruptedException {
        try (DirectoryWatcher watcher = new DirectoryWatcher(dir, 500)) {
            for (int i = 0; i < 5; i++) {
                Files.writeString(dir.resolve("data.json"), "{ \"i\": " + i + " }");
                Files.writeString(dir.resolve("other.json"), "{}");
                Thread.sleep(30);
            }
            List<String> early = new ArrayList<>();
            watcher.drainChanges(early::add);
            assertThat(early).as("still inside the quiet period").isEmpty();

            List<String> got = await(watcher, 10_000);
            assertThat(got).containsExactlyInAnyOrder("data.json", "other.json");
        }
    }

    @Test
    @Timeout(20)
    void aNewSubfolderIsWatchedToo() throws IOException, InterruptedException {
        try (DirectoryWatcher watcher = new DirectoryWatcher(dir, 100)) {
            Files.createDirectories(dir.resolve("new"));
            Thread.sleep(300);   // let the watcher register the folder
            watcher.drainChanges(p -> { });
            Files.writeString(dir.resolve("new/file.json"), "{}");

            assertThat(await(watcher, 10_000)).contains("new/file.json");
        }
    }

    @Test
    void closeStopsTheThreadAndBadArgumentsFail() throws InterruptedException {
        DirectoryWatcher watcher = new DirectoryWatcher(dir);
        assertThat(watcher.isRunning()).isTrue();
        assertThat(watcher.root()).isEqualTo(dir.toAbsolutePath().normalize());
        watcher.close();
        watcher.close();   // twice is fine
        assertThat(watcher.isRunning()).isFalse();

        assertThatThrownBy(() -> new DirectoryWatcher(dir.resolve("missing")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is not a folder");
        assertThatThrownBy(() -> new DirectoryWatcher(dir, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
