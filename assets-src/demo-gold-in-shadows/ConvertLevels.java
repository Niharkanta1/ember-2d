import com.deadwolfgames.goldinshadows.engine.math.Aabb;
import com.deadwolfgames.goldinshadows.engine.physics.StaticBody;
import com.deadwolfgames.goldinshadows.features.level.ChestSpawn;
import com.deadwolfgames.goldinshadows.features.level.EnemySpawn;
import com.deadwolfgames.goldinshadows.features.level.ItemSpawn;
import com.deadwolfgames.goldinshadows.features.level.Level;
import com.deadwolfgames.goldinshadows.features.level.LevelData;
import com.deadwolfgames.goldinshadows.features.level.LevelParser;
import com.deadwolfgames.goldinshadows.features.level.SpikeTrapSpawn;
import com.deadwolfgames.goldinshadows.features.level.TileCell;
import com.deadwolfgames.goldinshadows.features.level.TileLayer;
import com.deadwolfgames.goldinshadows.features.level.TileSetDefinition;
import com.badlogic.gdx.math.Vector2;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

/**
 * Converts the original Gold in Shadows levels (ASCII {@code .level} files) into Tiled maps for the Ember2D demo
 * (F-029, I-600). It runs the original game's own {@code LevelParser} and {@code Level.build}, so the auto-tiling
 * (brick faces, caps, thin wall overlays, the seeded floor and wall variation) and the collision boxes are exactly the
 * original's. Run once; afterwards the maps are edited in Tiled.
 *
 * <p>Output per level ({@code maps/<name>.tmx}):
 * <ul>
 *   <li>tile layers {@code Base}, {@code Floor}, {@code Walls}, {@code Props} (drawn under the actors) and
 *       {@code Overhead} (drawn above them, render layer {@code overhead}), using {@code maps/tileset.tsx};</li>
 *   <li>object layer {@code walls}: the original's collision boxes as rectangles, with {@code opaque} (blocks sight)
 *       and {@code door} (the exit door's leaf, removed when the door opens);</li>
 *   <li>object layer {@code entities}: point objects whose class is the prefab ID, at the actor's position
 *       (bottom-centre for cell-based things), with field overrides as properties.</li>
 * </ul>
 * Coordinates: Tiled and the original are both y-down pixels; the map origin is moved to the level bounds' top-left.
 * Patrol routes are written in Ember2D world units (y-up, one unit = one 16-pixel tile), ready for the game.
 *
 * <pre>
 * javac -d build/gis -cp gdx-1.14.2.jar &lt;original&gt;/core/src/main/java/.../{features/level,engine/math,engine/physics}/*.java
 * java -cp build/gis;gdx-1.14.2.jar ConvertLevels.java &lt;original assets/levels&gt; demos/demo-gold-in-shadows/core/assets/maps
 * </pre>
 */
public final class ConvertLevels {

    private static final int TILE = 16;
    private static final int TILESET_COLUMNS = 10;   // TileSet-Sheet.png is 160 x 144: 10 x 9 tiles

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: ConvertLevels <original levels folder> <output maps folder>");
            System.exit(1);
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        for (String name : List.of("level1", "level2", "level3", "main")) {
            String source = Files.readString(in.resolve(name + ".level"), StandardCharsets.UTF_8);
            LevelData data = new LevelParser().parse(source);
            Level level = Level.build(data, TileSetDefinition.dungeon());
            String tmx = convert(data, level);
            Files.writeString(out.resolve(name + ".tmx"), tmx, StandardCharsets.UTF_8);
            System.out.println(name + ": " + data.name() + ", " + level.getWalls().size() + " wall boxes, "
                    + data.enemySpawns().size() + " enemies, " + data.items().size() + " items");
        }
    }

    static String convert(LevelData data, Level level) {
        Aabb b = level.getBounds();
        int originX = (int) Math.floor(b.minX() / TILE);
        int originY = (int) Math.floor(b.minY() / TILE);
        int width = (int) Math.ceil(b.maxX() / TILE) - originX;
        int height = (int) Math.ceil(b.maxY() / TILE) - originY;
        float ox = originX * TILE;
        float oy = originY * TILE;
        StringBuilder s = new StringBuilder();
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        s.append("<map version=\"1.10\" tiledversion=\"1.11.0\" orientation=\"orthogonal\" renderorder=\"right-down\" ")
                .append("width=\"").append(width).append("\" height=\"").append(height)
                .append("\" tilewidth=\"16\" tileheight=\"16\" infinite=\"0\" nextlayerid=\"8\" nextobjectid=\"1\">\n");
        s.append(" <properties>\n");
        s.append("  <property name=\"title\" value=\"").append(xml(data.name())).append("\"/>\n");
        s.append(" </properties>\n");
        s.append(" <tileset firstgid=\"1\" source=\"tileset.tsx\"/>\n");
        int id = 1;
        for (TileLayer layer : data.layers()) {
            int[][] gids = new int[height][width];
            for (TileCell c : layer.cells()) {
                int x = c.x() - originX;
                int y = c.y() - originY;
                if (x >= 0 && y >= 0 && x < width && y < height) {
                    gids[y][x] = c.atlasY() * TILESET_COLUMNS + c.atlasX() + 1;
                }
            }
            boolean overhead = layer.name().equals(LevelData.OVERHEAD_LAYER);
            s.append(" <layer id=\"").append(id++).append("\" name=\"").append(xml(layer.name()))
                    .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n");
            s.append("  <properties><property name=\"renderLayer\" value=\"")
                    .append(overhead ? "overhead" : "floor").append("\"/></properties>\n");
            s.append("  <data encoding=\"csv\">\n");
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    s.append(gids[y][x]);
                    if (x < width - 1 || y < height - 1) {
                        s.append(',');
                    }
                }
                s.append('\n');
            }
            s.append("</data>\n </layer>\n");
        }

        int objectId = 1;
        OptionalInt door = level.doorLeafBodyId();
        s.append(" <objectgroup id=\"").append(id++).append("\" name=\"walls\" visible=\"0\">\n");
        for (StaticBody wall : level.getWalls()) {
            Aabb a = wall.bounds();
            boolean leaf = door.isPresent() && wall.bodyId() == door.getAsInt();
            s.append("  <object id=\"").append(objectId++).append("\" x=\"").append(f(a.minX() - ox))
                    .append("\" y=\"").append(f(a.minY() - oy)).append("\" width=\"").append(f(a.width()))
                    .append("\" height=\"").append(f(a.height())).append("\">\n   <properties>\n")
                    .append("    <property name=\"opaque\" type=\"bool\" value=\"").append(wall.isOpaque()).append("\"/>\n");
            if (leaf) {
                s.append("    <property name=\"door\" type=\"bool\" value=\"true\"/>\n");
            }
            s.append("   </properties>\n  </object>\n");
        }
        s.append(" </objectgroup>\n");

        s.append(" <objectgroup id=\"").append(id).append("\" name=\"entities\">\n");
        Vector2 player = data.playerSpawn();
        objectId = point(s, objectId, "player", "player", player.x - ox, player.y - oy, null);
        Vector2 camera = data.cameraStart();
        objectId = point(s, objectId, "camera", "camera", camera.x - ox, camera.y - oy, null);
        for (EnemySpawn e : data.enemySpawns()) {
            String props = null;
            if (!e.patrolRoute().isEmpty()) {
                StringBuilder route = new StringBuilder();
                for (Vector2 p : e.patrolRoute()) {
                    if (route.length() > 0) {
                        route.append(';');
                    }
                    route.append(f((p.x - ox) / TILE)).append(',').append(f((height * TILE - (p.y - oy)) / TILE));
                }
                props = "    <property name=\"Creature.patrol\" value=\"" + route + "\"/>\n";
            }
            String kind = e.type().name().toLowerCase(Locale.ROOT);
            objectId = point(s, objectId, null, "enemy/" + kind, e.x() - ox, e.y() - oy, props);
        }
        for (ItemSpawn item : data.items()) {
            String kind = switch (item.type()) {
                case GOLD_COIN -> "coin";
                case GOLD_PILE -> "pile";
                case MEDKIT -> "medkit";
                case KEY -> "key";
            };
            objectId = point(s, objectId, null, "pickup/" + kind, item.x() - ox, item.y() - oy, null);
        }
        for (ChestSpawn c : data.chests()) {
            objectId = point(s, objectId, null, "chest", c.cellX() * TILE + TILE / 2f - ox, (c.cellY() + 1) * TILE - oy,
                    null);
        }
        for (SpikeTrapSpawn t : data.spikeTraps()) {
            objectId = point(s, objectId, null, "spikes", t.cellX() * TILE + TILE / 2f - ox, (t.cellY() + 1) * TILE - oy,
                    "    <property name=\"SpikeTrap.phase\" type=\"float\" value=\"" + f(t.phaseSeconds()) + "\"/>\n");
        }
        if (data.exit().isPresent()) {
            var e = data.exit().get();
            objectId = point(s, objectId, "exit", "exit", (e.cellX() + 1) * TILE - ox, (e.cellY() + 1) * TILE - oy,
                    "    <property name=\"ExitDoor.locked\" type=\"bool\" value=\"" + e.requiresKey() + "\"/>\n");
        }
        s.append(" </objectgroup>\n</map>\n");
        return s.toString().replace("nextobjectid=\"1\"", "nextobjectid=\"" + objectId + "\"");
    }

    private static int point(StringBuilder s, int id, String name, String type, float x, float y, String props) {
        s.append("  <object id=\"").append(id).append('"');
        if (name != null) {
            s.append(" name=\"").append(name).append('"');
        }
        s.append(" type=\"").append(type).append("\" x=\"").append(f(x)).append("\" y=\"").append(f(y)).append("\">\n");
        if (props != null) {
            s.append("   <properties>\n").append(props).append("   </properties>\n");
        }
        s.append("   <point/>\n  </object>\n");
        return id + 1;
    }

    private static String f(float v) {
        String text = String.format(Locale.ROOT, "%.3f", v);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }
}
