package dev.ember2d.input.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class InputNamesTest {

    @Test
    void commonNamesMapToLibgdxCodes() {
        assertThat(InputNames.code("SPACE")).isEqualTo(Keys.SPACE);
        assertThat(InputNames.code("A")).isEqualTo(Keys.A);
        assertThat(InputNames.code("LEFT")).isEqualTo(Keys.LEFT);
        assertThat(InputNames.code("L_SHIFT")).isEqualTo(Keys.SHIFT_LEFT);
        assertThat(InputNames.code("ESCAPE")).isEqualTo(Keys.ESCAPE);
        assertThat(InputNames.code("ENTER")).isEqualTo(Keys.ENTER);
        assertThat(InputNames.code("BACK")).isEqualTo(Keys.BACK);
        assertThat(InputNames.code("F1")).isEqualTo(Keys.F1);
        assertThat(InputNames.code("1")).isEqualTo(Keys.NUM_1);
        assertThat(InputNames.code("NUMPAD_1")).isEqualTo(Keys.NUMPAD_1);
        assertThat(InputNames.code("MOUSE_LEFT")).isEqualTo(InputNames.MOUSE_BASE + Buttons.LEFT);
        assertThat(InputNames.code("MOUSE_FORWARD")).isEqualTo(InputNames.MOUSE_BASE + Buttons.FORWARD);
        assertThat(InputNames.code("TAP")).isEqualTo(InputNames.TAP);
        assertThat(InputNames.code("SWIPE_DOWN")).isEqualTo(InputNames.SWIPE_DOWN);
        assertThat(InputNames.name(InputNames.VIRTUAL_BASE - 1)).isEqualTo("SWIPE_DOWN");
        assertThat(InputNames.name(InputNames.VIRTUAL_BASE)).as("on-screen buttons have no bindable name").isNull();
    }

    @Test
    void unknownOrWrongCaseNamesAreRejected() {
        assertThat(InputNames.code("SPCE")).isEqualTo(-1);
        assertThat(InputNames.code("space")).isEqualTo(-1);
        assertThat(InputNames.code(null)).isEqualTo(-1);
    }

    @Test
    void everyNameRoundTripsAndIsUnique() {
        List<String> all = InputNames.all();
        assertThat(all).hasSizeGreaterThan(100);
        assertThat(new HashSet<>(all)).hasSameSizeAs(all);
        for (String name : all) {
            assertThat(InputNames.name(InputNames.code(name))).as(name).isEqualTo(name);
            assertThat(name).as("upper case, no spaces or dashes").doesNotContain(" ", "-")
                    .isEqualTo(name.toUpperCase(Locale.ROOT));
        }
    }
}
