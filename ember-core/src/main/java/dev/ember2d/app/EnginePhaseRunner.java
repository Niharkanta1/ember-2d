package dev.ember2d.app;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ScreenUtils;
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
 *   <li>{@code INPUT}: start the event frame, then systems.</li>
 *   <li>{@code FIXED}: systems, then flush queued events (once per fixed step).</li>
 *   <li>{@code UPDATE}: timers, tweens, systems, then flush.</li>
 *   <li>{@code LATE}: systems, then flush.</li>
 *   <li>{@code RENDER}: systems, then the placeholder screen clear (until the renderer, I-060).</li>
 * </ul>
 *
 * The {@code systems} hook is where the ECS (I-040) and scenes (I-070) plug in.
 */
final class EnginePhaseRunner implements PhaseRunner {

    private final Timers timers;
    private final Tweens tweens;
    private final DefaultEventBus events;
    private final PhaseRunner systems;

    EnginePhaseRunner(Timers timers, Tweens tweens, DefaultEventBus events, PhaseRunner systems) {
        this.timers = Objects.requireNonNull(timers, "timers");
        this.tweens = Objects.requireNonNull(tweens, "tweens");
        this.events = Objects.requireNonNull(events, "events");
        this.systems = Objects.requireNonNull(systems, "systems");
    }

    @Override
    public void run(Phase phase) {
        switch (phase) {
            case INPUT -> {
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
                systems.run(phase);
                if (Gdx.gl != null) {
                    // Placeholder until the renderer (I-060). No GL on headless backends.
                    ScreenUtils.clear(0f, 0f, 0f, 1f);
                }
            }
        }
    }
}
