package dev.ember2d.demo.gold;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.ui.RebindMenu;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The screens' data (F-029, I-650): the level-complete clock, the rebindable controls and the difficulty cycle. */
class ScreensTest {

    @Test
    void theClockShowsMinutesAndSeconds() {
        assertThat(LevelScene.clock(0f)).isEqualTo("0:00");
        assertThat(LevelScene.clock(83.7f)).isEqualTo("1:23");
        assertThat(LevelScene.clock(600f)).isEqualTo("10:00");
    }

    @Test
    void everyRebindableControlIsBoundInGameplay() throws IOException {
        JsonValue gameplay = new JsonReader().parse(Files.readString(Path.of("assets/input.json")))
                .get("contexts").get("gameplay");
        for (RebindMenu.Entry entry : GoldUi.CONTROLS) {
            if (entry.action() != null) {
                assertThat(gameplay.get("actions").has(entry.action())).as(entry.action()).isTrue();
            } else {
                assertThat(gameplay.get("axes").get(entry.axis()).has(entry.direction()))
                        .as(entry.axis() + "." + entry.direction()).isTrue();
            }
        }
    }

    @Test
    void difficultyCyclesThroughTheOriginalsThreeLevels() {
        assertThat(Difficulty.EASY.next()).isEqualTo(Difficulty.NORMAL);
        assertThat(Difficulty.NORMAL.next()).isEqualTo(Difficulty.HARD);
        assertThat(Difficulty.HARD.next()).isEqualTo(Difficulty.EASY);
    }
}
