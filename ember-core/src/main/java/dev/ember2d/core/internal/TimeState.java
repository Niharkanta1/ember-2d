package dev.ember2d.core.internal;

import dev.ember2d.core.Time;

/** The engine's {@link Time}: read by everyone, advanced only by the {@link GameLoop}. */
public final class TimeState implements Time {

    /** Highest allowed time scale. */
    public static final float MAX_TIME_SCALE = 10f;

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final float fixedDelta;
    private final long fixedStepNanos;
    private float delta;
    private float unscaledDelta;
    private float alpha;
    private double elapsed;
    private double unscaledElapsed;
    private long fixedTick;
    private float timeScale = 1f;
    private boolean paused;
    private boolean stepRequested;
    private long droppedFixedSteps;

    public TimeState(float fixedDelta) {
        if (!(fixedDelta > 0f)) {
            throw new IllegalArgumentException("fixedDelta must be > 0 (was " + fixedDelta + ")");
        }
        this.fixedDelta = fixedDelta;
        this.fixedStepNanos = Math.round(fixedDelta * NANOS_PER_SECOND);
    }

    @Override
    public float delta() {
        return delta;
    }

    @Override
    public float unscaledDelta() {
        return unscaledDelta;
    }

    @Override
    public float fixedDelta() {
        return fixedDelta;
    }

    @Override
    public float alpha() {
        return alpha;
    }

    @Override
    public double elapsed() {
        return elapsed;
    }

    @Override
    public double unscaledElapsed() {
        return unscaledElapsed;
    }

    @Override
    public long fixedTick() {
        return fixedTick;
    }

    @Override
    public float timeScale() {
        return timeScale;
    }

    @Override
    public void setTimeScale(float scale) {
        if (!(scale >= 0f && scale <= MAX_TIME_SCALE)) {
            throw new IllegalArgumentException("timeScale must be between 0 and " + MAX_TIME_SCALE + " (was " + scale + ")");
        }
        timeScale = scale;
    }

    @Override
    public boolean paused() {
        return paused;
    }

    @Override
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused) {
            stepRequested = false;
        }
    }

    @Override
    public long droppedFixedSteps() {
        return droppedFixedSteps;
    }

    long fixedStepNanos() {
        return fixedStepNanos;
    }

    void beginFrame(long unscaledNanos, long scaledNanos) {
        unscaledDelta = (float) (unscaledNanos / NANOS_PER_SECOND);
        delta = (float) (scaledNanos / NANOS_PER_SECOND);
        unscaledElapsed += unscaledNanos / NANOS_PER_SECOND;
        elapsed += scaledNanos / NANOS_PER_SECOND;
    }

    void advanceFixedTick() {
        fixedTick++;
    }

    void addDroppedFixedSteps(long steps) {
        droppedFixedSteps += steps;
    }

    void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    @Override
    public void step() {
        if (paused) {
            stepRequested = true;
        }
    }

    /** Game loop: takes a pending {@link #step()} request. */
    public boolean takeStep() {
        boolean requested = stepRequested;
        stepRequested = false;
        return requested;
    }
}
