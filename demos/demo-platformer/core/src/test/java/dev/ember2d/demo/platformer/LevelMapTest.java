package dev.ember2d.demo.platformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Every level as edited in Tiled (F-015, I-170, I-250): every object names a prefab in the manifest, the level has one player, one
 * goal and a checkpoint, and they stand on solid ground. Reads the files directly (no libGDX).
 */
class LevelMapTest {

    private static final Path ASSETS = Path.of("assets");

    record MapObject(String name, String type, float x, float y) {
    }

    @Test
    void everyObjectIsAPrefabAndTheKeyObjectsStandOnGround() throws Exception {
        for (Levels.Level level : Levels.ALL) {
            check("maps/level" + level.number() + ".tmx");
        }
    }

    private static void check(String file) throws Exception {
        Document map = xml(ASSETS.resolve(file));
        Element root = map.getDocumentElement();
        int width = Integer.parseInt(root.getAttribute("width"));
        int tile = Integer.parseInt(root.getAttribute("tilewidth"));

        Set<String> prefabs = new HashSet<>();
        JsonValue manifest = new JsonReader().parse(Files.readString(ASSETS.resolve("manifest.json")));
        for (JsonValue e = manifest.get("assets").child; e != null; e = e.next) {
            if (e.getString("type").equals("prefab")) {
                prefabs.add(e.getString("id"));
                assertThat(ASSETS.resolve(e.getString("path"))).exists();
            }
        }

        List<MapObject> objects = new ArrayList<>();
        NodeList list = map.getElementsByTagName("object");
        for (int i = 0; i < list.getLength(); i++) {
            Element o = (Element) list.item(i);
            objects.add(new MapObject(o.getAttribute("name"), o.getAttribute("type"),
                    Float.parseFloat(o.getAttribute("x")), Float.parseFloat(o.getAttribute("y"))));
        }
        assertThat(objects).as(file).allSatisfy(o -> assertThat(prefabs).as(o.name()).contains(o.type()));
        assertThat(objects).filteredOn(o -> o.type().equals("player")).hasSize(1);
        assertThat(objects).filteredOn(o -> o.type().equals("level/goal")).hasSize(1);
        assertThat(objects).filteredOn(o -> o.type().equals("level/checkpoint")).isNotEmpty();
        assertThat(objects).filteredOn(o -> o.type().equals("enemy/pig")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(objects).filteredOn(o -> o.type().equals("hazard/spikes")).isNotEmpty();

        Set<Integer> solid = solidTiles();
        int[] ground = groundLayer(map);
        for (MapObject o : objects) {
            if (o.type().equals("player") || o.type().startsWith("level/")) {
                int col = (int) (o.x() / tile);
                int row = (int) (o.y() / tile);   // the object stands on the top of this row
                int gid = ground[row * width + col];
                assertThat(gid != 0 && solid.contains(gid - 1)).as(o.name() + " stands on solid ground").isTrue();
                assertThat(ground[(row - 1) * width + col]).as(o.name() + " has room above").isZero();
            }
        }
    }

    private static Set<Integer> solidTiles() throws Exception {
        Document tsx = xml(ASSETS.resolve("maps/terrain.tsx"));
        Set<Integer> solid = new HashSet<>();
        NodeList tiles = tsx.getElementsByTagName("tile");
        for (int i = 0; i < tiles.getLength(); i++) {
            Element t = (Element) tiles.item(i);
            NodeList props = t.getElementsByTagName("property");
            for (int j = 0; j < props.getLength(); j++) {
                Element p = (Element) props.item(j);
                if (p.getAttribute("name").equals("solid") && p.getAttribute("value").equals("true")) {
                    solid.add(Integer.parseInt(t.getAttribute("id")));
                }
            }
        }
        assertThat(solid).isNotEmpty();
        return solid;
    }

    private static int[] groundLayer(Document map) {
        NodeList layers = map.getElementsByTagName("layer");
        for (int i = 0; i < layers.getLength(); i++) {
            Element layer = (Element) layers.item(i);
            if (layer.getAttribute("name").equals("ground")) {
                String[] cells = layer.getElementsByTagName("data").item(0).getTextContent().trim().split("[,\\s]+");
                int[] out = new int[cells.length];
                for (int c = 0; c < cells.length; c++) {
                    out[c] = Integer.parseInt(cells[c]);
                }
                return out;
            }
        }
        throw new AssertionError("no ground layer");
    }

    private static Document xml(Path file) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
    }
}
