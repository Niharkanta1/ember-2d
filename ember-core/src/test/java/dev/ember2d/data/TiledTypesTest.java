package dev.ember2d.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.ecs.EntityRef;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The Tiled project type generator (F-009, I-245). */
class TiledTypesTest {

    public enum Mood { CALM, ANGRY }

    public static class Stats {
        public int hp = 3;
        public long big = 7;
        public float speed = 1.5f;
        public double weight = 2;
        public boolean alive = true;
        public String label;
        public Mood mood = Mood.ANGRY;
        public final Color tint = new Color(1f, 0f, 0f, 0.5f);
        public final Vector2 home = new Vector2();
        public EntityRef target;
        public float[] path;
    }

    public static class Tag {
        public String text = "hi";
    }

    public static class Marker {
        public Vector2 at = new Vector2();
    }

    /** For the main() test. */
    public static class Setup implements ComponentSetup {
        @Override
        public void register(ComponentRegistry r) {
            r.register(Stats.class, Stats::new).register(Tag.class, Tag::new).register(Marker.class, Marker::new);
        }
    }

    final ComponentRegistry registry = new ComponentRegistry();
    final List<String> warnings = new ArrayList<>();

    TiledTypesTest() {
        new Setup().register(registry);
    }

    static JsonValue json(String text) {
        return new JsonReader().parse(text);
    }

    static JsonValue type(JsonValue project, String name) {
        for (JsonValue t = project.get("propertyTypes").child; t != null; t = t.next) {
            if (name.equals(t.getString("name"))) {
                return t;
            }
        }
        throw new AssertionError("no type " + name);
    }

    static List<String> names(JsonValue type) {
        List<String> out = new ArrayList<>();
        for (JsonValue m = type.get("members").child; m != null; m = m.next) {
            out.add(m.getString("name"));
        }
        return out;
    }

    static JsonValue member(JsonValue type, String name) {
        for (JsonValue m = type.get("members").child; m != null; m = m.next) {
            if (name.equals(m.getString("name"))) {
                return m;
            }
        }
        throw new AssertionError("no member " + name);
    }

    @Test
    void everyFieldKindBecomesAMemberWithItsDefault() {
        JsonValue project = json(TiledTypes.update(registry, Map.of(), null, warnings));
        assertThat(project.getString("extensionsPath")).isEqualTo("extensions");
        JsonValue stats = type(project, "Stats");
        assertThat(stats.get("useAs").asStringArray()).containsExactly("property");
        assertThat(member(stats, "hp").toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json))
                .isEqualTo("{\"name\":\"hp\",\"type\":\"int\",\"value\":3}");
        assertThat(member(stats, "big").getLong("value")).isEqualTo(7);
        assertThat(member(stats, "speed").getString("type")).isEqualTo("float");
        assertThat(member(stats, "speed").getFloat("value")).isEqualTo(1.5f);
        assertThat(member(stats, "weight").getFloat("value")).isEqualTo(2f);
        assertThat(member(stats, "alive").getString("type")).isEqualTo("bool");
        assertThat(member(stats, "alive").getBoolean("value")).isTrue();
        assertThat(names(stats)).as("null on a new instance, or set by object references only")
                .doesNotContain("label", "home", "target", "path");
        assertThat(member(stats, "mood").getString("propertyType")).isEqualTo("Mood");
        assertThat(member(stats, "mood").getString("value")).isEqualTo("ANGRY");
        assertThat(member(stats, "tint").getString("type")).isEqualTo("color");
        assertThat(member(stats, "tint").getString("value")).isEqualTo("#7fff0000");
        JsonValue mood = type(project, "Mood");
        assertThat(mood.getString("type")).isEqualTo("enum");
        assertThat(mood.get("values").asStringArray()).containsExactly("CALM", "ANGRY");
        assertThat(project.get("propertyTypes").child.getLong("id")).isEqualTo(1);
    }

    @Test
    void prefabClassesCarryThePrefabsValues() {
        JsonValue prefab = json("""
                { "version": 1, "components": {
                    "Stats": { "hp": 9, "mood": "CALM", "tint": "#00FF00", "home": [1, 2], "speed": 0.25 },
                    "Tag": {}, "Marker": { "at": [1, 1] } } }""");
        JsonValue project = json(TiledTypes.update(registry, Map.of("enemy/slime", prefab), null, warnings));
        JsonValue slime = type(project, "enemy/slime");
        assertThat(slime.get("useAs").asStringArray()).containsExactly("object");
        JsonValue stats = member(slime, "Stats");
        assertThat(stats.getString("type")).isEqualTo("class");
        assertThat(stats.getString("propertyType")).isEqualTo("Stats");
        assertThat(stats.get("value").toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json))
                .as("the vector comes from object references only")
                .isEqualTo("{\"hp\":9,\"mood\":\"CALM\",\"tint\":\"#ff00ff00\",\"speed\":0.25}");
        assertThat(member(slime, "Tag").get("value").size).isZero();
        assertThat(names(slime)).as("libGDX fails on a member whose class has no members")
                .containsExactly("Stats", "Tag");
        assertThat(type(project, "Marker").get("members").size).isZero();
    }

    @Test
    void existingTypesKeepTheirIdsAndHandMadeTypesStay() {
        String existing = """
                { "automappingRulesFile": "rules.txt", "commands": [], "folders": ["."],
                  "propertyTypes": [
                    { "id": 4, "name": "Door", "type": "class", "useAs": ["object"], "members": [] },
                    { "id": 9, "name": "Tag", "type": "class", "useAs": ["property"], "color": "#ffa0a0a4",
                      "members": [] },
                    { "id": 2, "name": "Gone", "type": "class", "useAs": ["object"], "color": "#ff4a9de0",
                      "members": [] } ] }""";
        JsonValue project = json(TiledTypes.update(registry, Map.of(), existing, warnings));
        assertThat(project.getString("automappingRulesFile")).isEqualTo("rules.txt");
        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (JsonValue t = project.get("propertyTypes").child; t != null; t = t.next) {
            names.add(t.getString("name"));
            ids.add(t.getLong("id"));
        }
        assertThat(names.subList(0, 3)).containsExactly("Door", "Tag", "Gone");
        assertThat(ids.subList(0, 3)).containsExactly(4L, 9L, 2L);
        assertThat(names).contains("Sprite", "Stats", "Mood", "Marker")
                .as("placement comes from the object").doesNotContain("Transform");
        assertThat(names(type(project, "Sprite"))).contains("tint", "layer")
                .doesNotContain("flipX", "flipY", "origin", "regionId");
        assertThat(ids.subList(3, ids.size())).as("new types after the highest id").startsWith(10L, 11L)
                .doesNotHaveDuplicates();
        assertThat(member(type(project, "Tag"), "text").getString("value")).isEqualTo("hi");
        assertThat(warnings).containsExactly("type \"Gone\" is no longer a component or prefab; delete it in Tiled if "
                + "it is stale");

        String again = TiledTypes.update(registry, Map.of(), project.prettyPrint(
                com.badlogic.gdx.utils.JsonWriter.OutputType.json, 0), new ArrayList<>());
        assertThat(again).as("stable when run twice").isEqualTo(project.prettyPrint(
                com.badlogic.gdx.utils.JsonWriter.OutputType.json, 0) + "\n");
    }

    @Test
    void badPrefabsAreReportedTogether() {
        Map<String, JsonValue> prefabs = new LinkedHashMap<>();
        prefabs.put("a", json("{ \"version\": 1, \"components\": { \"Nope\": {}, \"Stats\": { \"hp\": \"x\", "
                + "\"wings\": 2 } } }"));
        prefabs.put("b", json("{ \"version\": 1 }"));
        assertThatThrownBy(() -> TiledTypes.update(registry, prefabs, null, warnings))
                .isInstanceOf(PrefabException.class)
                .satisfies(e -> {
                    List<String> problems = ((PrefabException) e).problems();
                    assertThat(problems).hasSize(4);
                    assertThat(problems.get(0)).isEqualTo("a: unknown component \"Nope\"; registered: "
                            + registry.names());
                    assertThat(problems.get(1)).startsWith("a: Stats.hp: ");
                    assertThat(problems.get(2)).isEqualTo("a: Stats has no field \"wings\"");
                    assertThat(problems.get(3)).isEqualTo("b: \"components\" must be an object");
                });
    }

    @Test
    void mainReadsTheManifestAndWritesTheProject(@TempDir File dir) throws IOException {
        write(new File(dir, "manifest.json"), """
                { "version": 1, "assets": [
                  { "id": "tex", "path": "t.png", "type": "texture", "group": "global" },
                  { "id": "hero", "path": "prefabs/hero.json", "type": "prefab", "group": "global" } ] }""");
        new File(dir, "prefabs").mkdirs();
        write(new File(dir, "prefabs/hero.json"), "{ \"version\": 1, \"components\": { \"Tag\": { \"text\": "
                + "\"hero\" } } }");
        File project = new File(dir, "maps/game.tiled-project");
        TiledTypes.main(new String[] {Setup.class.getName(), dir.getPath(), project.getPath()});
        JsonValue written = json(new String(java.nio.file.Files.readAllBytes(project.toPath()),
                StandardCharsets.UTF_8));
        assertThat(member(type(written, "hero"), "Tag").get("value").getString("text")).isEqualTo("hero");

        write(new File(dir, "prefabs/hero.json"), "{ broken");
        assertThatThrownBy(() -> TiledTypes.main(new String[] {Setup.class.getName(), dir.getPath(),
                project.getPath()})).isInstanceOf(PrefabException.class).hasMessageContaining("hero: ");
        assertThatThrownBy(() -> TiledTypes.main(new String[] {"java.lang.String", dir.getPath(),
                project.getPath()})).hasMessageContaining("does not implement");
    }

    static void write(File file, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
