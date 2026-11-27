package dev.ember2d.demo.shooter;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.Emitter;
import dev.ember2d.kit.bullets.Pattern;
import dev.ember2d.kit.topdown.shooter.WeaponDef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** The shooter demo's data (I-340): maps, prefabs, bullet types, patterns and weapons. Reads the files directly. */
class ShooterDataTest {

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

    final BulletTypes types;

    ShooterDataTest() throws Exception {
        types = BulletTypes.parse(json("bullets.json"), "bullets.json");
    }

    @Test
    void everyMapObjectIsAPrefabWithKnownComponents() throws Exception {
        Set<String> prefabs = manifest("prefab").keySet();
        ComponentRegistry registry = new ComponentRegistry();
        new ShooterComponents().register(registry);
        for (Map.Entry<String, String> p : manifest("prefab").entrySet()) {
            for (JsonValue c = json(p.getValue()).get("components").child; c != null; c = c.next) {
                assertThat(registry.contains(c.name)).as(p.getKey() + ": " + c.name).isTrue();
            }
        }
        for (String map : manifest("map").values()) {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(ASSETS.resolve(map).toFile());
            NodeList objects = doc.getElementsByTagName("object");
            int players = 0;
            for (int i = 0; i < objects.getLength(); i++) {
                Element o = (Element) objects.item(i);
                assertThat(prefabs).as(map + ": " + o.getAttribute("name")).contains(o.getAttribute("type"));
                players += o.getAttribute("type").equals("player") ? 1 : 0;
            }
            assertThat(players).as(map).isEqualTo(1);
        }
    }

    @Test
    void patternsAndWeaponsParseAndTheBossUsesAtLeastThreeTypes() throws Exception {
        Set<Emitter.Type> bossTypes = EnumSet.noneOf(Emitter.Type.class);
        for (Map.Entry<String, String> e : manifest("json").entrySet()) {
            if (e.getKey().startsWith("pattern.")) {
                Pattern p = BulletPatterns.parse(e.getKey(), json(e.getValue()), e.getValue(), types);
                if (e.getKey().startsWith("pattern.boss")) {
                    for (Emitter emitter : p.emitters()) {
                        bossTypes.add(emitter.type);
                        if (emitter.child != null) {
                            bossTypes.add(emitter.child.type);
                        }
                    }
                }
            } else if (e.getKey().startsWith("weapon.")) {
                WeaponDef w = WeaponDef.parse(e.getKey(), json(e.getValue()), e.getValue(), types);
                assertThat(w.fireRate).isPositive();
            }
        }
        assertThat(bossTypes).as("F-024: a boss stage with at least three pattern types").hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void theAtlasNamesEveryRegionTheDataUses() throws Exception {
        String atlas = Files.readString(ASSETS.resolve("gfx/shooter.atlas"));
        for (int i = 0; i < types.size(); i++) {
            String region = types.get(i).region.substring("atlas.shooter/".length());
            assertThat(atlas).as(types.get(i).name).contains("\n" + region + "\n");
        }
        for (String path : manifest("prefab").values()) {
            String text = Files.readString(ASSETS.resolve(path));
            int at = text.indexOf("atlas.shooter/");
            if (at >= 0) {
                String region = text.substring(at + "atlas.shooter/".length(), text.indexOf('"', at));
                assertThat(atlas).as(path).contains("\n" + region + "\n");
            }
        }
    }
}
