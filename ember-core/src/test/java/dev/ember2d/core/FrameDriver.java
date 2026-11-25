package dev.ember2d.core;

import dev.ember2d.core.internal.Clock;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;

/** Test helper: the real game loop with a fake clock, advancing timers and tweens like EmberGame does. */
final class FrameDriver {

    static final float FIXED = 1f / 60f;
    static final long FRAME_NANOS = Math.round(FIXED * 1_000_000_000d);

    final TimeState time = new TimeState(FIXED);
    final Timers timers = new Timers(time);
    final Tweens tweens = new Tweens(time);
    private long nextDelta = FRAME_NANOS;
    private final Clock clock = () -> nextDelta;
    private final GameLoop loop = new GameLoop(clock, time, phase -> {
        if (phase == Phase.UPDATE) {
            timers.update();
            tweens.update();
        }
    }, 5);

    /** Runs one 60 Hz frame. */
    void frame() {
        frame(FRAME_NANOS);
    }

    void frame(long deltaNanos) {
        nextDelta = deltaNanos;
        loop.frame();
    }

    void frames(int count) {
        for (int i = 0; i < count; i++) {
            frame();
        }
    }
}
