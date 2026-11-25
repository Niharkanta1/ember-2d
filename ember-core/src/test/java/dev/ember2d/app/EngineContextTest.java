package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.utils.Disposable;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EngineContextTest {

    interface Clock {
    }

    interface Scores {
    }

    record FixedClock() implements Clock {
    }

    @Test
    void serviceLookupByType() {
        FixedClock clock = new FixedClock();
        EngineContext context = new EngineContext.Builder(new EngineConfig()).register(Clock.class, clock).build();

        assertThat(context.service(Clock.class)).isSameAs(clock);
        assertThat(context.findService(Clock.class)).containsSame(clock);
        assertThat(context.findService(Scores.class)).isEmpty();
    }

    @Test
    void unknownServiceNamesTypeAndRegisteredServices() {
        EngineContext context = new EngineContext.Builder(new EngineConfig()).register(Clock.class, new FixedClock()).build();

        assertThatThrownBy(() -> context.service(Scores.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(Scores.class.getName())
                .hasMessageContaining("Registered: " + Clock.class.getName());
    }

    @Test
    void emptyContextSaysNoneRegistered() {
        EngineContext context = new EngineContext.Builder(new EngineConfig()).build();

        assertThatThrownBy(() -> context.service(Scores.class)).hasMessageEndingWith("Registered: none");
    }

    @Test
    void duplicateTypeIsRejected() {
        EngineContext.Builder builder = new EngineContext.Builder(new EngineConfig()).register(Clock.class, new FixedClock());

        assertThatThrownBy(() -> builder.register(Clock.class, new FixedClock()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void serviceMustImplementItsType() {
        EngineContext.Builder builder = new EngineContext.Builder(new EngineConfig());

        assertThatThrownBy(() -> builder.register((Class) Scores.class, new FixedClock()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a");
    }

    @Test
    void serviceRegisteredUnderTwoTypesIsDisposedOnce() {
        List<String> disposed = new ArrayList<>();
        final class Both implements Clock, Scores, Disposable {
            @Override
            public void dispose() {
                disposed.add("both");
            }
        }
        Both both = new Both();
        EngineContext context = new EngineContext.Builder(new EngineConfig())
                .register(Clock.class, both)
                .register(Scores.class, both)
                .build();

        assertThat(context.disposeServices()).isEmpty();
        assertThat(disposed).containsExactly("both");
    }
}
