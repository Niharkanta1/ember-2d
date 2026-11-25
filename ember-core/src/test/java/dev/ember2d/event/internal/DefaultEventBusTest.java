package dev.ember2d.event.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import dev.ember2d.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEventBusTest {

    record Hit(int amount) {
    }

    record Heal(int amount) {
    }

    static class Base {
    }

    static final class Derived extends Base {
    }

    private final DefaultEventBus bus = new DefaultEventBus(false);
    private final List<String> received = new ArrayList<>();
    private final List<String> log = new ArrayList<>();
    private final LogSink sink = (level, category, message, error) -> log.add(level + " " + message);

    @BeforeEach
    void captureLog() {
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink(sink);
    }

    @AfterEach
    void resetLog() {
        LogConfig.resetToDefaults();
    }

    @Test
    void immediateEventsReachHandlersBeforePublishReturns() {
        bus.onGlobal(Hit.class, e -> received.add("hit " + e.amount()));

        bus.publish(new Hit(3));

        assertThat(received).containsExactly("hit 3");
    }

    @Test
    void queuedEventsWaitForFlushAndKeepPublishOrder() {
        bus.onGlobal(Hit.class, e -> received.add("hit " + e.amount()));
        bus.onGlobal(Heal.class, e -> received.add("heal " + e.amount()));

        bus.queue(new Hit(1));
        bus.queue(new Heal(2));
        bus.queue(new Hit(3));
        assertThat(received).isEmpty();
        assertThat(bus.queuedCount()).isEqualTo(3);

        bus.flush();

        assertThat(received).containsExactly("hit 1", "heal 2", "hit 3");
        assertThat(bus.queuedCount()).isZero();
    }

    @Test
    void handlersRunInSubscriptionOrder() {
        bus.onGlobal(Hit.class, e -> received.add("first"));
        bus.onGlobal(Hit.class, e -> received.add("second"));

        bus.publish(new Hit(1));

        assertThat(received).containsExactly("first", "second");
    }

    @Test
    void deliveryMatchesTheExactClassOnly() {
        bus.onGlobal(Base.class, e -> received.add("base"));

        bus.publish(new Derived());
        bus.publish(new Base());

        assertThat(received).containsExactly("base");
    }

    @Test
    void eventsQueuedDuringFlushAreDeliveredInTheSameFlush() {
        bus.onGlobal(Hit.class, e -> {
            received.add("hit " + e.amount());
            if (e.amount() < 3) {
                bus.queue(new Hit(e.amount() + 1));
            }
        });
        bus.queue(new Hit(1));

        bus.flush();

        assertThat(received).containsExactly("hit 1", "hit 2", "hit 3");
        assertThat(log).noneMatch(line -> line.startsWith("WARN"));
    }

    @Test
    void reentrancyIsLimitedToEightPassesWithAWarning() {
        bus.onGlobal(Hit.class, e -> {
            received.add("hit " + e.amount());
            bus.queue(new Hit(e.amount() + 1));   // never stops on its own
        });
        bus.queue(new Hit(1));

        bus.flush();

        assertThat(received).hasSize(DefaultEventBus.MAX_FLUSH_PASSES);
        assertThat(bus.queuedCount()).isEqualTo(1);
        assertThat(log).anyMatch(line -> line.startsWith("WARN 1 queued event(s) deferred to the next flush point"));

        bus.flush();   // the deferred event continues at the next flush point
        assertThat(received).hasSize(2 * DefaultEventBus.MAX_FLUSH_PASSES);
    }

    @Test
    void subscribingDuringDeliveryTakesEffectForTheNextEvent() {
        bus.onGlobal(Hit.class, e -> {
            received.add("outer");
            if (received.size() == 1) {
                bus.onGlobal(Hit.class, inner -> received.add("inner"));
            }
        });

        bus.publish(new Hit(1));
        bus.publish(new Hit(2));

        assertThat(received).containsExactly("outer", "outer", "inner");
    }

    @Test
    void handlerCancelledDuringDeliveryIsNotCalledAgain() {
        Subscription[] second = new Subscription[1];
        bus.onGlobal(Hit.class, e -> {
            received.add("first");
            second[0].cancel();
        });
        second[0] = bus.onGlobal(Hit.class, e -> received.add("second"));

        bus.publish(new Hit(1));
        bus.publish(new Hit(2));

        assertThat(received).containsExactly("first", "first");
        assertThat(second[0].isActive()).isFalse();
    }

    @Test
    void cancelIsIdempotent() {
        Subscription subscription = bus.onGlobal(Hit.class, e -> received.add("hit"));

        subscription.cancel();
        subscription.cancel();
        bus.publish(new Hit(1));

        assertThat(received).isEmpty();
    }

    @Test
    void closingAScopeCancelsItsSubscriptionsButNotGlobalOnes() {
        bus.onGlobal(Hit.class, e -> received.add("global"));
        DefaultEventBus.Scope scene = bus.openScope();
        Subscription scoped = bus.on(Hit.class, e -> received.add("scene"));
        Subscription explicitGlobal = bus.onGlobal(Hit.class, e -> received.add("global-in-scene"));

        bus.publish(new Hit(1));
        bus.closeScope(scene);
        bus.publish(new Hit(2));

        assertThat(received).containsExactly("global", "scene", "global-in-scene", "global", "global-in-scene");
        assertThat(scoped.isActive()).isFalse();
        assertThat(explicitGlobal.isActive()).isTrue();
        assertThat(scene.isClosed()).isTrue();
    }

    @Test
    void onWithoutAnOpenScopeBehavesLikeOnGlobal() {
        Subscription subscription = bus.on(Hit.class, e -> received.add("hit"));
        DefaultEventBus.Scope later = bus.openScope();
        bus.closeScope(later);

        bus.publish(new Hit(1));

        assertThat(received).containsExactly("hit");
        assertThat(subscription.isActive()).isTrue();
    }

    @Test
    void handlerExceptionIsLoggedAndOtherHandlersStillRun() {
        bus.onGlobal(Hit.class, e -> {
            throw new IllegalStateException("boom");
        });
        bus.onGlobal(Hit.class, e -> received.add("still called"));

        assertThatCode(() -> bus.publish(new Hit(1))).doesNotThrowAnyException();

        assertThat(received).containsExactly("still called");
        assertThat(log).anyMatch(line -> line.startsWith("ERROR Event handler") && line.contains(Hit.class.getName()));
    }

    @Test
    void devModeRethrowsTheFirstFailurePerFrameAfterDelivery() {
        DefaultEventBus dev = new DefaultEventBus(true);
        dev.onGlobal(Hit.class, e -> {
            throw new IllegalStateException("first " + e.amount());
        });
        dev.onGlobal(Hit.class, e -> received.add("after " + e.amount()));

        dev.beginFrame();
        dev.queue(new Hit(1));
        dev.queue(new Hit(2));
        assertThatThrownBy(dev::flush).isInstanceOf(IllegalStateException.class).hasMessage("first 1");
        assertThat(received).as("the whole flush completed before the rethrow").containsExactly("after 1", "after 2");

        assertThatCode(() -> dev.publish(new Hit(3))).as("only the first failure per frame is rethrown")
                .doesNotThrowAnyException();

        dev.beginFrame();
        assertThatThrownBy(() -> dev.publish(new Hit(4))).hasMessage("first 4");
    }

    @Test
    void devModeDoesNotRethrowInsideANestedPublish() {
        DefaultEventBus dev = new DefaultEventBus(true);
        dev.onGlobal(Heal.class, e -> {
            throw new IllegalStateException("inner");
        });
        dev.onGlobal(Hit.class, e -> {
            dev.publish(new Heal(1));            // fails inside a flush: must not abort the flush
            received.add("hit " + e.amount());
        });
        dev.beginFrame();
        dev.queue(new Hit(1));
        dev.queue(new Hit(2));

        assertThatThrownBy(dev::flush).hasMessage("inner");
        assertThat(received).containsExactly("hit 1", "hit 2");
    }

    @Test
    void nullEventsAndHandlersAreRejected() {
        assertThatThrownBy(() -> bus.publish(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> bus.queue(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> bus.onGlobal(Hit.class, null)).isInstanceOf(NullPointerException.class);
    }
}
