package dev.ember2d.kit.topdown.rpg;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.topdown.TopDownBody;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Interaction (F-017, I-400): choosing among candidates, focus events, using. */
class InteractionTest {

    boolean pressed;
    final Input input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] {Input.class},
            (proxy, method, args) -> method.getName().equals("justPressed") ? "interact".equals(args[0]) && pressed
                    : method.getReturnType() == boolean.class ? false : null);
    final World world = new World(false);
    final DefaultEventBus events = new DefaultEventBus(false);
    final TimeState time = new TimeState(1f / 60f);
    final InteractionSystem system = new InteractionSystem(world, input, events);
    final List<Object> seen = new ArrayList<>();
    final int player;
    final TopDownBody body;

    InteractionTest() {
        world.addSystem(system);
        events.onGlobal(Interacted.class, seen::add);
        events.onGlobal(InteractionFocus.class, seen::add);
        player = world.create();
        world.add(player, new Transform()).teleport(0f, 0f);
        world.add(player, new Interactor());
        body = world.add(player, new TopDownBody());
        body.facingX = 1f;   // facing right
        body.facingY = 0f;
    }

    int thing(float x, float y, String action) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        Interactable i = world.add(e, new Interactable());
        i.action = action;
        i.prompt = "Use " + action;
        return e;
    }

    void frame() {
        world.runPhase(Phase.UPDATE, time);
    }

    int target() {
        return world.get(player, Interactor.class).target;
    }

    @Test
    void theNearestInRangeAndInFrontWins() {
        thing(-0.8f, 0f, "behind");        // in range, but behind
        thing(2f, 0f, "far");              // in front, out of range (1.2)
        int ahead = thing(1.1f, 0.1f, "ahead");
        int nearer = thing(0.7f, 0.3f, "nearer");   // 23 degrees off but nearer: 0.76 x 1.13 < 1.10 x 1.03
        frame();
        assertThat(target()).isEqualTo(nearer);
        world.get(nearer, Interactable.class).enabled = false;
        frame();
        assertThat(target()).isEqualTo(ahead);
        assertThat(seen).containsExactly(new InteractionFocus(player, nearer, "Use nearer"),
                new InteractionFocus(player, ahead, "Use ahead"));
        frame();
        assertThat(seen).as("published only on change").hasSize(2);
    }

    @Test
    void touchingIgnoresTheFacingAndTheConeLimits() {
        int close = thing(-0.2f, 0f, "close");
        int side = thing(0f, 1f, "side");   // 90 degrees: outside a 120-degree cone
        frame();
        assertThat(target()).isEqualTo(close);
        world.destroy(close);
        frame();
        assertThat(target()).isEqualTo(World.NONE);
        world.get(player, Interactor.class).cone = 200f;
        frame();
        assertThat(target()).isEqualTo(side);
        assertThat(seen.get(seen.size() - 2)).isEqualTo(new InteractionFocus(player, World.NONE, ""));
    }

    @Test
    void pressingOrCodeInteractsAndALockedBodyCannot() {
        int door = thing(1f, 0f, "open:door");
        assertThat(system.interact(player)).as("nothing focused yet").isFalse();
        pressed = true;
        frame();
        assertThat(seen).contains(new Interacted(player, door, "open:door"));
        pressed = false;
        assertThat(system.interact(player)).isTrue();
        assertThat(seen).filteredOn(Interacted.class::isInstance).hasSize(2);

        body.locked = true;
        pressed = true;
        frame();
        assertThat(target()).isEqualTo(World.NONE);
        assertThat(seen).filteredOn(Interacted.class::isInstance).hasSize(2);
        assertThat(system.interact(player)).isFalse();
    }

    @Test
    void withoutABodyEveryDirectionCounts() {
        world.remove(player, TopDownBody.class);
        int behind = thing(-1f, 0f, "behind");
        frame();
        assertThat(target()).isEqualTo(behind);
    }
}
