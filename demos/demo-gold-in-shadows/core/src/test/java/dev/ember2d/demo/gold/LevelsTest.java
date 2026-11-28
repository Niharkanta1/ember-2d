package dev.ember2d.demo.gold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The converted campaign maps (F-029, I-600) are complete and every pick-up, enemy, chest and the exit can be reached
 * from the start: the original game's {@code CampaignLevelsTest}, run on the Tiled maps so that edits in Tiled stay
 * checked. Same rule as the original: the wall boxes grown by the player's 6-pixel radius, a 2-pixel grid, and a
 * target counts as reached within 10 pixels (chests within 16, the reach of the open-chest action).
 */
class LevelsTest {

    static final float PLAYER_RADIUS = 6f;
    static final float STEP = 2f;
    static final float REACH = 10f;
    static final float CHEST_REACH = 16f;

    record Box(float x, float y, float w, float h) {
    }

    record Thing(String type, float x, float y) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"level1", "level2", "level3"})
    void campaignLevelsAreCompleteAndReachable(String name) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Path.of("assets", "maps", name + ".tmx").toFile());
        Element map = doc.getDocumentElement();
        int width = Integer.parseInt(map.getAttribute("width")) * 16;
        int height = Integer.parseInt(map.getAttribute("height")) * 16;
        List<Box> walls = new ArrayList<>();
        List<Thing> things = new ArrayList<>();
        NodeList groups = doc.getElementsByTagName("objectgroup");
        for (int g = 0; g < groups.getLength(); g++) {
            Element group = (Element) groups.item(g);
            NodeList objects = group.getElementsByTagName("object");
            for (int i = 0; i < objects.getLength(); i++) {
                Element o = (Element) objects.item(i);
                float x = Float.parseFloat(o.getAttribute("x"));
                float y = Float.parseFloat(o.getAttribute("y"));
                if (group.getAttribute("name").equals("walls")) {
                    walls.add(new Box(x, y, Float.parseFloat(o.getAttribute("width")),
                            Float.parseFloat(o.getAttribute("height"))));
                } else {
                    things.add(new Thing(o.getAttribute("type"), x, y));
                }
            }
        }
        assertThat(things).filteredOn(t -> t.type().equals("pickup/key")).hasSize(1);
        assertThat(things).filteredOn(t -> t.type().equals("exit")).hasSize(1);
        assertThat(things).filteredOn(t -> t.type().startsWith("enemy/")).isNotEmpty();
        assertThat(things).filteredOn(t -> t.type().equals("pickup/coin")).as("plenty of gold").hasSizeGreaterThan(7);

        int cols = (int) (width / STEP);
        int rows = (int) (height / STEP);
        boolean[][] open = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float px = (c + 0.5f) * STEP;
                float py = (r + 0.5f) * STEP;
                boolean free = true;
                for (Box b : walls) {
                    float dx = Math.max(Math.max(b.x() - px, 0f), px - (b.x() + b.w()));
                    float dy = Math.max(Math.max(b.y() - py, 0f), py - (b.y() + b.h()));
                    if (dx * dx + dy * dy < PLAYER_RADIUS * PLAYER_RADIUS) {
                        free = false;
                        break;
                    }
                }
                open[r][c] = free;
            }
        }
        Thing player = things.stream().filter(t -> t.type().equals("player")).findFirst().orElseThrow();
        int sr = (int) (player.y() / STEP);
        int sc = (int) (player.x() / STEP);
        assertThat(open[sr][sc]).as(name + ": the player starts in the open").isTrue();
        boolean[][] seen = new boolean[rows][cols];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[] {sr, sc});
        seen[sr][sc] = true;
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int[][] next = {{p[0] + 1, p[1]}, {p[0] - 1, p[1]}, {p[0], p[1] + 1}, {p[0], p[1] - 1}};
            for (int[] n : next) {
                if (n[0] >= 0 && n[1] >= 0 && n[0] < rows && n[1] < cols && open[n[0]][n[1]] && !seen[n[0]][n[1]]) {
                    seen[n[0]][n[1]] = true;
                    queue.add(n);
                }
            }
        }
        for (Thing t : things) {
            if (t.type().equals("camera") || t.type().equals("spikes")) {
                continue;
            }
            float tx = t.x();
            float ty = t.type().equals("exit") ? t.y() + 8f : t.y();   // in front of the door, as in the original
            float ry = t.type().equals("chest") ? ty - 8f : ty;         // the chest's centre
            float reach = t.type().equals("chest") ? CHEST_REACH : REACH;
            assertThat(reachable(seen, tx, ry, reach)).as(name + ": " + t.type() + " at " + tx + "," + ty).isTrue();
        }
    }

    private static boolean reachable(boolean[][] seen, float x, float y, float reach) {
        int r0 = (int) ((y - reach) / STEP);
        int r1 = (int) ((y + reach) / STEP);
        int c0 = (int) ((x - reach) / STEP);
        int c1 = (int) ((x + reach) / STEP);
        for (int r = Math.max(0, r0); r <= Math.min(seen.length - 1, r1); r++) {
            for (int c = Math.max(0, c0); c <= Math.min(seen[0].length - 1, c1); c++) {
                float dx = (c + 0.5f) * STEP - x;
                float dy = (r + 0.5f) * STEP - y;
                if (seen[r][c] && dx * dx + dy * dy <= reach * reach) {
                    return true;
                }
            }
        }
        return false;
    }
}
