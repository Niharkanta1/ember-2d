package dev.ember2d.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.utils.Disposable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Integration test on the libGDX headless backend: boot, run frames, exit, dispose (F-001). */
class EmberGameHeadlessTest {

    interface Probe {
    }

    @AfterEach
    void resetGlobals() {
        LogConfig.resetToDefaults();
        // The headless backend leaves its globals set after exit; clear them so other tests see no app.
        Gdx.app = null;
        Gdx.graphics = null;
        Gdx.audio = null;
        Gdx.files = null;
        Gdx.input = null;
        Gdx.net = null;
    }

    @Test
    @Timeout(20)
    void bootsRunsFramesAndShutsDownCleanly() throws InterruptedException {
        CountDownLatch disposed = new CountDownLatch(1);
        AtomicInteger framesSeen = new AtomicInteger();
        AtomicReference<Double> elapsedAtExit = new AtomicReference<>();
        final class DisposeProbe implements Probe, Disposable {
            @Override
            public void dispose() {
                disposed.countDown();
            }
        }
        EmberGame game = new EmberGame(new EngineConfig()) {
            @Override
            protected void configureContext(EngineContext.Builder builder) {
                builder.register(Probe.class, new DisposeProbe());
            }
        };
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = 120;

        new HeadlessApplication(game, config);
        // Each posted runnable runs on the loop thread before a frame; chain five, then exit.
        Gdx.app.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (framesSeen.incrementAndGet() < 5) {
                    Gdx.app.postRunnable(this);
                } else {
                    elapsedAtExit.set(game.context().time().unscaledElapsed());
                    Gdx.app.exit();
                }
            }
        });

        assertThat(disposed.await(15, TimeUnit.SECONDS)).as("disposed within 15 s").isTrue();
        assertThat(framesSeen.get()).isEqualTo(5);
        assertThat(game.state()).isEqualTo(AppState.DISPOSED);
        assertThat(elapsedAtExit.get()).as("the game loop advanced time").isGreaterThan(0.0);
    }
}
