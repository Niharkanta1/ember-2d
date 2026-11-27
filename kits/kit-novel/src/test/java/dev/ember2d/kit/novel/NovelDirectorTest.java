package dev.ember2d.kit.novel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.JsonReader;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.save.SaveData;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The novel director (F-023, I-460) on stories compiled by inklecate. */
class NovelDirectorTest {

    static final NovelCharacters CAST = NovelCharacters.parse(new JsonReader().parse("""
            { "version": 1, "positions": { "far": 0.05 },
              "characters": {
                "alice": { "name": "Alice", "color": "#FF8FA3", "position": "left",
                           "expressions": { "happy": "atlas.novel/alice_happy", "sad": "atlas.novel/alice_sad" } },
                "bob": { "name": "Bob", "color": "#88CCFF", "position": "right",
                         "expressions": { "calm": "atlas.novel/bob" } } } }
            """), "cast.json");

    static String story(String name) throws Exception {
        try (InputStream in = NovelDirectorTest.class.getResourceAsStream("/ink/" + name + ".ink.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    final List<String> calls = new ArrayList<>();
    final StageListener recorder = new StageListener() {
        @Override
        public void background(String region, String transition, float seconds) {
            calls.add("bg " + region + " " + transition + " " + seconds);
        }

        @Override
        public void show(String character, String region, float x, String transition, float seconds) {
            calls.add("show " + character + " " + region + " " + x + " " + transition + " " + seconds);
        }

        @Override
        public void hide(String character, String transition, float seconds) {
            calls.add("hide " + character + " " + transition + " " + seconds);
        }

        @Override
        public void music(String id, float fade) {
            calls.add("music " + id + " " + fade);
        }

        @Override
        public void stopMusic(float fade) {
            calls.add("stopmusic " + fade);
        }

        @Override
        public void sound(String id) {
            calls.add("sfx " + id);
        }

        @Override
        public void voice(String id) {
            calls.add("voice " + id);
        }

        @Override
        public void cg(String region) {
            calls.add("cg " + region);
        }

        @Override
        public void shake(float strength) {
            calls.add("shake " + strength);
        }

        @Override
        public void flash(Color color, float seconds) {
            calls.add("flash " + color + " " + seconds);
        }

        @Override
        public void restore(StageState stage, NovelCharacters characters) {
            calls.add("restore " + stage.background() + " " + stage.characters().keySet());
        }
    };
    final SaveData global = new SaveData();
    final NovelMemory memory = new NovelMemory(global);

    NovelDirector director() throws Exception {
        return new NovelDirector(DialogueRunner.fromJson(story("novel"), "novel.ink.json"), CAST, recorder, memory, true);
    }

    @Test
    void everyTagStagesTheSceneInOrder() throws Exception {
        NovelDirector d = director();
        NovelDirector.Step s = d.next();
        assertThat(s.kind()).isEqualTo(NovelDirector.Kind.LINE);
        assertThat(s.line().speakerId()).isEqualTo("alice");
        assertThat(s.line().name()).isEqualTo("Alice");
        assertThat(s.line().color()).isEqualTo(Color.valueOf("FF8FA3"));
        assertThat(s.line().text()).isEqualTo("What a lovely evening.");
        assertThat(s.line().voice()).isEqualTo("alice_001");
        assertThat(calls).containsExactly("bg park fade 1.0", "music theme_calm 2.0",
                "show alice atlas.novel/alice_happy 0.22 fade 0.3", "voice alice_001");

        calls.clear();
        s = d.next();
        assertThat(s.line().name()).isEqualTo("Bob");
        assertThat(s.line().delay()).as("the waits add up").isEqualTo(0.75f);
        assertThat(calls).containsExactly("show bob atlas.novel/bob 0.78 slide 0.3",
                "show alice atlas.novel/alice_sad 0.22 cut 0.0");
        assertThat(d.stage().characters()).containsOnlyKeys("alice", "bob");
        assertThat(d.stage().characters().get("alice").expression()).isEqualTo("sad");

        s = d.next();
        assertThat(s.kind()).isEqualTo(NovelDirector.Kind.CHOICES);
        assertThat(s.choices()).containsExactly("Agree", "Leave");
        calls.clear();
        d.choose(0);
        s = d.next();
        assertThat(s.line().name()).as("an unknown speaker is shown as written").isEqualTo("Narrator");
        assertThat(s.line().speakerId()).isNull();
        assertThat(calls).containsExactly("sfx door_knock", "shake 0.6", "flash ff0000ff 0.3");

        calls.clear();
        s = d.next();
        assertThat(s.line().text()).isEqualTo("The end.");
        assertThat(s.line().name()).as("narration").isEmpty();
        assertThat(s.line().tags()).as("game tags are passed on").containsExactly("mood:quiet", "chapter_end");
        assertThat(calls).containsExactly("cg sunset", "cg null");
        assertThat(memory.unlockedCgs()).containsExactly("sunset");
        assertThat(d.next().kind()).isEqualTo(NovelDirector.Kind.END);
        assertThat(d.backlog()).extracting(NovelDirector.Logged::text).containsExactly("What a lovely evening.",
                "It is.", "Someone knocks.", "The end.");
    }

    @Test
    void theOtherBranchHidesAndStopsTheMusic() throws Exception {
        NovelDirector d = director();
        d.next();
        d.next();
        d.next();
        calls.clear();
        d.choose(1);
        assertThat(d.next().line().text()).isEqualTo("Goodbye then.");
        assertThat(calls).containsExactly("hide bob cut 0.3", "stopmusic 1.0");
        assertThat(d.stage().music()).isNull();
        assertThat(d.stage().characters()).containsOnlyKeys("alice");
    }

    @Test
    void savingMidSceneRestoresTheStageAndThePosition() throws Exception {
        NovelDirector d = director();
        d.next();
        d.next();
        SaveData slot = new SaveData();
        d.save(slot);

        calls.clear();
        NovelDirector back = director();
        back.load(SaveData.parse(slot.toJson()));
        assertThat(calls).containsExactly("restore park [alice, bob]");
        assertThat(back.stage().music()).isEqualTo("theme_calm");
        assertThat(back.stage().characters().get("alice").expression()).isEqualTo("sad");
        assertThat(back.stage().characters().get("bob").position()).isEqualTo("right");
        NovelLine resume = back.resumeLine();
        assertThat(resume.text()).as("the line on screen is shown again").isEqualTo("It is.");
        assertThat(resume.name()).isEqualTo("Bob");
        assertThat(resume.color()).isEqualTo(Color.valueOf("88CCFF"));
        assertThat(back.resumeLine()).as("only once").isNull();
        assertThat(back.backlog()).extracting(NovelDirector.Logged::text).containsExactly("What a lovely evening.",
                "It is.");
        assertThat(back.backlog().get(0).voice()).isEqualTo("alice_001");
        assertThat(back.next().choices()).containsExactly("Agree", "Leave");
    }

    @Test
    void readLinesAreRememberedAcrossPlayThroughs() throws Exception {
        NovelDirector first = director();
        assertThat(first.next().line().read()).isFalse();
        assertThat(first.next().line().read()).isFalse();
        NovelDirector second = director();
        assertThat(second.next().line().read()).as("skip mode may pass it").isTrue();
        assertThat(second.next().line().read()).as("seen in the first run").isTrue();
        second.next();
        second.choose(1);
        assertThat(second.next().line().read()).as("this branch is new").isFalse();
    }

    @Test
    void theBacklogKeepsTheLastHundredLines() throws Exception {
        StringBuilder ink = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            ink.append("Line ").append(i).append(".\n");
        }
        // compiled story with 120 lines is built from the runtime's JSON format through a tiny hand-made story
        NovelDirector d = new NovelDirector(DialogueRunner.fromJson(manyLines(120), "many"), CAST, null, null, true);
        while (d.next().kind() == NovelDirector.Kind.LINE) {
            // read them all
        }
        assertThat(d.backlog()).hasSize(NovelDirector.BACKLOG);
        assertThat(d.backlog().get(0).text()).isEqualTo("Line 20.");
    }

    /** A compiled Ink story of plain lines (the JSON format inklecate writes for text-only content). */
    static String manyLines(int n) {
        StringBuilder root = new StringBuilder("{\"inkVersion\":21,\"root\":[[");
        for (int i = 0; i < n; i++) {
            root.append("\"^Line ").append(i).append(".\",\"\\n\",");
        }
        root.append("\"end\",[\"done\",{\"#n\":\"g-0\"}],null],\"done\",null],\"listDefs\":{}}");
        return root.toString();
    }

    @Test
    void badTagsAreReportedWithTheStoryAndLine() throws Exception {
        NovelDirector dev = new NovelDirector(DialogueRunner.fromJson(story("bad"), "bad.ink.json"), CAST, null, null,
                true);
        assertThatThrownBy(dev::next).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Story bad.ink.json, line \"Hello.\": unknown staging tag \"dance:\"");
        NovelDirector release = new NovelDirector(DialogueRunner.fromJson(story("bad"), "bad.ink.json"), CAST, null,
                null, false);
        assertThat(release.next().line().text()).as("logged and skipped").isEqualTo("Hello.");
    }

    @Test
    void theCastParsesAndReportsProblems() {
        assertThat(CAST.forSpeaker("ALICE").id()).isEqualTo("alice");
        assertThat(CAST.forSpeaker("Bob").name()).isEqualTo("Bob");
        assertThat(CAST.forSpeaker("Eve")).isNull();
        assertThat(CAST.position("far")).isEqualTo(0.05f);
        assertThat(CAST.position("center")).isEqualTo(0.5f);
        assertThatThrownBy(() -> NovelCharacters.parse(new JsonReader().parse("""
                { "version": 1, "characters": { "x": { "color": "pink", "position": "up", "default": "a",
                  "age": 3, "expressions": { "b": "r" } } } }"""), "c.json"))
                .hasMessageContaining("c.json: character \"x\": unknown field \"age\"")
                .hasMessageContaining("\"color\" must be").hasMessageContaining("default expression \"a\"")
                .hasMessageContaining("unknown position \"up\"");
    }

    @Test
    void tagOptionsParse() {
        NovelTag t = NovelTag.parse("bg: park [transition=fade 1.5]");
        assertThat(t.args).containsExactly("park");
        assertThat(t.options).containsEntry("transition", "fade").containsEntry("time", "1.5");
        assertThat(NovelTag.parse("speaker:guard")).isNull();
        assertThat(NovelTag.parse("chapter_end")).isNull();
        NovelTag stop = NovelTag.parse("stopmusic fade=2");
        assertThat(stop.name).as("options without a colon").isEqualTo("stopmusic");
        assertThat(stop.options).containsEntry("fade", "2");
        assertThat(NovelTag.parse("hidecg").name).isEqualTo("hidecg");
        assertThat(NovelTag.parse("chapter end")).as("a plain tag with a space").isNull();
        assertThatThrownBy(() -> NovelTag.parse("bg: park fade=x").number("fade", 0f))
                .hasMessageContaining("\"x\" is not a number");
    }
}
