package dev.ember2d.kit.topdown.ai;

import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.indexed.IndexedGraph;
import com.badlogic.gdx.utils.Array;
import dev.ember2d.collision.TileGrid;

/**
 * The walkable cells of a tile grid as a gdx-ai graph (I-330): 8 neighbours, diagonals only when both side cells are
 * free (no corner cutting). Built once; call {@link #refresh()} after the grid changes. Internal to the AI adapter.
 */
final class TileGraph implements IndexedGraph<TileGraph.Node> {

    static final class Node {
        final int x;
        final int y;
        final int index;
        final Array<Connection<Node>> connections = new Array<>(8);

        Node(int x, int y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;
        }
    }

    private static final class Link implements Connection<Node> {
        final Node from;
        final Node to;
        final float cost;

        Link(Node from, Node to, float cost) {
            this.from = from;
            this.to = to;
            this.cost = cost;
        }

        @Override
        public float getCost() {
            return cost;
        }

        @Override
        public Node getFromNode() {
            return from;
        }

        @Override
        public Node getToNode() {
            return to;
        }
    }

    private final TileGrid grid;
    private final Node[] nodes;

    TileGraph(TileGrid grid) {
        this.grid = grid;
        nodes = new Node[grid.width() * grid.height()];
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                nodes[y * grid.width() + x] = new Node(x, y, y * grid.width() + x);
            }
        }
        refresh();
    }

    /** Rebuilds the connections from the grid's solid cells. */
    void refresh() {
        float diagonal = (float) Math.sqrt(2);
        for (Node n : nodes) {
            n.connections.clear();
            if (!walkable(n.x, n.y)) {
                continue;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if ((dx == 0 && dy == 0) || !walkable(n.x + dx, n.y + dy)) {
                        continue;
                    }
                    if (dx != 0 && dy != 0 && (!walkable(n.x + dx, n.y) || !walkable(n.x, n.y + dy))) {
                        continue;   // no corner cutting
                    }
                    n.connections.add(new Link(n, node(n.x + dx, n.y + dy), dx != 0 && dy != 0 ? diagonal : 1f));
                }
            }
        }
    }

    boolean walkable(int x, int y) {
        return x >= 0 && y >= 0 && x < grid.width() && y < grid.height() && !grid.solid(x, y);
    }

    Node node(int x, int y) {
        return nodes[y * grid.width() + x];
    }

    TileGrid grid() {
        return grid;
    }

    @Override
    public int getIndex(Node node) {
        return node.index;
    }

    @Override
    public int getNodeCount() {
        return nodes.length;
    }

    @Override
    public Array<Connection<Node>> getConnections(Node fromNode) {
        return fromNode.connections;
    }
}
