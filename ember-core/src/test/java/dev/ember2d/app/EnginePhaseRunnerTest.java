package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input.Keys;
import dev.ember2d.core.Phase;
import dev.ember2d.core.Timers;
import dev.ember2d.core.Tweens;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.internal.DefaultInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Flush points through the real game loop (F-019): after each fixed step, after UPDATE, after LATE. */
class EnginePhaseRunnerTest {

    record Ping(String from) {
    }

    private static final long FRAME = Math.round((1f / 60f) * 1_000_000_000d);

    private final TimeState time = new TimeState(1f / 60f);
    private final Timers timers = new Timers(time);
    private final DefaultEventBus bus = new DefaultEventBus(false);
    private final DefaultInput input = new DefaultInput(time, bus);
    /** What happened, in order: phases as "PHASE", deliveries as "got <from>". */
    private final List<String> trace = new ArrayList<>();
    private Phase queueIn;
    private long nextDelta = FRAME;

    private final GameLoop loop = new GameLoop(() -> nextDelta, time,
            new EnginePhaseRunner(timers, new Tweens(time), bus, input, () -> { }, () -> { }, () -> { }, phase -> {
                trace.add(phase.name());
                if (input.hasAction("jump") && input.justPressed("jump")) {
                    trace.add("jump");
                }
                if (phase == queueIn) {
                    bus.queue(new Ping(phase.name()));
                }
            }), 5);

    {
        bus.onGlobal(Ping.class, ping -> trace.add("got " + ping.from()));
    }

    private void frame(long delta) {
        nextDelta = delta;
        loop.frame();
    }

    @Test
    void eventQueuedInAFixedStepIsDeliveredBeforeTheNextFixedStep() {
        queueIn = Phase.FIXED;

        frame(FRAME * 2);   // two fixed steps

        assertThat(trace).containsExactly("INPUT", "FIXED", "got FIXED", "FIXED", "got FIXED", "UPDATE", "LATE", "RENDER");
    }

    @Test
    void eventQueuedInUpdateIsDeliveredBeforeLate() {
        queueIn = Phase.UPDATE;

        frame(FRAME);

        assertThat(trace).containsExactly("INPUT", "FIXED", "UPDATE", "got UPDATE", "LATE", "RENDER");
    }

    @Test
    void eventQueuedInLateIsDeliveredBeforeRender() {
        queueIn = Phase.LATE;

        frame(FRAME);

        assertThat(trace).containsExactly("INPUT", "FIXED", "UPDATE", "LATE", "got LATE", "RENDER");
    }

    @Test
    void eventQueuedInInputReachesTheFirstFlushOfTheFrame() {
        queueIn = Phase.INPUT;

        frame(FRAME);
        assertThat(trace).containsExactly("INPUT", "FIXED", "got INPUT", "UPDATE", "LATE", "RENDER");

        trace.clear();
        frame(0);   // no fixed step this frame: the first flush is after UPDATE
        assertThat(trace).containsExactly("INPUT", "UPDATE", "got INPUT", "LATE", "RENDER");
    }

    @Test
    void eventQueuedInRenderReachesTheNextFrame() {
        queueIn = Phase.RENDER;

        frame(FRAME);
        assertThat(trace).containsExactly("INPUT", "FIXED", "UPDATE", "LATE", "RENDER");

        queueIn = null;
        trace.clear();
        frame(FRAME);
        assertThat(trace).containsExactly("INPUT", "FIXED", "got RENDER", "UPDATE", "LATE", "RENDER");
    }

    @Test
    void eventQueuedByATimerIsDeliveredAfterUpdate() {
        timers.after(0f, () -> bus.queue(new Ping("timer")));

        frame(FRAME);

        assertThat(trace).containsExactly("INPUT", "FIXED", "UPDATE", "got timer", "LATE", "RENDER");
    }

    @Test
    void inputEdgesAreAppliedAtInputAndLatchedForTheFirstFixedStepOnly() {
        input.setBindings(InputBindings.parse(new com.badlogic.gdx.utils.JsonReader().parse(
                "{ \"version\": 1, \"contexts\": { \"gameplay\": { \"actions\": { \"jump\": [\"SPACE\"] } } } }"),
                "test.json"));
        input.source().keyDown(Keys.SPACE);

        frame(FRAME * 2);   // two fixed steps

        assertThat(trace).containsExactly("INPUT", "jump", "FIXED", "jump", "FIXED", "UPDATE", "jump", "LATE", "jump",
                "RENDER", "jump");
    }
}
