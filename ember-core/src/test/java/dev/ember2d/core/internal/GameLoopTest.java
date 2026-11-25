package dev.ember2d.core.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import dev.ember2d.core.Phase;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameLoopTest {

    private static final float FIXED = 1f / 60f;
    /** Exactly the engine's fixed step for 1/60 s, in nanoseconds. */
    private static final long FIXED_NANOS = Math.round(FIXED * 1_000_000_000d);

    /** Fake clock: returns whatever the test sets for the next frame. */
    private static final class FakeClock implements Clock {
        long nextDelta;

        @Override
        public long frameDeltaNanos() {
            return nextDelta;
        }
    }

    private final FakeClock clock = new FakeClock();
    private final TimeState time = new TimeState(FIXED);
    private final List<Phase> phases = new ArrayList<>();
    private final GameLoop loop = new GameLoop(clock, time, phases::add, 5);
    private final List<String> warnings = new ArrayList<>();
    private final LogSink warningSink = (level, category, message, error) -> {
        if (level == LogLevel.WARN) {
            warnings.add(message);
        }
    };

    @BeforeEach
    void captureWarnings() {
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink(warningSink);
    }

    @AfterEach
    void resetLog() {
        LogConfig.resetToDefaults();
    }

    /** Runs one frame of the given length; returns the number of fixed steps it ran. */
    private int frame(long deltaNanos) {
        clock.nextDelta = deltaNanos;
        phases.clear();
        loop.frame();
        return (int) phases.stream().filter(p -> p == Phase.FIXED).count();
    }

    @Test
    void phasesRunInOrder() {
        frame(FIXED_NANOS * 2);

        assertThat(phases).containsExactly(Phase.INPUT, Phase.FIXED, Phase.FIXED, Phase.UPDATE, Phase.LATE, Phase.RENDER);
    }

    @Test
    void sixtyHertzRunsExactlyOneStepPerFrame() {
        for (int i = 0; i < 600; i++) {
            assertThat(frame(FIXED_NANOS)).as("frame %d", i).isEqualTo(1);
            assertThat(time.alpha()).isZero();
        }
        assertThat(time.fixedTick()).isEqualTo(600);
        assertThat(time.elapsed()).isCloseTo(10.0, within(1e-6));
    }

    @Test
    void oneFortyFourHertzStepsOnTheRightFramesAndAlphaProgresses() {
        long frameNanos = FIXED_NANOS * 60 / 144;   // 144 Hz expressed in the same nanosecond grid
        long accumulator = 0;
        for (int i = 0; i < 288; i++) {
            accumulator += frameNanos;
            int expectedSteps = (int) (accumulator / FIXED_NANOS);
            accumulator %= FIXED_NANOS;

            assertThat(frame(frameNanos)).as("frame %d", i).isEqualTo(expectedSteps);
            assertThat(time.alpha()).as("alpha at frame %d", i)
                    .isCloseTo((float) accumulator / FIXED_NANOS, within(1e-6f))
                    .isGreaterThanOrEqualTo(0f).isLessThan(1f);
        }
        // 288 frames at 144 Hz = 2 s = 120 fixed steps (minus at most one partial step).
        assertThat(time.fixedTick()).isBetween(119L, 120L);
    }

    @Test
    void thirtyHertzRunsTwoStepsPerFrame() {
        for (int i = 0; i < 30; i++) {
            assertThat(frame(FIXED_NANOS * 2)).isEqualTo(2);
        }
        assertThat(time.fixedTick()).isEqualTo(60);
    }

    @Test
    void hitchIsClampedCappedAndCountedAsDropped() {
        int steps = frame(1_000_000_000L);   // a 1 s hitch

        assertThat(steps).isEqualTo(5);
        assertThat(time.unscaledDelta()).isEqualTo(0.25f);
        // The clamped 0.25 s holds 14 whole steps of 16,666,668 ns (just under 15); 5 ran, the rest were dropped.
        long wholeSteps = GameLoop.MAX_FRAME_DELTA_NANOS / FIXED_NANOS;
        assertThat(wholeSteps).isEqualTo(14);
        assertThat(time.droppedFixedSteps()).isEqualTo(wholeSteps - 5);
        assertThat(time.alpha()).isGreaterThanOrEqualTo(0f).isLessThan(1f);
        assertThat(warnings).singleElement().asString().contains("Dropped 9 fixed step(s)");
    }

    @Test
    void dropWarningIsLoggedAtMostOncePerSecond() {
        frame(1_000_000_000L);   // t = 0.25 s: warns
        frame(1_000_000_000L);   // t = 0.50 s: silent
        frame(1_000_000_000L);   // t = 0.75 s: silent
        frame(1_000_000_000L);   // t = 1.00 s: silent (only 0.75 s since the warning)
        frame(1_000_000_000L);   // t = 1.25 s: warns again

        assertThat(warnings).hasSize(2);
        assertThat(time.fixedTick()).isEqualTo(25);   // 5 steps per frame, never more
    }

    @Test
    void negativeDeltaIsTreatedAsZero() {
        assertThat(frame(-5_000_000L)).isZero();
        assertThat(time.unscaledDelta()).isZero();
    }

    @Test
    void pauseStopsFixedStepsAndScaledTimeButNotRender() {
        frame(FIXED_NANOS);
        time.setPaused(true);

        int steps = frame(FIXED_NANOS);

        assertThat(steps).isZero();
        assertThat(phases).containsExactly(Phase.INPUT, Phase.UPDATE, Phase.LATE, Phase.RENDER);
        assertThat(time.delta()).isZero();
        assertThat(time.unscaledDelta()).isEqualTo(FIXED, within(1e-6f));
        assertThat(time.elapsed()).isCloseTo(FIXED, within(1e-6));
        assertThat(time.unscaledElapsed()).isCloseTo(2 * FIXED, within(1e-6));

        time.setPaused(false);
        assertThat(frame(FIXED_NANOS)).isEqualTo(1);
    }

    @Test
    void halfTimeScaleStepsEveryOtherFrame() {
        time.setTimeScale(0.5f);
        List<Integer> steps = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            steps.add(frame(FIXED_NANOS));
        }

        assertThat(steps).containsExactly(0, 1, 0, 1, 0, 1);
        assertThat(time.delta()).isEqualTo(FIXED / 2, within(1e-6f));
    }

    @Test
    void zeroTimeScaleFreezesSimulation() {
        time.setTimeScale(0f);

        assertThat(frame(FIXED_NANOS)).isZero();
        assertThat(time.delta()).isZero();
        assertThat(phases).contains(Phase.RENDER);
    }

    @Test
    void timeScaleOutsideRangeIsRejected() {
        assertThatThrownBy(() -> time.setTimeScale(-0.1f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> time.setTimeScale(10.5f)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 10");
        assertThatThrownBy(() -> time.setTimeScale(Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        time.setTimeScale(10f);
        assertThat(time.timeScale()).isEqualTo(10f);
    }

    @Test
    void resetAccumulatorPreventsCatchUpBurstAfterResume() {
        frame(FIXED_NANOS / 2);                 // half a step accumulated
        loop.resetAccumulator();

        assertThat(frame(200_000_000L)).as("first frame after resume ignores its delta").isZero();
        assertThat(time.alpha()).isZero();
        assertThat(frame(FIXED_NANOS)).as("normal stepping resumes").isEqualTo(1);
    }

    @Test
    void fixedDeltaIsTheConfiguredStep() {
        assertThat(time.fixedDelta()).isEqualTo(FIXED);
        assertThatThrownBy(() -> new TimeState(0f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameLoop(clock, time, phases::add, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
