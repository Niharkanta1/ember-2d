package dev.ember2d.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimersTest {

    private final FrameDriver driver = new FrameDriver();
    private final Timers timers = driver.timers;
    private final List<String> fired = new ArrayList<>();

    /** Runs frames until the given frame number (1-based) and records on which frame each event fired. */
    private List<String> runFrames(int count) {
        List<String> log = new ArrayList<>();
        for (int frame = 1; frame <= count; frame++) {
            int before = fired.size();
            driver.frame();
            for (int i = before; i < fired.size(); i++) {
                log.add(fired.get(i) + "@" + frame);
            }
        }
        return log;
    }

    @Test
    void afterFiresOnTheExactFrame() {
        timers.after(0.05f, () -> fired.add("a"));       // 3 frames at 60 Hz
        timers.after(1f, () -> fired.add("b"));          // frame 60

        assertThat(runFrames(61)).containsExactly("a@3", "b@60");
    }

    @Test
    void zeroDelayFiresOnTheNextUpdate() {
        timers.after(0f, () -> fired.add("now"));

        assertThat(runFrames(2)).containsExactly("now@1");
    }

    @Test
    void everyKeepsItsRhythmUntilCancelled() {
        TimerHandle handle = timers.every(0.1f, () -> fired.add("tick"));   // every 6 frames

        assertThat(runFrames(18)).containsExactly("tick@6", "tick@12", "tick@18");
        handle.cancel();
        assertThat(runFrames(12)).isEmpty();
        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void everyFiresOncePerIntervalInALongFrame() {
        timers.every(0.05f, () -> fired.add("tick"));

        driver.frame(250_000_000L);   // 0.25 s hitch (clamp limit)

        assertThat(fired).hasSize(5);
    }

    @Test
    void cancelledTimerNeverFires() {
        TimerHandle handle = timers.after(0.05f, () -> fired.add("never"));
        driver.frame();
        timers.cancel(handle);

        assertThat(runFrames(10)).isEmpty();
        assertThat(timers.size()).isZero();
    }

    @Test
    void dueTimersFireInDueThenCreationOrder() {
        timers.after(0.05f, () -> fired.add("late"));
        timers.after(0.0333f, () -> fired.add("early"));
        timers.after(0.05f, () -> fired.add("late-second"));

        driver.frame(250_000_000L);   // everything is due in one frame

        assertThat(fired).containsExactly("early", "late", "late-second");
    }

    @Test
    void schedulingInsideAnActionRunsOnTheNextFrame() {
        timers.after(0f, () -> {
            fired.add("outer");
            timers.after(0f, () -> fired.add("inner"));
        });

        assertThat(runFrames(3)).containsExactly("outer@1", "inner@2");
    }

    @Test
    void cancellingInsideAnActionIsSafe() {
        TimerHandle[] other = new TimerHandle[1];
        timers.after(0f, () -> {
            fired.add("first");
            other[0].cancel();
        });
        other[0] = timers.after(0f, () -> fired.add("cancelled"));

        assertThat(runFrames(3)).containsExactly("first@1");
    }

    @Test
    void pauseFreezesTimers() {
        timers.after(0.05f, () -> fired.add("a"));
        driver.frame();
        driver.time.setPaused(true);

        assertThat(runFrames(30)).isEmpty();
        driver.time.setPaused(false);
        assertThat(runFrames(3)).containsExactly("a@2");
    }

    @Test
    void staleHandleCannotCancelAReusedEntry() {
        TimerHandle first = timers.after(0f, () -> fired.add("first"));
        driver.frame();                                   // fires; entry returns to the pool
        TimerHandle second = timers.after(0.05f, () -> fired.add("second"));

        first.cancel();                                   // same pooled entry, older generation

        assertThat(first.isActive()).isFalse();
        assertThat(second.isActive()).isTrue();
        assertThat(runFrames(3)).containsExactly("second@3");
    }

    @Test
    void entriesArePooledAndReused() {
        timers.after(0f, () -> { });
        driver.frame();
        int free = timers.pooledFree();

        timers.after(0f, () -> { });

        assertThat(free).isEqualTo(1);
        assertThat(timers.pooledFree()).isZero();       // the freed entry was reused, not a new one
    }

    @Test
    void cancelAllStopsEverything() {
        timers.after(0.05f, () -> fired.add("a"));
        timers.every(0.05f, () -> fired.add("b"));

        timers.cancelAll();

        assertThat(runFrames(10)).isEmpty();
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> timers.after(-1f, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> timers.after(Float.NaN, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> timers.every(0f, () -> { })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("> 0");
        assertThatThrownBy(() -> timers.after(1f, null)).isInstanceOf(NullPointerException.class);
    }
}
