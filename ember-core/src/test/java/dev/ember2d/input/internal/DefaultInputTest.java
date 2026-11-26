package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputCapture;
import dev.ember2d.input.InputContextChanged;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Input semantics through a real {@link GameLoop} driven by a fake clock. The fixed step is 1/16 s (62.5 ms, exact in
 * nanoseconds), so frame deltas give exact fixed-step counts.
 */
class DefaultInputTest {

    static final long STEP = 62_500_000L;
    static final long SHORT = 10_000_000L;   // a frame with no fixed step on its own

    static final String BINDINGS = """
            { "version": 1, "contexts": {
                "gameplay": {
                  "actions": { "jump": ["SPACE", "Z"], "fire": ["MOUSE_LEFT"], "pause": ["ESCAPE", "P"] },
                  "axes": { "move": { "left": ["LEFT", "A"], "right": ["RIGHT", "D"], "up": ["UP"], "down": ["DOWN"] },
                            "raw": { "left": ["J"], "right": ["L"], "up": ["I"], "down": ["K"], "normalize": false },
                            "steer": { "negative": ["Q"], "positive": ["E"] } } },
                "ui": {
                  "actions": { "ui_back": ["ESCAPE"] },
                  "axes": { "ui_nav": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"], "down": ["DOWN"] } } } } }
            """;

    final TimeState time = new TimeState(1f / 16f);
    final DefaultEventBus bus = new DefaultEventBus(false);
    final DefaultInput input = new DefaultInput(time, bus);
    final GdxInputSource keys = input.source();

    /** What "jump" looked like: one entry per fixed step and per UPDATE, as "F:pressed/justPressed/justReleased". */
    final List<String> seen = new ArrayList<>();
    String probe = "jump";
    long nextDelta;

    final GameLoop loop = new GameLoop(() -> nextDelta, time, phase -> {
        if (phase == Phase.INPUT) {
            input.beginFrame();
        } else if (phase == Phase.FIXED) {
            input.beginFixedStep();
            seen.add("F:" + state());
            input.endFixedStep();
        } else if (phase == Phase.UPDATE) {
            seen.add("U:" + state());
        }
    }, 5);

    DefaultInputTest() {
        input.setBindings(InputBindings.parse(new JsonReader().parse(BINDINGS), "test.json"));
    }

    String state() {
        return (input.pressed(probe) ? "P" : "-") + (input.justPressed(probe) ? "J" : "-")
                + (input.justReleased(probe) ? "R" : "-");
    }

    void frame(long delta) {
        nextDelta = delta;
        loop.frame();
    }

    List<String> frameSeen(long delta) {
        seen.clear();
        frame(delta);
        return List.copyOf(seen);
    }

    @Test
    void pressIsSeenByTheFixedStepAndTheFrame() {
        keys.keyDown(Keys.SPACE);

        assertThat(frameSeen(STEP)).containsExactly("F:PJ-", "U:PJ-");
        assertThat(frameSeen(STEP)).containsExactly("F:P--", "U:P--");
    }

    @Test
    void pressInFramesWithoutAFixedStepIsLatchedForTheNextFixedStep() {
        keys.keyDown(Keys.SPACE);

        assertThat(frameSeen(SHORT)).as("frame of the press, no fixed step").containsExactly("U:PJ-");
        assertThat(frameSeen(SHORT)).as("frame edge is gone").containsExactly("U:P--");
        assertThat(frameSeen(STEP - 2 * SHORT)).as("first fixed step still sees it").containsExactly("F:PJ-", "U:P--");
        assertThat(frameSeen(STEP)).containsExactly("F:P--", "U:P--");
    }

    @Test
    void withSeveralFixedStepsOnlyTheFirstSeesTheEdge() {
        keys.keyDown(Keys.SPACE);

        assertThat(frameSeen(3 * STEP)).containsExactly("F:PJ-", "F:P--", "F:P--", "U:PJ-");
    }

    @Test
    void releaseLatchesTheSameWay() {
        keys.keyDown(Keys.SPACE);
        frame(STEP);
        keys.keyUp(Keys.SPACE);

        assertThat(frameSeen(SHORT)).containsExactly("U:--R");
        assertThat(frameSeen(STEP - SHORT)).containsExactly("F:--R", "U:---");
    }

    @Test
    void aTapWithinOneFrameIsNotLost() {
        keys.keyDown(Keys.SPACE);
        keys.keyUp(Keys.SPACE);

        assertThat(frameSeen(STEP)).containsExactly("F:-JR", "U:-JR");
    }

    @Test
    void secondBindingKeepsTheActionHeld() {
        keys.keyDown(Keys.SPACE);
        keys.keyDown(Keys.Z);
        frame(STEP);
        keys.keyUp(Keys.SPACE);

        assertThat(frameSeen(STEP)).containsExactly("F:P--", "U:P--");
        keys.keyUp(Keys.Z);
        assertThat(frameSeen(STEP)).containsExactly("F:--R", "U:--R");
    }

    @Test
    void whilePausedNoEdgeIsKeptForTheFixedStep() {
        time.setPaused(true);
        keys.keyDown(Keys.SPACE);

        assertThat(frameSeen(STEP)).as("paused: no fixed step, frame edge visible").containsExactly("U:PJ-");
        time.setPaused(false);
        assertThat(frameSeen(STEP)).as("stale jump is not replayed").containsExactly("F:P--", "U:P--");
    }

    @Test
    void heldTimeCountsGameTime() {
        keys.keyDown(Keys.SPACE);
        frame(STEP);
        assertThat(input.heldTime("jump")).as("press frame").isZero();
        frame(STEP);
        assertThat(input.heldTime("jump")).isCloseTo(0.0625f, within(1e-6f));
        time.setTimeScale(0.5f);
        frame(STEP);
        assertThat(input.heldTime("jump")).isCloseTo(0.09375f, within(1e-6f));
        time.setPaused(true);
        frame(STEP);
        assertThat(input.heldTime("jump")).as("paused").isCloseTo(0.09375f, within(1e-6f));
        keys.keyUp(Keys.SPACE);
        frame(STEP);
        assertThat(input.heldTime("jump")).isZero();
    }

    @Test
    void axesCancelAndNormalise() {
        Vector2 v = new Vector2();
        keys.keyDown(Keys.RIGHT);
        keys.keyDown(Keys.UP);
        keys.keyDown(Keys.J);
        keys.keyDown(Keys.I);
        keys.keyDown(Keys.Q);
        keys.keyDown(Keys.E);
        frame(SHORT);

        assertThat(input.axis2("move", v).len()).isCloseTo(1f, within(1e-6f));
        assertThat(v.x).isCloseTo(v.y, within(1e-6f)).isPositive();
        assertThat(input.axis2("raw", v)).as("normalize false").isEqualTo(new Vector2(-1f, 1f));
        assertThat(input.axis("steer")).as("opposites cancel").isZero();

        keys.keyDown(Keys.A);   // left and right together
        keys.keyUp(Keys.E);
        frame(SHORT);
        assertThat(input.axis2("move", v)).isEqualTo(new Vector2(0f, 1f));
        assertThat(input.axis("steer")).isEqualTo(-1f);
    }

    @Test
    void mouseButtonsDriveActions() {
        probe = "fire";
        keys.touchDown(10, 20, 0, Buttons.LEFT);

        assertThat(frameSeen(STEP)).containsExactly("F:PJ-", "U:PJ-");
        keys.touchDown(10, 20, 1, Buttons.LEFT);   // a second touch pointer is not the mouse
        keys.touchUp(10, 20, 1, Buttons.LEFT);
        assertThat(frameSeen(STEP)).containsExactly("F:P--", "U:P--");
    }

    @Test
    void higherContextConsumesItsInputs() {
        List<String> tops = new ArrayList<>();
        bus.onGlobal(InputContextChanged.class, e -> tops.add(e.top()));
        Vector2 v = new Vector2();
        probe = "pause";
        keys.keyDown(Keys.ESCAPE);
        keys.keyDown(Keys.RIGHT);
        frame(STEP);
        frame(STEP);   // the press edges are from an earlier frame now

        input.pushContext("ui");
        assertThat(input.topContext()).isEqualTo("ui");
        assertThat(tops).containsExactly("ui");
        assertThat(state()).as("gameplay loses ESCAPE").isEqualTo("--R");
        assertThat(input.pressed("ui_back")).as("held across the change: needs a new press").isFalse();
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);
        assertThat(input.axis2("ui_nav", v)).as("held across the change").isEqualTo(Vector2.Zero);

        keys.keyUp(Keys.ESCAPE);
        keys.keyDown(Keys.ESCAPE);
        keys.keyDown(Keys.P);   // not bound in ui: still reaches gameplay
        keys.keyDown(Keys.DOWN);
        frame(STEP);
        assertThat(input.justPressed("ui_back")).isTrue();
        assertThat(state()).isEqualTo("PJ-");
        assertThat(input.axis2("ui_nav", v)).isEqualTo(new Vector2(0f, -1f));
        assertThat(input.axis2("move", v)).isEqualTo(Vector2.Zero);

        input.popContext();
        assertThat(tops).containsExactly("ui", "gameplay");
        assertThat(input.justReleased("ui_back")).isTrue();
        assertThat(input.pressed("ui_back")).as("not on the stack").isFalse();
        assertThat(input.axis2("move", v)).as("held across the change again").isEqualTo(Vector2.Zero);
        assertThat(input.pressed("pause")).as("P is still held for gameplay").isTrue();
    }

    @Test
    void contextStackRules() {
        assertThat(input.topContext()).isEqualTo("gameplay");
        assertThatThrownBy(input::popContext).isInstanceOf(IllegalStateException.class).hasMessageContaining("gameplay");
        assertThatThrownBy(() -> input.pushContext("menu")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menu").hasMessageContaining("[gameplay, ui]");
    }

    @Test
    void captureBlocksNewPressesButNotReleases() {
        boolean[] keyboard = {false};
        input.setCapture(new InputCapture() {
            @Override
            public boolean capturesKeyboard() {
                return keyboard[0];
            }

            @Override
            public boolean capturesPointer() {
                return false;
            }
        });
        keys.keyDown(Keys.SPACE);
        frame(STEP);
        keyboard[0] = true;
        keys.keyDown(Keys.Z);
        keys.keyUp(Keys.SPACE);
        keys.touchDown(0, 0, 0, Buttons.LEFT);

        assertThat(frameSeen(STEP)).as("release applied, Z ignored").containsExactly("F:--R", "U:--R");
        assertThat(input.pressed("fire")).as("pointer not captured").isTrue();
        keyboard[0] = false;
        assertThat(frameSeen(STEP)).as("Z held during capture stays up").containsExactly("F:---", "U:---");
    }

    @Test
    void releaseAllReleasesEverythingInTheNextFrame() {
        keys.keyDown(Keys.SPACE);
        keys.keyDown(Keys.RIGHT);
        frame(STEP);

        input.releaseAll();
        assertThat(input.pressed("jump")).as("applied with the next frame").isTrue();
        assertThat(frameSeen(STEP)).containsExactly("F:--R", "U:--R");
        assertThat(input.axis2("move", new Vector2())).isEqualTo(Vector2.Zero);

        keys.keyUp(Keys.SPACE);   // the lost key-up arrives late: no effect
        assertThat(frameSeen(STEP)).containsExactly("F:---", "U:---");
        keys.keyDown(Keys.SPACE);
        assertThat(frameSeen(STEP)).containsExactly("F:PJ-", "U:PJ-");
    }

    @Test
    void pointerPositions() {
        keys.mouseMoved(100, 50);
        Vector2 v = new Vector2();

        assertThat(input.pointerScreen(v)).isEqualTo(new Vector2(100f, 50f));
        assertThat(input.pointerWorld(v)).as("no renderer: window pixels").isEqualTo(new Vector2(100f, 50f));
        input.setScreenToWorld((x, y, out) -> out.set(x / 10f, -y / 10f));
        keys.touchDragged(200, 30, 0);
        assertThat(input.pointerWorld(v)).isEqualTo(new Vector2(20f, -3f));
    }

    @Test
    void misuseOfNamesFailsWithTheDefinedNames() {
        assertThat(input.hasAction("jump")).isTrue();
        assertThat(input.hasAction("move")).isTrue();
        assertThat(input.hasAction("dash")).isFalse();
        assertThatThrownBy(() -> input.pressed("dash")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\"dash\"").hasMessageContaining("jump");
        assertThatThrownBy(() -> input.pressed("move")).hasMessageContaining("is an input axis");
        assertThatThrownBy(() -> input.axis("jump")).hasMessageContaining("is an input action");
        assertThatThrownBy(() -> input.axis("move")).hasMessageContaining("2D; use axis2()");
        assertThatThrownBy(() -> input.axis2("steer", new Vector2())).hasMessageContaining("1D; use axis()");
        assertThatThrownBy(() -> input.axis("nope")).hasMessageContaining("Unknown input axis");
    }

    @Test
    void steadyStateFramesDoNotAllocate() {
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        Vector2 v = new Vector2();
        GameLoop quiet = new GameLoop(() -> STEP, time, phase -> {
            if (phase == Phase.INPUT) {
                input.beginFrame();
            } else if (phase == Phase.FIXED) {
                input.beginFixedStep();
                input.justPressed("jump");
                input.axis2("move", v);
                input.endFixedStep();
            } else if (phase == Phase.UPDATE) {
                input.pressed("jump");
                input.axis("steer");
                input.heldTime("jump");
            }
        }, 5);
        for (int i = 0; i < 20_000; i++) {   // warm up (JIT)
            pressAndRelease(i);
            quiet.frame();
        }
        long id = Thread.currentThread().getId();
        long before = threads.getThreadAllocatedBytes(id);
        for (int i = 0; i < 10_000; i++) {
            pressAndRelease(i);
            quiet.frame();
        }
        long allocated = threads.getThreadAllocatedBytes(id) - before;
        assertThat(allocated).as("bytes allocated over 10,000 frames").isLessThan(1024);
    }

    private void pressAndRelease(int i) {
        if (i % 2 == 0) {
            keys.keyDown(Keys.SPACE);
        } else {
            keys.keyUp(Keys.SPACE);
        }
    }
}
