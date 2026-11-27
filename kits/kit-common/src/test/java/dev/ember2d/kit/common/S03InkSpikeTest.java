package dev.ember2d.kit.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.bladecoder.ink.runtime.Choice;
import com.bladecoder.ink.runtime.Story;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spike S-03: blade-ink runs an inklecate-compiled story, and its state survives a save and restore. */
class S03InkSpikeTest {

    static String json() throws Exception {
        try (InputStream in = S03InkSpikeTest.class.getResourceAsStream("/ink/s03.ink.json")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.startsWith("﻿") ? text.substring(1) : text;   // inklecate writes a UTF-8 BOM
        }
    }

    static Story story() throws Exception {
        Story story = new Story(json());
        story.bindExternalFunction("greet", args -> "Hi, " + args[0] + "!");
        return story;
    }

    @Test
    void runsLinesTagsChoicesVariablesAndExternalFunctions() throws Exception {
        Story story = story();
        assertThat(story.Continue()).isEqualTo("Guard: Halt! Who goes there?\n");
        assertThat(story.getCurrentTags()).containsExactly("speaker:guard", "mood:stern");
        List<String> choices = new ArrayList<>();
        for (Choice c : story.getCurrentChoices()) {
            choices.add(c.getText());
        }
        assertThat(choices).containsExactly("I'm a traveller.", "Bribe him (3 gold)");
        story.chooseChoiceIndex(0);
        assertThat(story.continueMaximally()).contains("Guard: Hi, traveller! You may pass.", "You have 3 gold left.");
        assertThat(story.getVariablesState().get("met_guard")).isEqualTo(true);
        assertThat(story.canContinue()).isFalse();
    }

    @Test
    void stateRoundTripsThroughJson() throws Exception {
        Story story = story();
        story.Continue();
        String saved = story.getState().toJson();   // saved at the choice

        Story restored = story();
        restored.getState().loadJson(saved);
        assertThat(restored.getCurrentChoices()).hasSize(2);
        restored.chooseChoiceIndex(1);
        String rest = restored.continueMaximally();
        assertThat(rest).contains("Very well...", "You have 1 gold left.");
        assertThat(restored.getCurrentTags()).isEmpty();
        assertThat(restored.getVariablesState().get("gold")).isEqualTo(1);
        System.out.println("S-03 saved state: " + saved.length() + " chars");
    }
}
