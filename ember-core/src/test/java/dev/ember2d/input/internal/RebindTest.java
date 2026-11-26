package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.input.InputBindings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Player rebinding (F-013, I-230): overrides over the bindings file. */
class RebindTest {

    static InputBindings bindings() {
        return InputBindings.parse(new JsonReader().parse("""
                { "version": 1,
                  "contexts": { "gameplay": { "actions": { "jump": ["SPACE"], "fire": ["Z"] } },
                                "ui": { "actions": { "ui_back": ["ESCAPE"] } } } }
                """), "input.json");
    }

    final DefaultInput input = new DefaultInput(new TimeState(1f / 60f), new DefaultEventBus(false));

    boolean press(int key) {
        input.source().keyDown(key);
        input.beginFrame();
        boolean jumped = input.pressed("jump");
        input.source().keyUp(key);
        input.beginFrame();
        return jumped;
    }

    @Test
    void aRebindChangesOnlyThatActionAndKeepsTheContextStack() {
        input.setBindings(bindings());
        input.pushContext("ui");
        input.rebindAction("jump", List.of("W", "UP", "W"));
        assertThat(input.topContext()).isEqualTo("ui");
        assertThat(input.actionInputs("jump")).containsExactly("W", "UP");
        assertThat(input.actionInputs("fire")).containsExactly("Z");
        input.popContext();
        assertThat(press(Keys.W)).isTrue();
        assertThat(press(Keys.SPACE)).isFalse();
        assertThat(input.bindingOverrides()).isEqualTo(Map.of("jump", List.of("W", "UP")));

        input.setBindings(bindings());   // a hot reload of the file keeps the override
        assertThat(press(Keys.W)).isTrue();

        input.resetBinding("jump");
        assertThat(press(Keys.SPACE)).isTrue();
        assertThat(input.bindingOverrides()).isEmpty();
    }

    @Test
    void badRebindsAreRejected() {
        input.setBindings(bindings());
        assertThatThrownBy(() -> input.rebindAction("fly", List.of("W"))).hasMessageContaining("Unknown action");
        assertThatThrownBy(() -> input.rebindAction("jump", List.of("SPAAACE")))
                .hasMessage("jump: unknown input name \"SPAAACE\"");
        assertThatThrownBy(() -> input.rebindAction("jump", List.of())).hasMessage("jump: needs at least one input");
        assertThat(input.bindingOverrides()).isEmpty();
    }
}
