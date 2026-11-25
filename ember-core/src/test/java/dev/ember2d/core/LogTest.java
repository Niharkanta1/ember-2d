package dev.ember2d.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogTest {

    private record Entry(LogLevel level, String category, String message, Throwable error) {
    }

    private static final class RecordingSink implements LogSink {
        final List<Entry> entries = new ArrayList<>();

        @Override
        public void write(LogLevel level, String category, String message, Throwable error) {
            entries.add(new Entry(level, category, message, error));
        }
    }

    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        sink = new RecordingSink();
        LogConfig.addSink(sink);
    }

    @AfterEach
    void tearDown() {
        LogConfig.resetToDefaults();
    }

    @Test
    void defaultLevelIsInfo() {
        Log log = Log.get("test");
        log.debug("hidden");
        log.info("shown");
        log.warn("shown too");

        assertThat(sink.entries).extracting(Entry::message).containsExactly("shown", "shown too");
        assertThat(sink.entries).extracting(Entry::level).containsExactly(LogLevel.INFO, LogLevel.WARN);
    }

    @Test
    void categoryLevelOverridesDefaultLevel() {
        LogConfig.setDefaultLevel(LogLevel.ERROR);
        LogConfig.setLevel("assets", LogLevel.DEBUG);

        Log.get("assets").debug("asset detail");
        Log.get("scene").warn("scene warning");
        Log.get("scene").error("scene error");

        assertThat(sink.entries).extracting(Entry::message).containsExactly("asset detail", "scene error");
    }

    @Test
    void clearLevelFallsBackToDefault() {
        LogConfig.setLevel("assets", LogLevel.OFF);
        LogConfig.clearLevel("assets");

        Log.get("assets").info("back");

        assertThat(sink.entries).extracting(Entry::message).containsExactly("back");
    }

    @Test
    void offDisablesEverythingAndOffIsNeverEnabled() {
        LogConfig.setLevel("quiet", LogLevel.OFF);
        Log quiet = Log.get("quiet");
        quiet.error("dropped");

        assertThat(sink.entries).isEmpty();
        assertThat(quiet.isEnabled(LogLevel.ERROR)).isFalse();
        LogConfig.setDefaultLevel(LogLevel.DEBUG);
        assertThat(Log.get("loud").isEnabled(LogLevel.OFF)).isFalse();
    }

    @Test
    void sameCategoryReturnsSameLogger() {
        assertThat(Log.get("same")).isSameAs(Log.get("same"));
        assertThat(Log.get("same").category()).isEqualTo("same");
    }

    @Test
    void errorCarriesThrowableAndCategory() {
        IllegalStateException failure = new IllegalStateException("boom");
        Log.get("core").error("failed", failure);

        assertThat(sink.entries).singleElement().satisfies(entry -> {
            assertThat(entry.category()).isEqualTo("core");
            assertThat(entry.error()).isSameAs(failure);
        });
    }

    @Test
    void failingSinkDoesNotStopOtherSinks() {
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink((level, category, message, error) -> {
            throw new IllegalStateException("broken sink");
        });
        LogConfig.addSink(sink);
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            Log.get("test").info("still delivered");
        } finally {
            System.setErr(originalErr);
        }

        assertThat(sink.entries).extracting(Entry::message).containsExactly("still delivered");
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("broken sink");
    }

    @Test
    void consoleSinkFallsBackToStandardErrorWithoutLibgdx() {
        LogConfig.resetToDefaults();
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            Log.get("boot").warn("no app yet");
        } finally {
            System.setErr(originalErr);
        }

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("WARN [boot] no app yet");
    }
}
