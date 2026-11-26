package dev.ember2d.core.internal;

import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import java.util.Objects;

/**
 * Runs one frame per libGDX {@code render()} call (F-002): clamps the frame delta, runs {@link Phase#INPUT},
 * runs {@link Phase#FIXED} zero or more times from an accumulator with a catch-up limit, computes the
 * interpolation alpha, then runs {@link Phase#UPDATE}, {@link Phase#LATE} and {@link Phase#RENDER}.
 *
 * <p>The accumulator counts whole nanoseconds, so step timing is exact and reproducible in tests.
 * Allocation-free per frame.
 */
public final class GameLoop {

    /** Longest frame delta accepted; longer frames (hitches, debugger pauses) are clamped to this. */
    public static final long MAX_FRAME_DELTA_NANOS = 250_000_000L;

    private static final Log LOG = Log.get("time");
    private static final double DROP_WARNING_INTERVAL_SECONDS = 1.0;

    private final Clock clock;
    private final TimeState time;
    private final PhaseRunner runner;
    private final int maxCatchUpSteps;
    private long accumulatorNanos;
    private boolean skipNextDelta;
    private double lastDropWarning = Double.NEGATIVE_INFINITY;

    public GameLoop(Clock clock, TimeState time, PhaseRunner runner, int maxCatchUpSteps) {
        if (maxCatchUpSteps < 1) {
            throw new IllegalArgumentException("maxCatchUpSteps must be >= 1 (was " + maxCatchUpSteps + ")");
        }
        this.clock = Objects.requireNonNull(clock, "clock");
        this.time = Objects.requireNonNull(time, "time");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.maxCatchUpSteps = maxCatchUpSteps;
    }

    /** Runs one frame. */
    public void frame() {
        long raw = clock.frameDeltaNanos();
        long unscaled = skipNextDelta ? 0L : Math.max(0L, Math.min(raw, MAX_FRAME_DELTA_NANOS));
        skipNextDelta = false;
        long scaled = time.paused() ? 0L : Math.round(unscaled * (double) time.timeScale());
        boolean step = time.paused() && time.takeStep();
        if (step) {
            scaled = time.fixedStepNanos() - accumulatorNanos;   // exactly one fixed step this frame
        }
        time.beginFrame(unscaled, scaled);

        runner.run(Phase.INPUT);

        long fixedStep = time.fixedStepNanos();
        accumulatorNanos += scaled;
        int steps = 0;
        while (accumulatorNanos >= fixedStep && steps < maxCatchUpSteps) {
            runner.run(Phase.FIXED);
            accumulatorNanos -= fixedStep;
            steps++;
            time.advanceFixedTick();
        }
        if (accumulatorNanos >= fixedStep) {
            long dropped = accumulatorNanos / fixedStep;
            accumulatorNanos %= fixedStep;
            time.addDroppedFixedSteps(dropped);
            warnDropped(dropped);
        }
        time.setAlpha((float) ((double) accumulatorNanos / fixedStep));

        runner.run(Phase.UPDATE);
        runner.run(Phase.LATE);
        runner.run(Phase.RENDER);
    }

    /**
     * Discards accumulated time and ignores the next frame's delta, so the game does not fast-forward after
     * the app resumes.
     */
    public void resetAccumulator() {
        accumulatorNanos = 0L;
        skipNextDelta = true;
        time.setAlpha(0f);
    }

    private void warnDropped(long dropped) {
        double now = time.unscaledElapsed();
        if (now - lastDropWarning >= DROP_WARNING_INTERVAL_SECONDS) {
            lastDropWarning = now;
            LOG.warn("Dropped " + dropped + " fixed step(s): a frame needed more than " + maxCatchUpSteps
                    + " catch-up steps (total dropped: " + time.droppedFixedSteps() + ")");
        }
    }
}
