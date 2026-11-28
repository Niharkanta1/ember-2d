package dev.ember2d.tools.assets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AssetToolsTest {

    private static final Path REPO = Path.of(System.getProperty("ember.repo", "../.."));

    @TempDir
    Path dir;

    // ------------------------------------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"demo-platformer", "demo-shooter", "demo-rpg", "demo-novel"})
    void theDemosManifestsAreAlreadyComplete(String demo) throws IOException {
        Path assets = copy(REPO.resolve("demos/" + demo + "/core/assets"));
        byte[] before = Files.readAllBytes(assets.resolve("manifest.json"));
        ManifestTool.Result result = ManifestTool.update(assets, false);
        assertThat(result.added()).as("added").isEmpty();
        assertThat(result.missing()).as("missing").isEmpty();
        assertThat(result.written()).isFalse();
        assertThat(Files.readAllBytes(assets.resolve("manifest.json"))).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo-platformer", "demo-shooter", "demo-rpg", "demo-novel"})
    void aManifestBuiltFromNothingHasTheDemosTypesForEveryPath(String demo) throws IOException {
        Path assets = copy(REPO.resolve("demos/" + demo + "/core/assets"));
        Map<String, JsonValue> handWritten = byPath(assets);
        Files.delete(assets.resolve("manifest.json"));
        ManifestTool.update(assets, false);
        Map<String, JsonValue> generated = byPath(assets);
        assertThat(handWritten.keySet()).containsAll(generated.keySet());
        for (Map.Entry<String, JsonValue> e : generated.entrySet()) {
            assertThat(e.getValue().getString("type")).as(e.getKey())
                    .isEqualTo(handWritten.get(e.getKey()).getString("type"));
        }
        // The only files left out are images another asset already loads (atlas pages, map image layers), which
        // some demos also use as separate textures; those stay in a manifest once they are added by hand.
        java.util.Set<String> skipped = ManifestTool.loadedByOthers(assets, ManifestTool.files(assets));
        for (String path : handWritten.keySet()) {
            if (!generated.containsKey(path)) {
                assertThat(skipped).as(path).contains(path);
            }
        }
        // The demos' own naming conventions come back for most entries.
        assertThat(generated.get(firstPathOfType(handWritten, "prefab")).getString("id"))
                .isEqualTo(handWritten.get(firstPathOfType(handWritten, "prefab")).getString("id"));
        assertThat(generated.get("input.json").getString("id")).isEqualTo("input");
    }

    @Test
    void newFilesGetTypesIdsAndGroups() throws IOException {
        write("manifest.json", "{ \"version\": 1, \"assets\": [\n  {\"id\": \"input\", \"path\": \"input.json\", "
                + "\"type\": \"json\", \"group\": \"global\"}\n] }\n");
        write("input.json", "{}");
        image("gfx/hero/Idle Strip.png", 64, 16);
        image("bg/forest.png", 8, 8);
        write("sfx/jump.wav", "x");
        write("music/theme.ogg", "x");
        write("maps/level1.tmx", "<map><tileset source=\"tiles.tsx\"/></map>");
        write("maps/tiles.tsx", "<tileset><image source=\"tiles.png\" width=\"8\" height=\"8\"/></tileset>");
        image("maps/tiles.png", 8, 8);
        write("gfx/ui.atlas", "\nui.png\nsize: 8,8\nbutton\n  bounds: 0,0,8,8\n");
        image("gfx/ui.png", 8, 8);
        write("fx/spark.p", "spark\n- Image Paths -\nC:\\work\\dot.png\n");
        image("fx/dot.png", 2, 2);
        write("fonts/Pixel.ttf", "x");
        write("fonts/OFL.txt", "licence");
        write("fonts/tiny.fnt", "info face=\"tiny\"\ncommon lineHeight=9\npage id=0 file=\"tiny.png\"\n");
        image("fonts/tiny.png", 8, 8);
        write("prefabs/enemy/bat.json", "{}");
        write("anim/bat.clips.json", "{}");
        write("anim/bat.asm.json", "{}");
        write("ink/intro.ink.json", "{}");
        write("weapons/bow.json", "{}");
        write("i18n/de.json", "{}");
        write("ink/intro.de.ink.json", "{}");
        write(".hidden/x.png", "x");

        ManifestTool.Result result = ManifestTool.update(dir, false);
        Map<String, JsonValue> m = byPath(dir);
        assertThat(m.keySet()).containsExactlyInAnyOrder("input.json", "gfx/hero/Idle Strip.png", "bg/forest.png",
                "sfx/jump.wav", "music/theme.ogg", "maps/level1.tmx", "gfx/ui.atlas", "fx/spark.p", "fonts/Pixel.ttf",
                "prefabs/enemy/bat.json", "anim/bat.clips.json", "anim/bat.asm.json", "ink/intro.ink.json",
                "weapons/bow.json", "i18n/de.json", "ink/intro.de.ink.json", "fonts/tiny.fnt");
        assertEntry(m, "fonts/tiny.fnt", "font.tiny", "font");
        assertEntry(m, "gfx/hero/Idle Strip.png", "tex.hero_idle_strip", "texture");
        assertEntry(m, "bg/forest.png", "bg.forest", "texture");
        assertEntry(m, "sfx/jump.wav", "sfx.jump", "sound");
        assertEntry(m, "music/theme.ogg", "music.theme", "music");
        assertEntry(m, "maps/level1.tmx", "map.level1", "map");
        assertThat(m.get("maps/level1.tmx").getString("group")).isEqualTo("level1");
        assertEntry(m, "gfx/ui.atlas", "atlas.ui", "atlas");
        assertEntry(m, "fx/spark.p", "fx.spark", "particle");
        assertEntry(m, "fonts/Pixel.ttf", "font.pixel", "freetype");
        assertThat(m.get("fonts/Pixel.ttf").get("font").getInt("size")).isEqualTo(16);
        assertEntry(m, "prefabs/enemy/bat.json", "enemy/bat", "prefab");
        assertEntry(m, "anim/bat.clips.json", "anim.bat", "clips");
        assertEntry(m, "anim/bat.asm.json", "asm.bat", "asm");
        assertEntry(m, "ink/intro.ink.json", "ink.intro", "json");
        assertEntry(m, "weapons/bow.json", "weapon.bow", "json");
        assertEntry(m, "i18n/de.json", "strings.de", "json");
        assertEntry(m, "ink/intro.de.ink.json", "ink.intro.de", "json");
        assertThat(result.added()).hasSize(16);

        // Running again changes nothing.
        assertThat(ManifestTool.update(dir, false).written()).isFalse();
    }

    @Test
    void existingEntriesAreKeptAndMissingFilesReportedOrPruned() throws IOException {
        write("manifest.json", "{ \"version\": 1, \"assets\": [\n"
                + "  {\"id\": \"font.big\", \"path\": \"fonts/a.ttf\", \"type\": \"freetype\", \"group\": \"menu\", "
                + "\"font\": {\"size\": 24, \"shadowColor\": \"#000000A0\", \"scale\": 0.5}},\n"
                + "  {\"id\": \"tex.gone\", \"path\": \"gfx/gone.png\", \"type\": \"texture\", \"group\": \"global\"}\n"
                + "] }\n");
        write("fonts/a.ttf", "x");
        image("gfx/new.png", 4, 4);

        ManifestTool.Result first = ManifestTool.update(dir, false);
        assertThat(first.missing()).containsExactly("tex.gone (gfx/gone.png)");
        assertThat(first.added()).containsExactly("tex.new (gfx/new.png, texture)");
        String text = Files.readString(dir.resolve("manifest.json"));
        assertThat(text).contains("{\"id\": \"font.big\", \"path\": \"fonts/a.ttf\", \"type\": \"freetype\", "
                + "\"group\": \"menu\", \"font\": {\"size\": 24, \"shadowColor\": \"#000000A0\", \"scale\": 0.5}}");
        assertThat(text).contains("tex.gone");

        ManifestTool.Result pruned = ManifestTool.update(dir, true);
        assertThat(pruned.removed()).containsExactly("tex.gone (gfx/gone.png)");
        assertThat(Files.readString(dir.resolve("manifest.json"))).doesNotContain("tex.gone");
    }

    @Test
    void takenIdsGetANumber() throws IOException {
        image("gfx/a/b.png", 4, 4);
        image("gfx/a_b.png", 4, 4);
        ManifestTool.update(dir, false);
        Map<String, JsonValue> m = byPath(dir);
        assertThat(List.of(m.get("gfx/a/b.png").getString("id"), m.get("gfx/a_b.png").getString("id")))
                .containsExactlyInAnyOrder("tex.a_b", "tex.a_b_2");
    }

    @Test
    void aBrokenManifestIsNotOverwritten() throws IOException {
        write("manifest.json", "{ broken");
        image("gfx/a.png", 4, 4);
        assertThatThrownBy(() -> ManifestTool.update(dir, false)).isInstanceOf(IOException.class)
                .hasMessageContaining("cannot be read");
        assertThat(Files.readString(dir.resolve("manifest.json"))).isEqualTo("{ broken");
    }

    // ------------------------------------------------------------------------------------------------
    // Clips
    // ------------------------------------------------------------------------------------------------

    @Test
    void stripsMakeTheSameClipsAsTheHandWrittenFrogSet() throws IOException {
        Path assets = copy(REPO.resolve("demos/demo-platformer/core/assets"));
        ClipsTool.Result r = ClipsTool.fromFolder(assets, "gfx/frog", "frog2", null, 0.05f);
        assertThat(r.file()).isEqualTo("anim/frog2.clips.json");
        assertThat(r.clips()).containsExactly("fall", "hit", "idle", "jump", "run");

        JsonValue mine = json(assets.resolve(r.file())).get("clips");
        JsonValue theirs = json(assets.resolve("anim/frog.clips.json")).get("clips");
        for (String clip : r.clips()) {
            JsonValue a = mine.get(clip);
            JsonValue b = theirs.get(clip);
            assertThat(a.getString("sheet")).as(clip).isEqualTo(b.getString("sheet"));
            assertThat(a.getInt("frameWidth")).isEqualTo(b.getInt("frameWidth"));
            assertThat(a.getInt("frameHeight")).isEqualTo(b.getInt("frameHeight"));
            assertThat(a.get("frames").asIntArray()).isEqualTo(b.get("frames").asIntArray());
        }
        assertThat(json(assets.resolve(r.file())).get("defaults").getFloat("frameDuration")).isEqualTo(0.05f);
        assertThat(byPath(assets).get("anim/frog2.clips.json").getString("id")).isEqualTo("anim.frog2");
    }

    @Test
    void frameSizesComeFromTheNameTheOptionOrTheHeight() throws IOException {
        image("gfx/bat/Fly (46x30).png", 46 * 7, 30);
        image("gfx/bat/Idle.png", 16 * 4, 16);
        image("gfx/bat/Big Grid.png", 64, 64);
        ClipsTool.Result r = ClipsTool.fromFolder(dir, "gfx/bat", null, null, 0.1f);
        assertThat(r.file()).isEqualTo("anim/bat.clips.json");
        JsonValue clips = json(dir.resolve(r.file())).get("clips");
        assertThat(clips.get("fly").getInt("frameWidth")).isEqualTo(46);
        assertThat(clips.get("fly").get("frames").asIntArray()).containsExactly(0, 6);
        assertThat(clips.get("idle").get("frames").asIntArray()).containsExactly(0, 3);
        assertThat(clips.get("big_grid").get("frames").asIntArray()).containsExactly(0, 0);
        assertThat(clips.get("fly").getString("sheet")).isEqualTo("tex.bat_fly");

        ClipsTool.Result grid = ClipsTool.fromFolder(dir, "gfx/bat", "bat_small", new int[] {16, 16}, 0.1f);
        assertThat(json(dir.resolve(grid.file())).get("clips").get("big_grid").get("frames").asIntArray())
                .containsExactly(0, 15);
    }

    @Test
    void badSizesAndExistingFilesAreRefusedBeforeWriting() throws IOException {
        image("gfx/x/Odd (10x10).png", 25, 10);
        assertThatThrownBy(() -> ClipsTool.fromFolder(dir, "gfx/x", null, null, 0.1f))
                .isInstanceOf(IOException.class).hasMessageContaining("not a whole number of 10x10 frames");
        assertThat(dir.resolve("manifest.json")).doesNotExist();

        image("gfx/y/Run.png", 32, 16);
        ClipsTool.fromFolder(dir, "gfx/y", null, null, 0.1f);
        assertThatThrownBy(() -> ClipsTool.fromFolder(dir, "gfx/y", null, null, 0.1f))
                .isInstanceOf(IOException.class).hasMessageContaining("already exists");
    }

    @Test
    void anAsepriteExportMakesOneClipPerTag() throws IOException {
        Path test = REPO.resolve("ember-core/src/test/resources/assets-test/anim");
        Files.createDirectories(dir.resolve("anim"));
        Files.copy(test.resolve("hero-array.json"), dir.resolve("anim/hero.json"));
        Files.copy(test.resolve("hero.png"), dir.resolve("anim/hero.png"));
        String image = json(dir.resolve("anim/hero.json")).get("meta").getString("image");
        if (!image.equals("hero.png")) {
            Files.move(dir.resolve("anim/hero.png"), dir.resolve("anim").resolve(image));
        }
        ClipsTool.Result r = ClipsTool.fromAseprite(dir, "anim/hero.json", null);
        assertThat(r.file()).isEqualTo("anim/hero.clips.json");
        List<String> tags = new ArrayList<>();
        for (JsonValue t = json(dir.resolve("anim/hero.json")).get("meta").get("frameTags").child; t != null;
                t = t.next) {
            tags.add(ClipsTool.clean(t.getString("name")));
        }
        assertThat(r.clips()).containsExactlyElementsOf(tags);
        JsonValue first = json(dir.resolve(r.file())).get("clips").child;
        Map<String, JsonValue> m = byPath(dir);
        assertThat(first.getString("aseprite")).isEqualTo(m.get("anim/hero.json").getString("id"));
        assertThat(first.getString("texture")).isEqualTo(m.get("anim/" + image).getString("id"));
        assertThat(m.get("anim/hero.json").getString("type")).isEqualTo("json");
    }

    @Test
    void clipNamesAreCleanedFileNames() {
        assertThat(ClipsTool.clipName("Double Jump (32x32).png")).isEqualTo("double_jump");
        assertThat(ClipsTool.clipName("wall-slide.png")).isEqualTo("wall_slide");
        assertThat(AssetTools.frame("32x16")).containsExactly(32, 16);
    }

    // ------------------------------------------------------------------------------------------------

    private Path copy(Path from) throws IOException {
        Path to = dir.resolve("assets");
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path t = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    Files.copy(p, t);
                }
            }
        }
        return to;
    }

    private void write(String path, String content) throws IOException {
        Path p = dir.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }

    private void image(String path, int w, int h) throws IOException {
        Path p = dir.resolve(path);
        Files.createDirectories(p.getParent());
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", p.toFile());
    }

    private static JsonValue json(Path file) throws IOException {
        return new JsonReader().parse(Files.readString(file, StandardCharsets.UTF_8));
    }

    private static Map<String, JsonValue> byPath(Path assets) throws IOException {
        Map<String, JsonValue> out = new java.util.LinkedHashMap<>();
        for (JsonValue e = json(assets.resolve("manifest.json")).get("assets").child; e != null; e = e.next) {
            out.put(e.getString("path"), e);
        }
        return out;
    }

    private static String firstPathOfType(Map<String, JsonValue> m, String type) {
        return m.entrySet().stream().filter(e -> type.equals(e.getValue().getString("type"))).map(Map.Entry::getKey)
                .findFirst().orElse("input.json");
    }

    private static void assertEntry(Map<String, JsonValue> m, String path, String id, String type) {
        assertThat(m.get(path)).as(path).isNotNull();
        assertThat(m.get(path).getString("id")).as(path).isEqualTo(id);
        assertThat(m.get(path).getString("type")).as(path).isEqualTo(type);
    }
}
