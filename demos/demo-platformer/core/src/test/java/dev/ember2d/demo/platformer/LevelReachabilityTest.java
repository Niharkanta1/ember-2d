package dev.ember2d.demo.platformer;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.utils.JsonReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Every level can be finished (I-250): a walk from the player to the goal over standable cells with a cautious jump
 * model, using only the mechanics the level switches on for its player.
 *
 * <ul>
 *   <li>Plain jump: up to 5 columns across and 3 up (kit defaults reach about 5.7 across and 3.2 up).</li>
 *   <li>Air jump or dash: up to 7 across; an air jump also 5 up.</li>
 *   <li>Wall jump: from a cell next to a wall 2 tiles high, up to 4 tiles away from it and 2 up.</li>
 * </ul>
 * A jump goes straight up to its top row, across, then straight down; every cell on the way and the cell above it must
 * be free (one-way planks can be jumped through). Spike cells are never stood on. Moving platforms count as ground
 * along their whole path. Pigs are ignored (they can be stomped).
 */
class LevelReachabilityTest {

    private static final Path ASSETS = Path.of("assets");
    private static final int TILE = 16;

    private int width;
    private int height;
    private boolean[][] solid;
    private boolean[][] oneWay;
    private boolean[][] spikes;
    private boolean[][] platform;
    private int dx;
    private int up;
    private boolean wallJump;
    private int[] furthest;

    @Test
    void everyLevelCanBeFinished() throws Exception {
        for (Levels.Level level : Levels.ALL) {
            String file = "maps/level" + level.number() + ".tmx";
            boolean ok = reachable(file);
            assertThat(ok).as(file + ": the goal is reachable from the player; stuck at column " + furthest[0]
                    + ", row " + furthest[1]).isTrue();
        }
    }

    @Test
    void eachNewMechanicIsNeeded() throws Exception {
        // Level 3 without its air jump and dash: the 6-tile spike pits are wider than a cautious plain jump.
        assertThat(reachable("maps/level3.tmx", false)).isFalse();
        // Level 2 without its wall jump: the cliff is too high for a plain jump.
        assertThat(reachable("maps/level2.tmx", false)).isFalse();
    }

    private boolean reachable(String file) throws Exception {
        return reachable(file, true);
    }

    private boolean reachable(String file, boolean abilities) throws Exception {
        Document map = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ASSETS.resolve(file).toFile());
        Element root = map.getDocumentElement();
        width = Integer.parseInt(root.getAttribute("width"));
        height = Integer.parseInt(root.getAttribute("height"));
        readTiles(map);
        spikes = new boolean[height][width];
        platform = new boolean[height][width];
        int[] start = null;
        int[] goal = null;
        dx = 5;
        up = 3;
        wallJump = false;
        float prefabDistance = new JsonReader().parse(Files.readString(ASSETS.resolve("prefabs/platform/moving.json")))
                .get("components").get("MovingPlatform").getFloat("distance");
        NodeList objects = map.getElementsByTagName("object");
        for (int i = 0; i < objects.getLength(); i++) {
            Element o = (Element) objects.item(i);
            float x = Float.parseFloat(o.getAttribute("x"));
            float y = Float.parseFloat(o.getAttribute("y"));
            int col = (int) (x / TILE);
            int row = (int) (y / TILE) - 1;   // the cell the object stands in
            switch (o.getAttribute("type")) {
                case "player" -> {
                    start = new int[] {col, row};
                    if (abilities) {
                        if (number(o, "airJumps", 0) > 0) {
                            dx = 7;
                            up = 5;
                        }
                        if (bool(o, "dash")) {
                            dx = 7;
                        }
                        wallJump = bool(o, "wallSlide") && bool(o, "wallJump");
                    }
                }
                case "level/goal" -> goal = new int[] {col, row};
                case "hazard/spikes" -> spikes[row][col] = true;
                case "platform/moving" -> {
                    int distance = (int) Math.ceil(number(o, "distance", prefabDistance));
                    int left = Math.round(x / TILE) - 1;   // two tiles wide, centred on x
                    if (bool(o, "vertical")) {
                        for (int r = Math.max(0, row - distance); r <= row; r++) {
                            platform[r][left] = true;
                            platform[r][left + 1] = true;
                        }
                    } else {
                        for (int c = left; c <= Math.min(width - 1, left + 1 + distance); c++) {
                            platform[row][c] = true;
                        }
                    }
                }
                default -> { }
            }
        }
        assertThat(start).as(file + " has a player").isNotNull();
        assertThat(goal).as(file + " has a goal").isNotNull();
        return search(start, goal);
    }

    /** States: c, r and 0 standing, 1 on a wall to the left, 2 on a wall to the right. */
    private boolean search(int[] start, int[] goal) {
        furthest = null;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {start[0], start[1], 0});
        while (!queue.isEmpty()) {
            int[] s = queue.poll();
            if (!seen.add(((long) s[0] * 1000 + s[1]) * 3 + s[2])) {
                continue;
            }
            if (furthest == null || s[0] > furthest[0]) {
                furthest = new int[] {s[0], s[1]};
            }
            if (s[2] == 0 && s[1] == goal[1] && Math.abs(s[0] - goal[0]) <= 1) {
                return true;
            }
            int reach = s[2] == 0 ? dx : 4;
            int rise = s[2] == 0 ? up : 2;
            for (int c2 = Math.max(0, s[0] - reach - 6); c2 <= Math.min(width - 1, s[0] + reach + 6); c2++) {
                if (s[2] == 1 && c2 <= s[0] || s[2] == 2 && c2 >= s[0]) {
                    continue;   // a wall jump goes away from the wall
                }
                for (int r2 = Math.max(1, s[1] - rise); r2 < height; r2++) {
                    int across = Math.abs(c2 - s[0]);
                    int fall = Math.max(0, r2 - s[1]);
                    if (across > reach + fall / 2) {
                        continue;   // falling further carries further
                    }
                    if (!jumpClear(s[0], s[1], c2, r2, rise)) {
                        continue;
                    }
                    if (standable(c2, r2)) {
                        queue.add(new int[] {c2, r2, 0});
                    } else if (wallJump && free(c2, r2)) {
                        if (wall(c2 - 1, r2)) {
                            queue.add(new int[] {c2, r2, 1});
                        }
                        if (wall(c2 + 1, r2)) {
                            queue.add(new int[] {c2, r2, 2});
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean standable(int c, int r) {
        if (!free(c, r) || spikes[r][c]) {
            return false;
        }
        return platform[r][c] || r + 1 < height && (solid[r + 1][c] || oneWay[r + 1][c]);
    }

    /** The cell and the one above it are free of solid tiles. */
    private boolean free(int c, int r) {
        return c >= 0 && c < width && r >= 1 && r < height && !solid[r][c] && !solid[r - 1][c];
    }

    private boolean wall(int c, int r) {
        return c >= 0 && c < width && r >= 1 && solid[r][c] && solid[r - 1][c];
    }

    /** Up at the start column to some top row, across, then down: every cell free. */
    private boolean jumpClear(int c, int r, int c2, int r2, int rise) {
        for (int top = Math.min(r, r2); top >= r - rise && top >= 1; top--) {
            if (column(c, r, top) && row(top, c, c2) && column(c2, r2, top)) {
                return true;
            }
        }
        return false;
    }

    private boolean column(int c, int a, int b) {
        for (int r = Math.min(a, b); r <= Math.max(a, b); r++) {
            if (!free(c, r)) {
                return false;
            }
        }
        return true;
    }

    private boolean row(int r, int a, int b) {
        for (int c = Math.min(a, b); c <= Math.max(a, b); c++) {
            if (!free(c, r)) {
                return false;
            }
        }
        return true;
    }

    private void readTiles(Document map) throws Exception {
        Set<Integer> solidIds = new HashSet<>();
        Set<Integer> oneWayIds = new HashSet<>();
        Document tsx = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ASSETS.resolve("maps/terrain.tsx").toFile());
        NodeList tiles = tsx.getElementsByTagName("tile");
        for (int i = 0; i < tiles.getLength(); i++) {
            Element t = (Element) tiles.item(i);
            int id = Integer.parseInt(t.getAttribute("id"));
            if (bool(t, "solid")) {
                solidIds.add(id);
            }
            if (bool(t, "oneWay")) {
                oneWayIds.add(id);
            }
        }
        String data = ((Element) map.getElementsByTagName("data").item(0)).getTextContent().trim();
        String[] cells = data.split("[,\\s]+");
        solid = new boolean[height][width];
        oneWay = new boolean[height][width];
        for (int i = 0; i < cells.length; i++) {
            int gid = Integer.parseInt(cells[i]);
            if (gid > 0) {
                solid[i / width][i % width] = solidIds.contains(gid - 1);
                oneWay[i / width][i % width] = oneWayIds.contains(gid - 1);
            }
        }
    }

    private static String property(Element e, String name) {
        NodeList props = e.getElementsByTagName("property");
        for (int i = 0; i < props.getLength(); i++) {
            Element p = (Element) props.item(i);
            if (p.getAttribute("name").equals(name)) {
                return p.getAttribute("value");
            }
        }
        return null;
    }

    private static boolean bool(Element e, String name) {
        return "true".equals(property(e, name));
    }

    private static float number(Element e, String name, float fallback) {
        String v = property(e, name);
        return v == null || v.isEmpty() ? fallback : Float.parseFloat(v);
    }
}
