package dev.ember2d.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.math.Interpolation;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TweensTest {

    private final FrameDriver driver = new FrameDriver();
    private final Tweens tweens = driver.tweens;
    private float value = 10f;
    private final List<String> events = new ArrayList<>();

    @Test
    void reachesTargetExactlyAtTheEnd() {
        tweens.to(() -> value, v -> value = v, 20f, 0.5f, Interpolation.linear);   // 30 frames

        driver.frames(15);
        assertThat(value).isCloseTo(15f, within(0.01f));
        driver.frames(14);
        assertThat(value).isLessThan(20f);
        driver.frame();
        assertThat(value).isEqualTo(20f);
    }

    @Test
    void easingCurveIsApplied() {
        tweens.to(() -> value, v -> value = v, 20f, 1f, Interpolation.pow2In);

        driver.frames(30);   // half way in time

        assertThat(value).isCloseTo(Interpolation.pow2In.apply(10f, 20f, 0.5f), within(0.01f));
    }

    @Test
    void thenRunsOnceAfterTheTargetIsSet() {
        tweens.to(() -> value, v -> value = v, 0f, 0.1f, Interpolation.linear)
                .then(() -> events.add("done at " + value));

        driver.frames(20);

        assertThat(events).containsExactly("done at 0.0");
    }

    @Test
    void zeroDurationSetsTargetOnNextUpdate() {
        tweens.to(() -> value, v -> value = v, 3f, 0f, Interpolation.linear);
        assertThat(value).isEqualTo(10f);

        driver.frame();

        assertThat(value).isEqualTo(3f);
    }

    @Test
    void cancelStopsWhereItIsWithoutCompletion() {
        TweenHandle handle = tweens.to(() -> value, v -> value = v, 20f, 1f, Interpolation.linear)
                .then(() -> events.add("done"));
        driver.frames(30);
        float midway = value;

        handle.cancel();
        driver.frames(60);

        assertThat(value).isEqualTo(midway);
        assertThat(events).isEmpty();
        assertThat(handle.isActive()).isFalse();
    }

    @Test
    void pauseFreezesTweens() {
        tweens.to(() -> value, v -> value = v, 20f, 1f, Interpolation.linear);
        driver.frames(30);
        float midway = value;
        driver.time.setPaused(true);

        driver.frames(60);

        assertThat(value).isEqualTo(midway);
        driver.time.setPaused(false);
        driver.frames(30);
        assertThat(value).isEqualTo(20f);
    }

    @Test
    void tweenStartedInACompletionActionBeginsNextFrame() {
        tweens.to(() -> value, v -> value = v, 0f, 0f, Interpolation.linear)
                .then(() -> tweens.to(() -> value, v -> value = v, 5f, 0f, Interpolation.linear));

        driver.frame();
        assertThat(value).isZero();
        driver.frame();
        assertThat(value).isEqualTo(5f);
    }

    @Test
    void cancelAllStopsEverythingAndEntriesArePooled() {
        tweens.to(() -> value, v -> value = v, 20f, 1f, Interpolation.linear);
        tweens.to(() -> value, v -> value = v, 30f, 1f, Interpolation.linear);

        tweens.cancelAll();

        assertThat(tweens.size()).isZero();
        assertThat(tweens.pooledFree()).isEqualTo(2);
        tweens.to(() -> value, v -> value = v, 1f, 1f, Interpolation.linear);
        assertThat(tweens.pooledFree()).isEqualTo(1);
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThatThrownBy(() -> tweens.to(() -> value, v -> value = v, 1f, -1f, Interpolation.linear))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tweens.to(() -> value, v -> value = v, 1f, 1f, null))
                .isInstanceOf(NullPointerException.class);
    }
}
