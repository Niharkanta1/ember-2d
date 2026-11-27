package dev.ember2d.kit.topdown.ai;

import com.badlogic.gdx.ai.pfa.DefaultGraphPath;
import com.badlogic.gdx.ai.pfa.Heuristic;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.utils.FloatArray;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.PublicApi;

/**
 * Paths on a tile grid (F-016, I-330): A* through gdx-ai over the free cells, 8 directions without cutting corners.
 * One planner per map; searches reuse their buffers. Waypoints are cell centres in world units, from the cell after the
 * start to the goal's cell.
 *
 * <pre>{@code
 * PathPlanner paths = new PathPlanner(map.collision());
 * FloatArray waypoints = new FloatArray();
 * if (paths.find(x, y, playerX, playerY, waypoints)) { ... }   // x0, y0, x1, y1, ...
 * }</pre>
 */
@PublicApi
public final class PathPlanner {

    private final TileGraph graph;
    private final IndexedAStarPathFinder<TileGraph.Node> finder;
    private final DefaultGraphPath<TileGraph.Node> path = new DefaultGraphPath<>();
    private final Heuristic<TileGraph.Node> octile = (a, b) -> {
        int dx = Math.abs(a.x - b.x);
        int dy = Math.abs(a.y - b.y);
        return Math.max(dx, dy) + 0.41421356f * Math.min(dx, dy);
    };

    public PathPlanner(TileGrid grid) {
        this.graph = new TileGraph(grid);
        this.finder = new IndexedAStarPathFinder<>(graph);
    }

    /** Call after changing the grid's solid cells (doors, destructible walls). */
    public void refresh() {
        graph.refresh();
    }

    /** Whether the cell at a world position can be walked on. */
    public boolean walkable(float x, float y) {
        TileGrid g = graph.grid();
        return graph.walkable(g.cellX(x), g.cellY(y));
    }

    /**
     * Finds a path between two world positions; on success {@code out} holds the waypoints as x, y pairs.
     *
     * @return false when either end is in a solid cell or outside the grid, or no path exists ({@code out} is cleared)
     */
    public boolean find(float fromX, float fromY, float toX, float toY, FloatArray out) {
        out.clear();
        TileGrid g = graph.grid();
        int sx = g.cellX(fromX);
        int sy = g.cellY(fromY);
        int gx = g.cellX(toX);
        int gy = g.cellY(toY);
        if (!graph.walkable(sx, sy) || !graph.walkable(gx, gy)) {
            return false;
        }
        path.clear();
        if (!finder.searchNodePath(graph.node(sx, sy), graph.node(gx, gy), octile, path)) {
            return false;
        }
        for (int i = 1; i < path.getCount(); i++) {
            TileGraph.Node n = path.get(i);
            out.add((n.x + 0.5f) * g.cellWidth());
            out.add((n.y + 0.5f) * g.cellHeight());
        }
        return true;
    }
}
