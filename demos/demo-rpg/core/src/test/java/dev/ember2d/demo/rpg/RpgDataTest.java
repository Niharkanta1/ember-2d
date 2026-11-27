package dev.ember2d.demo.rpg;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.kit.common.DialogueRunner;
import dev.ember2d.kit.novel.NovelCharacters;
import dev.ember2d.kit.novel.NovelDirector;
import dev.ember2d.kit.topdown.rpg.ItemCatalog;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.WorldFlags;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The RPG demo's data and play-through rules (I-450): maps, prefabs, the town story with its bound functions, the
 * cutscene's staging, and saving a play-through. Reads the files directly.
 */
class RpgDataTest {

    static final Path ASSETS = Path.of("assets");

    static JsonValue json(String path) throws Exception {
        return new JsonReader().parse(Files.readString(ASSETS.resolve(path)));
    }

    static Map<String, String> manifest(String type) throws Exception {
        Map<String, String> out = new HashMap<>();
        for (JsonValue e = json("manifest.json").get("assets").child; e != null; e = e.next) {
            if (e.getString("type").equals(type)) {
                out.put(e.getString("id"), e.getString("path"));
                assertThat(ASSETS.resolve(e.getString("path"))).as(e.getString("id")).exists();
            }
        }
        return out;
    }

    static Game newGame() throws Exception {
        ItemCatalog items = ItemCatalog.parse(json("items.json"), "items.json");
        return new Game(items, new WorldFlags(null), story());
    }

    static DialogueRunner story() throws Exception {
        return DialogueRunner.fromJson(Files.readString(ASSETS.resolve("ink/town.ink.json")), "town.ink.json");
    }

    /** Plays a knot, picking the given choices in order; returns the lines shown. */
    static List<String> talk(Game game, String knot, int... picks) {
        List<String> lines = new ArrayList<>();
        game.story.jumpTo(knot);
        int pick = 0;
        while (true) {
            while (game.story.canContinue()) {
                game.story.next();
                if (!game.story.text().isBlank()) {
                    lines.add(game.story.line());
                }
            }
            if (game.story.choices().isEmpty()) {
                return lines;
            }
            game.story.choose(picks[pick++]);
        }
    }

    @Test
    void theQuestRunsThroughTheStoryFunctions() throws Exception {
        Game game = newGame();
        List<String> lines = talk(game, "elder", 1);
        assertThat(lines).first().isEqualTo("Grik: Ah, a knight! Just in time.");
        assertThat(lines).last().isEqualTo("Grik: Pity. Come back if you change your mind.");
        assertThat(game.inventory.has("key.cellar", 1)).isFalse();

        assertThat(talk(game, "cellar_door")).containsExactly("The door is locked. Someone in town must have the key.");
        assertThat(game.flags.getBool("door.cellar", false)).isFalse();

        lines = talk(game, "elder", 0);
        assertThat(lines).first().as("the story remembers the first talk").isEqualTo("Grik: Back again? Changed your mind?");
        assertThat(lines).contains("You received the cellar key.");
        assertThat(game.inventory.has("key.cellar", 1)).isTrue();

        talk(game, "cellar_door");
        assertThat(game.flags.getBool("door.cellar", false)).as("the door opens through a flag").isTrue();
        talk(game, "chest");
        assertThat(game.inventory.count("tonic")).isEqualTo(2);
        assertThat(game.flags.getBool("chest.cellar", false)).isTrue();

        lines = talk(game, "elder");
        assertThat(lines).first().isEqualTo("Grik: Is that... my tonic! You found it!");
        assertThat(game.inventory.count("tonic")).isEqualTo(1);
        assertThat(game.flags.getBool("quest.done", false)).isTrue();
        assertThat(talk(game, "elder")).containsExactly("Grik: The cellar is quiet again, thanks to you. Mind the stairs!");
        assertThat(talk(game, "slime")).containsExactly("Slime: Blub. Tell the old goblin I said hello.");
    }

    @Test
    void aSavedPlayThroughLoadsTheFlagsTheItemsTheStoryAndThePlace() throws Exception {
        Game game = newGame();
        talk(game, "elder", 1);
        talk(game, "elder", 0);
        talk(game, "cellar_door");
        game.area = "cellar";
        game.x = 5.5f;
        game.y = 3f;
        game.flags.set(AreaScene.CUTSCENE_FLAG, true);
        String saved = game.write().toJson();

        Game back = newGame();
        back.read(SaveData.parse(saved));
        assertThat(back.area).isEqualTo("cellar");
        assertThat(back.hasPosition).isTrue();
        assertThat(back.x).isEqualTo(5.5f);
        assertThat(back.inventory.has("key.cellar", 1)).isTrue();
        assertThat(back.flags.getBool("door.cellar", false)).as("the door stays open").isTrue();
        assertThat(back.flags.getBool(AreaScene.CUTSCENE_FLAG, false)).as("the cutscene is not shown again").isTrue();
        assertThat(talk(back, "elder")).containsExactly(
                "Grik: The cellar door is in the old hall, just north of here. Bring back my tonic!");
        talk(back, "chest");
        assertThat(back.flags.getBool("chest.cellar", false)).isTrue();
        Game again = newGame();
        again.read(SaveData.parse(back.write().toJson()));
        assertThat(again.flags.getBool("chest.cellar", false)).as("the chest stays open").isTrue();
        assertThat(again.inventory.count("tonic")).isEqualTo(2);
    }

    @Test
    void theCutsceneStagesOnlyKnownCharactersAndTags() throws Exception {
        NovelCharacters cast = NovelCharacters.parse(json("cast.json"), "cast.json");
        for (int pick = 0; pick < 2; pick++) {
            NovelDirector director = new NovelDirector(DialogueRunner.fromJson(
                    Files.readString(ASSETS.resolve("ink/cellar.ink.json")), "cellar.ink.json"), cast, null, null,
                    true);   // dev mode: a bad tag throws
            List<String> lines = new ArrayList<>();
            NovelDirector.Step step;
            while ((step = director.next()).kind() != NovelDirector.Kind.END) {
                if (step.kind() == NovelDirector.Kind.CHOICES) {
                    assertThat(step.choices()).hasSize(2);
                    director.choose(pick);
                } else {
                    lines.add(step.line().name() + ": " + step.line().text());
                }
            }
            assertThat(lines).first().isEqualTo("Knight: So this is Grik's cellar. It smells of old cheese.");
            assertThat(lines).last().isEqualTo("Knight: That was... odd.");
            assertThat(director.stage().characters()).containsOnlyKeys("knight");
        }
    }

    @Test
    void everyMapObjectIsAPrefabAndEveryTravelArrivesAtAMarker() throws Exception {
        Set<String> prefabs = manifest("prefab").keySet();
        ComponentRegistry registry = new ComponentRegistry();
        new RpgComponents().register(registry);
        for (Map.Entry<String, String> p : manifest("prefab").entrySet()) {
            for (JsonValue c = json(p.getValue()).get("components").child; c != null; c = c.next) {
                assertThat(registry.contains(c.name)).as(p.getKey() + ": " + c.name).isTrue();
            }
        }
        Map<String, Set<String>> names = new HashMap<>();
        Map<String, List<String>> travels = new HashMap<>();
        Set<String> interactions = new HashSet<>();
        for (Map.Entry<String, String> m : manifest("map").entrySet()) {
            String area = m.getKey().substring("map.".length());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(ASSETS.resolve(m.getValue()).toFile());
            NodeList objects = doc.getElementsByTagName("object");
            Set<String> here = names.computeIfAbsent(area, a -> new HashSet<>());
            for (int i = 0; i < objects.getLength(); i++) {
                Element o = (Element) objects.item(i);
                assertThat(prefabs).as(area + ": " + o.getAttribute("name")).contains(o.getAttribute("type"));
                here.add(o.getAttribute("name"));
                NodeList props = o.getElementsByTagName("property");
                String to = null;
                String spawn = null;
                for (int k = 0; k < props.getLength(); k++) {
                    Element p = (Element) props.item(k);
                    if (p.getAttribute("name").equals("Travel.area")) {
                        to = p.getAttribute("value");
                    } else if (p.getAttribute("name").equals("Travel.spawn")) {
                        spawn = p.getAttribute("value");
                    }
                }
                if (to != null) {
                    travels.computeIfAbsent(to, a -> new ArrayList<>()).add(spawn);
                }
            }
            assertThat(here).as(area).contains("player", "camera");
        }
        for (Map.Entry<String, List<String>> t : travels.entrySet()) {
            assertThat(names).as("travel to " + t.getKey()).containsKey(t.getKey());
            assertThat(names.get(t.getKey())).as("markers in " + t.getKey()).containsAll(t.getValue());
        }
        assertThat(names.get("town")).as("a new game starts at the start marker").contains("start");
        for (String path : manifest("prefab").values()) {
            JsonValue interactable = json(path).get("components").get("Interactable");
            if (interactable != null) {
                interactions.add(interactable.getString("action"));
            }
        }
        DialogueRunner story = story();
        for (String knot : interactions) {
            story.jumpTo(knot);   // fails for a knot the story does not have
        }
        story.jumpTo("chest_empty");
    }
}
