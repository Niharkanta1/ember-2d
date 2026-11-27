package dev.ember2d.demo.novel;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.kit.novel.NovelCharacters;
import dev.ember2d.kit.novel.NovelDirector;
import dev.ember2d.kit.novel.NovelLine;
import dev.ember2d.kit.novel.NovelMemory;
import dev.ember2d.save.SaveData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The novel demo's chapter (I-470): both endings, the gallery unlock, read tracking, saving mid-scene, and that every
 * asset the story and the cast name is in the manifest. Reads the files directly.
 */
class ChapterTest {

    static final Path ASSETS = Path.of("assets");

    static JsonValue json(String path) throws Exception {
        return new JsonReader().parse(Files.readString(ASSETS.resolve(path)));
    }

    final NovelCharacters cast;
    final SaveData global = new SaveData();
    final NovelMemory memory = new NovelMemory(global);

    ChapterTest() throws Exception {
        cast = NovelCharacters.parse(json("cast.json"), "cast.json");
    }

    NovelDirector director() throws Exception {
        return new NovelDirector(DialogueRunner.fromJson(Files.readString(ASSETS.resolve("ink/chapter1.ink.json")),
                "chapter1.ink.json"), cast, null, memory, true);   // dev mode: a bad tag throws
    }

    /** Reads to the end, picking {@code choice}; returns the lines (name: text) and collects the game tags. */
    static List<String> read(NovelDirector d, int choice, List<String> tags) {
        List<String> lines = new ArrayList<>();
        NovelDirector.Step step;
        while ((step = d.next()).kind() != NovelDirector.Kind.END) {
            if (step.kind() == NovelDirector.Kind.CHOICES) {
                assertThat(step.choices()).hasSize(2);
                d.choose(choice);
            } else {
                lines.add(step.line().name() + ": " + step.line().text());
                tags.addAll(step.line().tags());
            }
        }
        return lines;
    }

    @Test
    void theChoiceChangesTheEndingAndOnlyTheHappyOneUnlocksTheCg() throws Exception {
        List<String> tags = new ArrayList<>();
        NovelDirector alone = director();
        List<String> lines = read(alone, 1, tags);
        assertThat(lines).last().isEqualTo(": The End. A quiet evening.");
        assertThat(tags).containsExactly("ending_alone");
        assertThat(alone.stage().background()).isEqualTo("bg.night");
        assertThat(alone.stage().characters()).containsOnlyKeys("kero");
        assertThat(alone.stage().music()).isNull();
        assertThat(memory.unlockedCgs()).isEmpty();

        tags.clear();
        NovelDirector together = director();
        lines = read(together, 0, tags);
        assertThat(lines).last().isEqualTo(": The End. Friends at sunset.");
        assertThat(tags).containsExactly("ending_together");
        assertThat(together.stage().background()).isEqualTo("bg.dusk");
        assertThat(together.stage().characters()).containsOnlyKeys("kero", "pip");
        assertThat(together.stage().characters().get("kero").expression()).isEqualTo("happy");
        assertThat(together.stage().music()).isEqualTo("music.sunset");
        assertThat(memory.unlockedCgs()).containsExactly("cg.sunset");
    }

    @Test
    void readLinesAreRememberedSoSkipCanPassThem() throws Exception {
        NovelDirector first = director();
        assertThat(first.next().line().read()).isFalse();
        NovelDirector second = director();
        assertThat(second.next().line().read()).as("read in the first play-through").isTrue();
    }

    @Test
    void savingMidSceneRestoresTheStageTheLineAndTheBacklogExactly() throws Exception {
        NovelDirector d = director();
        for (int i = 0; i < 4; i++) {
            d.next();
        }
        SaveData slot = new SaveData();
        d.save(slot);
        NovelDirector back = director();
        back.load(SaveData.parse(slot.toJson()));
        assertThat(back.stage().background()).isEqualTo("bg.meadow");
        assertThat(back.stage().music()).isEqualTo("music.meadow");
        assertThat(back.stage().characters()).containsOnlyKeys("kero", "pip");
        assertThat(back.stage().characters().get("kero").expression()).isEqualTo("surprised");
        assertThat(back.stage().characters().get("pip").position()).isEqualTo("right");
        NovelLine resume = back.resumeLine();
        assertThat(resume.name() + ": " + resume.text()).isEqualTo("Kero: Gone? The ones you picked for the festival?");
        assertThat(back.backlog()).hasSize(4);
        assertThat(back.next().line().text()).isEqualTo("All of them. I left the basket by the old tree, and now it is "
                + "empty.");
    }

    @Test
    void everyAssetTheStoryAndTheCastNameIsInTheManifest() throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonValue e = json("manifest.json").get("assets").child; e != null; e = e.next) {
            ids.add(e.getString("id"));
            assertThat(ASSETS.resolve(e.getString("path"))).as(e.getString("id")).exists();
        }
        String story = Files.readString(Path.of("..", "..", "..", "assets-src", "demo-novel", "ink", "chapter1.ink"));
        for (String line : story.split("\n")) {
            String t = line.trim();
            for (String tag : new String[] {"# bg:", "# music:", "# sfx:", "# cg:"}) {
                if (t.startsWith(tag)) {
                    String id = t.substring(tag.length()).trim().split("[ \\[]")[0];
                    assertThat(ids).as(t).contains(id);
                }
            }
        }
        String atlas = Files.readString(ASSETS.resolve("gfx/novel.atlas"));
        for (NovelCharacters.Character c : cast.all().values()) {
            for (String region : c.expressions().values()) {
                assertThat(atlas).as(region).contains("\n" + region.substring("atlas.novel/".length()) + "\n");
            }
        }
        assertThat(ids).contains(Chapter.CGS);
    }
}
