package dev.ember2d.kit.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.save.SaveData;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The dialogue runner (F-021, I-420) on a story compiled by inklecate (ink/test.ink). */
class DialogueRunnerTest {

    static String json() throws Exception {
        try (InputStream in = DialogueRunnerTest.class.getResourceAsStream("/ink/test.ink.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    final List<String> given = new ArrayList<>();

    DialogueRunner runner() throws Exception {
        DialogueRunner r = DialogueRunner.fromJson(json(), "test.ink.json");
        r.bind("give", args -> {
            given.add((String) args[0]);
            return true;
        });
        return r;
    }

    @Test
    void linesSpeakersTagsAndChoices() throws Exception {
        assertThat(DialogueRunner.fromJson("﻿" + json(), "bom.json").canContinue())
                .as("a leading byte-order mark is stripped").isTrue();
        DialogueRunner r = runner();
        assertThat(r.canContinue()).isTrue();
        assertThat(r.next()).isEqualTo("Guard: Halt! Who goes there?");
        assertThat(r.speaker()).as("from the tag").isEqualTo("guard");
        assertThat(r.text()).isEqualTo("Halt! Who goes there?");
        assertThat(r.tags()).containsExactly("speaker:guard", "mood:stern");
        assertThat(r.tag("mood")).isEqualTo("stern");
        assertThat(r.tag("pose")).isNull();
        assertThat(r.canContinue()).isFalse();
        assertThat(r.choices()).containsExactly("I'm a traveller.", "Bribe him (3 gold)");
        assertThatThrownBy(() -> r.choose(2)).isInstanceOf(IndexOutOfBoundsException.class)
                .hasMessage("choice 2 (the story offers 2)");

        r.choose(1);
        assertThat(r.next()).isEqualTo("Guard: Very well...");
        assertThat(r.speaker()).as("from the prefix").isEqualTo("Guard");
        assertThat(r.tag("mood")).isEqualTo("greedy");
        assertThat(r.next()).isEqualTo("You have 1 gold left.");
        assertThat(r.speaker()).isEmpty();
        assertThat(r.isFinished()).isTrue();
        assertThatThrownBy(r::next).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void variablesFunctionsAndKnots() throws Exception {
        DialogueRunner r = runner();
        assertThat(r.variable("gold")).isEqualTo(3);
        r.setVariable("gold", 10);
        r.next();
        assertThat(r.choices()).contains("Bribe him (10 gold)");
        r.choose(0);
        assertThat(r.next()).isEqualTo("Guard: Hello, traveller. You may pass.");
        assertThat(r.variable("met_guard")).isEqualTo(true);
        assertThatThrownBy(() -> r.variable("silver")).hasMessageContaining("no variable \"silver\"");

        r.jumpTo("elder");
        assertThat(r.next()).isEqualTo("Elder: Take this, you will need it.");
        assertThat(r.speaker()).isEqualTo("elder");
        while (r.canContinue()) {
            r.next();
        }
        assertThat(given).as("the bound function replaced the Ink fallback").containsExactly("potion");
        assertThatThrownBy(() -> r.jumpTo("wizard")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no knot or stitch \"wizard\"");
    }

    @Test
    void stateRoundTripsThroughASaveSlot() throws Exception {
        DialogueRunner r = runner();
        r.setVariable("gold", 7);   // before the choices are made: their texts use it
        r.next();
        SaveData slot = new SaveData();
        r.save(slot);

        DialogueRunner back = runner();
        back.load(SaveData.parse(slot.toJson()));
        assertThat(back.choices()).containsExactly("I'm a traveller.", "Bribe him (7 gold)");
        back.choose(1);
        back.next();
        assertThat(back.next()).isEqualTo("You have 5 gold left.");
        back.load(new SaveData());   // nothing stored: unchanged
        assertThat(back.isFinished()).isTrue();
        assertThatThrownBy(() -> back.loadState("{ nope")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void badStoriesAreReported() {
        assertThatThrownBy(() -> DialogueRunner.fromJson("{ \"not\": \"ink\" }", "bad.json"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("Ink story bad.json cannot be loaded");
        // fromAsset goes through libGDX JSON; the round trip keeps the story intact
        assertThat(new JsonReader().parse(stripBom()).toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json))
                .contains("inkVersion");
    }

    static String stripBom() {
        try {
            String j = json();
            return j.startsWith("﻿") ? j.substring(1) : j;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
