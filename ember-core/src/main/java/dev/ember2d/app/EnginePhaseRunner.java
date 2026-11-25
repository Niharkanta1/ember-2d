package dev.ember2d.app;

import dev.ember2d.core.Phase;
import dev.ember2d.core.Timers;
import dev.ember2d.core.Tweens;
import dev.ember2d.core.internal.PhaseRunner;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.Objects;

/**
 * The engine's work in each phase, around the game's systems (F-002, F-019):
 *
 * <ul>
 *   <li>{@code INPUT}: advance asset loading, start the event frame, then systems.</li>
 *   <li>{@code FIXED}: systems, then flush queued events (once per fixed step).</li>
 *   <li>{@code UPDATE}: timers, tweens, systems, then flush.</li>
 *   <li>{@code LATE}: systems, then flush.</li>
 *   <li>{@code RENDER}: begin the world pass, systems, end the world pass (scale to the window). Without a
 *       renderer (headless) only the systems run.</li>
 * </ul>
 *
 * The {@code systems} hook is where the ECS (I-040) and scenes (I-070) plug in.
 */
final class EnginePhaseRunner implements PhaseRunner {

    private final Timers timers;
    private final Tweens tweens;
    private final DefaultEventBus events;
    private final PhaseRunner systems;
    private final Runnable assetLoading;
    private final Runnable beginRender;
    private final Runnable endRender;

    EnginePhaseRunner(Timers timers, Tweens tweens, DefaultEventBus events, Runnable assetLoading,
            Runnable beginRender, Runnable endRender, PhaseRunner systems) {
        this.timers = Objects.requireNonNull(timers, "timers");
        this.tweens = Objects.requireNonNull(tweens, "tweens");
        this.events = Objects.requireNonNull(events, "events");
        this.systems = Objects.requireNonNull(systems, "systems");
        this.assetLoading = Objects.requireNonNull(assetLoading, "assetLoading");
        this.beginRender = Objects.requireNonNull(beginRender, "beginRender");
        this.endRender = Objects.requireNonNull(endRender, "endRender");
    }

    @Override
    public void run(Phase phase) {
        switch (phase) {
            case INPUT -> {
                assetLoading.run();
                events.beginFrame();
                systems.run(phase);
            }
            case FIXED, LATE -> {
                systems.run(phase);
                events.flush();
            }
            case UPDATE -> {
                timers.update();
                tweens.update();
                systems.run(phase);
                events.flush();
            }
            case RENDER -> {
                beginRender.run();
                try {
                    systems.run(phase);
                } finally {
                    endRender.run();
                }
            }
        }
    }
}
