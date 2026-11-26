package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import dev.ember2d.core.Time;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lifecycle tests without a libGDX backend (render() is covered by EmberGameHeadlessTest). */
class EmberGameTest {

    /** Records lifecycle calls and disposal into a shared journal. */
    static class RecordingService implements AppLifecycleListener, Disposable {
        final String name;
        final List<String> journal;

        RecordingService(String name, List<String> journal) {
            this.name = name;
            this.journal = journal;
        }

        @Override
        public void onSuspend() {
            journal.add(name + ".suspend");
        }

        @Override
        public void onResume() {
            journal.add(name + ".resume");
        }

        @Override
        public void onResize(int width, int height) {
            journal.add(name + ".resize " + width + "x" + height);
        }

        @Override
        public void dispose() {
            journal.add(name + ".dispose");
        }
    }

    interface Audio {
    }

    interface Saves {
    }

    static class FakeAudio extends RecordingService implements Audio {
        FakeAudio(List<String> journal) {
            super("audio", journal);
        }
    }

    static class FakeSaves extends RecordingService implements Saves {
        FakeSaves(List<String> journal) {
            super("saves", journal);
        }
    }

    private final List<String> journal = new ArrayList<>();
    private final List<String> logLines = new ArrayList<>();
    private final LogSink logSink = (level, category, message, error) -> logLines.add(level + " " + message);

    @BeforeEach
    void captureLog() {
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.setDefaultLevel(LogLevel.DEBUG);
        LogConfig.addSink(logSink);
    }

    @AfterEach
    void resetLog() {
        LogConfig.resetToDefaults();
    }

    /** A game with an empty manifest, so no libGDX file access is needed. */
    static EmberGame testGame(EngineConfig config) {
        return new EmberGame(config, c -> new GdxAssets(AssetManifest.empty("test"), new InternalFileHandleResolver()));
    }

    private EmberGame gameWith(RecordingService... services) {
        EngineConfig config = new EngineConfig();
        return new EmberGame(config, c -> new GdxAssets(AssetManifest.empty("test"), new InternalFileHandleResolver())) {
            @Override
            protected void configureContext(EngineContext.Builder builder) {
                for (RecordingService service : services) {
                    if (service instanceof Audio audio) {
                        builder.register(Audio.class, audio);
                    } else if (service instanceof Saves saves) {
                        builder.register(Saves.class, saves);
                    }
                }
            }
        };
    }

    @Test
    void createBuildsContextWithGameServicesAndRuns() {
        FakeAudio audio = new FakeAudio(journal);
        EmberGame game = gameWith(audio);

        assertThat(game.state()).isEqualTo(AppState.CREATED);
        game.create();

        assertThat(game.state()).isEqualTo(AppState.RUNNING);
        assertThat(game.context().service(Audio.class)).isSameAs(audio);
        assertThat(game.context().config()).isNotNull();
    }

    @Test
    void contextExposesTimeWithConfiguredFixedStep() {
        EngineConfig config = new EngineConfig();
        config.fixedStep = 1f / 30f;
        EmberGame game = testGame(config);
        game.create();

        Time time = game.context().time();
        assertThat(time.fixedDelta()).isEqualTo(1f / 30f);
        assertThat(time.fixedTick()).isZero();
        assertThat(time.paused()).isFalse();
        assertThat(time.timeScale()).isEqualTo(1f);
    }

    @Test
    void contextIsUnavailableBeforeCreate() {
        assertThatThrownBy(() -> gameWith().context())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("after create()");
    }

    @Test
    void suspendAndResumeReachServicesInRegistrationOrder() {
        EmberGame game = gameWith(new FakeAudio(journal), new FakeSaves(journal));
        game.create();

        game.resize(800, 600);
        game.pause();
        assertThat(game.state()).isEqualTo(AppState.SUSPENDED);
        game.resume();
        assertThat(game.state()).isEqualTo(AppState.RUNNING);

        assertThat(journal).containsExactly(
                "audio.resize 800x600", "saves.resize 800x600",
                "audio.suspend", "saves.suspend",
                "audio.resume", "saves.resume");
    }

    @Test
    void suspendAndResumeArePublishedAsImmediateEvents() {
        EmberGame game = gameWith(new FakeAudio(journal));
        game.create();
        game.context().events().onGlobal(dev.ember2d.event.AppSuspended.class, e -> journal.add("event suspended"));
        game.context().events().onGlobal(dev.ember2d.event.AppResumed.class, e -> journal.add("event resumed"));

        game.pause();
        game.resume();

        assertThat(journal).containsExactly("audio.suspend", "event suspended", "audio.resume", "event resumed");
    }

    @Test
    void repeatedSuspendOrResumeIsIgnored() {
        EmberGame game = gameWith(new FakeAudio(journal));
        game.create();

        game.resume();
        game.pause();
        game.pause();
        game.resume();
        game.resume();

        assertThat(journal).containsExactly("audio.suspend", "audio.resume");
        assertThat(logLines).contains("DEBUG pause() while already suspended; ignored",
                "DEBUG resume() while already running; ignored");
    }

    @Test
    void disposeReleasesServicesInReverseOrderAndIsIdempotent() {
        EmberGame game = gameWith(new FakeAudio(journal), new FakeSaves(journal));
        game.create();

        game.dispose();
        game.dispose();

        assertThat(game.state()).isEqualTo(AppState.DISPOSED);
        assertThat(journal).containsExactly("saves.dispose", "audio.dispose");
    }

    @Test
    void disposeWhileSuspendedIsAllowed() {
        EmberGame game = gameWith(new FakeAudio(journal));
        game.create();
        game.pause();

        game.dispose();

        assertThat(journal).containsExactly("audio.suspend", "audio.dispose");
    }

    @Test
    void disposalFailureIsLoggedAndShutdownContinues() {
        FakeAudio audio = new FakeAudio(journal);
        RecordingService broken = new FakeSaves(journal) {
            @Override
            public void dispose() {
                throw new IllegalStateException("saves broke");
            }
        };
        EmberGame game = gameWith(audio, broken);
        game.create();

        game.dispose();

        assertThat(journal).containsExactly("audio.dispose");
        assertThat(logLines).anyMatch(line -> line.startsWith("ERROR Disposing") && line.contains("continuing"));
        assertThat(logLines).contains("INFO Shut down with 1 disposal failure(s)");
    }

    @Test
    void failingLifecycleListenerDoesNotStopOthers() {
        RecordingService broken = new FakeAudio(journal) {
            @Override
            public void onSuspend() {
                throw new IllegalStateException("audio broke");
            }
        };
        EmberGame game = gameWith(broken, new FakeSaves(journal));
        game.create();

        game.pause();

        assertThat(journal).containsExactly("saves.suspend");
        assertThat(logLines).anyMatch(line -> line.startsWith("ERROR onSuspend failed"));
    }

    @Test
    void invalidTransitionsAreRejected() {
        EmberGame fresh = gameWith();
        assertThatThrownBy(fresh::pause).isInstanceOf(IllegalStateException.class).hasMessageContaining("CREATED");
        assertThatThrownBy(fresh::resume).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(fresh::render).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fresh.resize(1, 1)).isInstanceOf(IllegalStateException.class);

        EmberGame running = gameWith();
        running.create();
        assertThatThrownBy(running::create).isInstanceOf(IllegalStateException.class).hasMessageContaining("RUNNING");

        running.dispose();
        assertThatThrownBy(running::resume).isInstanceOf(IllegalStateException.class).hasMessageContaining("DISPOSED");
        assertThatThrownBy(running::render).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidConfigFailsCreateAndLeavesGameUnstarted() {
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 0;
        EmberGame game = testGame(config);

        assertThatThrownBy(game::create).isInstanceOf(EngineConfigException.class).hasMessageContaining("virtualWidth");
        assertThat(game.state()).isEqualTo(AppState.CREATED);
        game.dispose();
        assertThat(game.state()).isEqualTo(AppState.DISPOSED);
    }

    @Test
    void bootTimeIsLoggedInDevModeOnly() {
        EngineConfig dev = new EngineConfig();
        dev.devMode = true;
        testGame(dev).create();
        assertThat(logLines).anyMatch(line -> line.matches("INFO Boot took \\d+ ms"));

        logLines.clear();
        testGame(new EngineConfig()).create();
        assertThat(logLines).noneMatch(line -> line.startsWith("INFO Boot took"));
    }

    @Test
    void theEngineBuildsAnAudioServiceOverTheAssets() {
        EmberGame game = testGame(new EngineConfig());
        game.create();
        assertThat(game.context().audio()).isInstanceOf(dev.ember2d.audio.internal.GdxAudio.class);
        game.pause();    // suspend and resume reach the audio service without failing
        game.resume();
        game.dispose();
    }
}
