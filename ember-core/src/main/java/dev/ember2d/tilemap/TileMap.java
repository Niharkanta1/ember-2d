package dev.ember2d.tilemap;

import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.assets.Assets;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A loaded Tiled map (F-009) in world units: one tile is {@code tileWidth / pixelsPerUnit} world units, world y grows
 * upwards, and the map's bottom-left corner is world (0, 0). The asset service owns the underlying {@link TiledMap};
 * this class never disposes it.
 *
 * <p>Creating a {@code TileMap} replaces libGDX's animated tiles with tiles driven by game time (see
 * {@link TileMapRenderer}).
 */
@PublicApi
public final class TileMap {

    /** Tiles on a layer with this name are solid; the layer is never drawn. */
    public static final String COLLISION_LAYER = "collision";
    /** Tile property for a solid tile. */
    public static final String SOLID = "solid";
    /** Tile property for a one-way (jump-through) tile. */
    public static final String ONE_WAY = "oneWay";

    private static final Log LOG = Log.get("tilemap");

    private final TiledMap map;
    private final String source;
    private final int width;
    private final int height;
    private final float tileWidth;
    private final float tileHeight;
    private final List<MapLayer> leaves;
    private final List<String> layerNames;
    private final GameTimeAnimatedTile.Clock clock;
    private TileGrid collision;

    private TileMap(TiledMap map, String source, float pixelsPerUnit) {
        this.map = map;
        this.source = source;
        MapProperties p = map.getProperties();
        String orientation = p.get("orientation", String.class);
        if (!"orthogonal".equals(orientation)) {
            throw new TileMapException(source, orientation == null ? "not a Tiled map (no orientation)"
                    : "orientation \"" + orientation + "\" is not supported; Ember2D loads orthogonal maps only");
        }
        this.width = p.get("width", 0, Integer.class);
        this.height = p.get("height", 0, Integer.class);
        int tilePixelsWide = p.get("tilewidth", 0, Integer.class);
        int tilePixelsHigh = p.get("tileheight", 0, Integer.class);
        if (width <= 0 || height <= 0 || tilePixelsWide <= 0 || tilePixelsHigh <= 0) {
            throw new TileMapException(source, "empty map (size " + width + "x" + height + " tiles of "
                    + tilePixelsWide + "x" + tilePixelsHigh + " px)");
        }
        this.tileWidth = tilePixelsWide / pixelsPerUnit;
        this.tileHeight = tilePixelsHigh / pixelsPerUnit;
        List<MapLayer> found = new ArrayList<>();
        flatten(map.getLayers(), found);
        this.leaves = Collections.unmodifiableList(found);
        List<String> names = new ArrayList<>();
        for (MapLayer layer : found) {
            names.add(layer.getName());
        }
        this.layerNames = Collections.unmodifiableList(names);
        this.clock = swapAnimatedTiles();
    }

    /**
     * Wraps a loaded map.
     *
     * @param source the map's path, used in messages
     * @throws TileMapException if the map is not orthogonal or has no size
     */
    public static TileMap of(TiledMap map, String source, float pixelsPerUnit) {
        Objects.requireNonNull(map, "map");
        if (!(pixelsPerUnit > 0f)) {
            throw new IllegalArgumentException("pixelsPerUnit must be > 0 (was " + pixelsPerUnit + ")");
        }
        return new TileMap(map, Objects.requireNonNull(source, "source"), pixelsPerUnit);
    }

    /** Wraps the map loaded for a manifest ID ({@code type: "map"}). */
    public static TileMap load(Assets assets, String assetId, float pixelsPerUnit) {
        return of(assets.get(assetId, TiledMap.class), assetId, pixelsPerUnit);
    }

    /** The underlying libGDX map. */
    public TiledMap tiled() {
        return map;
    }

    /** The map's path or asset ID. */
    public String source() {
        return source;
    }

    /** Width in tiles. */
    public int width() {
        return width;
    }

    /** Height in tiles. */
    public int height() {
        return height;
    }

    /** Tile width in world units. */
    public float tileWidth() {
        return tileWidth;
    }

    /** Tile height in world units. */
    public float tileHeight() {
        return tileHeight;
    }

    public float worldWidth() {
        return width * tileWidth;
    }

    public float worldHeight() {
        return height * tileHeight;
    }

    /** Map properties set in Tiled (plus libGDX's own: width, height, tilewidth, ...). */
    public MapProperties properties() {
        return map.getProperties();
    }

    /** Names of all layers, group layers flattened, in Tiled's drawing order (bottom first). */
    public List<String> layerNames() {
        return layerNames;
    }

    /**
     * The tile at a cell of a tile layer.
     *
     * @return the tile, or null if the cell is empty or outside the map
     * @throws IllegalArgumentException if there is no tile layer with that name
     */
    public TiledMapTile tile(String layer, int x, int y) {
        TiledMapTileLayer.Cell cell = tileLayer(layer).getCell(x, y);
        return cell == null ? null : cell.getTile();
    }

    /** The column containing a world x (may be outside the map). */
    public int tileX(float worldX) {
        return (int) Math.floor(worldX / tileWidth);
    }

    /** The row containing a world y (row 0 at the bottom; may be outside the map). */
    public int tileY(float worldY) {
        return (int) Math.floor(worldY / tileHeight);
    }

    /** The world position of a cell's centre. Returns {@code out}. */
    public Vector2 tileCenter(int x, int y, Vector2 out) {
        return out.set((x + 0.5f) * tileWidth, (y + 0.5f) * tileHeight);
    }

    /** The map's area in world units, from (0, 0). Returns {@code out}. */
    public Rectangle bounds(Rectangle out) {
        return out.set(0f, 0f, worldWidth(), worldHeight());
    }

    /**
     * The collision grid, built on first use: a cell is solid if a tile in it has {@code solid=true} or lies on the
     * {@value #COLLISION_LAYER} layer, and one-way if a tile has {@code oneWay=true} and the cell is not solid.
     */
    public TileGrid collision() {
        if (collision == null) {
            collision = buildCollision();
        }
        return collision;
    }

    List<MapLayer> leafLayers() {
        return leaves;
    }

    GameTimeAnimatedTile.Clock clock() {
        return clock;
    }

    private TiledMapTileLayer tileLayer(String name) {
        for (MapLayer layer : leaves) {
            if (layer.getName() != null && layer.getName().equals(name) && layer instanceof TiledMapTileLayer t) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tiled map " + source + " has no tile layer \"" + name + "\"; layers: "
                + layerNames);
    }

    private TileGrid buildCollision() {
        TileGrid grid = new TileGrid(width, height, tileWidth, tileHeight);
        Map<TiledMapTile, Boolean> warned = new IdentityHashMap<>();
        for (MapLayer layer : leaves) {
            if (!(layer instanceof TiledMapTileLayer tiles)) {
                continue;
            }
            boolean collisionLayer = COLLISION_LAYER.equals(layer.getName());
            int w = Math.min(width, tiles.getWidth());
            int h = Math.min(height, tiles.getHeight());
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    TiledMapTileLayer.Cell cell = tiles.getCell(x, y);
                    if (cell == null || cell.getTile() == null) {
                        continue;
                    }
                    TiledMapTile tile = cell.getTile();
                    if (collisionLayer || flag(tile, SOLID, warned)) {
                        grid.setSolid(x, y, true);
                    } else if (flag(tile, ONE_WAY, warned)) {
                        grid.setOneWay(x, y, true);
                    }
                }
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid.solid(x, y)) {
                    grid.setOneWay(x, y, false);
                }
            }
        }
        LOG.debug("Tiled map " + source + ": collision grid " + width + "x" + height + ", " + grid.solidCount()
                + " solid, " + grid.oneWayCount() + " one-way");
        return grid;
    }

    private boolean flag(TiledMapTile tile, String property, Map<TiledMapTile, Boolean> warned) {
        Object value = tile.getProperties().get(property);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (warned.put(tile, Boolean.TRUE) == null) {
            LOG.warn("Tiled map " + source + ": tile " + tile.getId() + " has property \"" + property
                    + "\" of type " + value.getClass().getSimpleName() + "; expected bool, ignored");
        }
        return false;
    }

    private GameTimeAnimatedTile.Clock swapAnimatedTiles() {
        GameTimeAnimatedTile.Clock shared = null;
        Map<TiledMapTile, TiledMapTile> swaps = new IdentityHashMap<>();
        for (TiledMapTileSet tileset : map.getTileSets()) {
            for (TiledMapTile tile : tileset) {
                if (tile instanceof GameTimeAnimatedTile existing) {
                    shared = existing.clock();   // wrapped before: keep that clock
                }
            }
        }
        if (shared == null) {
            shared = new GameTimeAnimatedTile.Clock();
        }
        for (TiledMapTileSet tileset : map.getTileSets()) {
            List<TiledMapTile> animated = new ArrayList<>();
            for (TiledMapTile tile : tileset) {
                if (tile instanceof AnimatedTiledMapTile) {
                    animated.add(tile);
                }
            }
            for (TiledMapTile tile : animated) {
                TiledMapTile replacement = new GameTimeAnimatedTile((AnimatedTiledMapTile) tile, shared);
                swaps.put(tile, replacement);
                tileset.putTile(tile.getId(), replacement);
            }
        }
        if (!swaps.isEmpty()) {
            for (MapLayer layer : leaves) {
                if (layer instanceof TiledMapTileLayer tiles) {
                    for (int y = 0; y < tiles.getHeight(); y++) {
                        for (int x = 0; x < tiles.getWidth(); x++) {
                            TiledMapTileLayer.Cell cell = tiles.getCell(x, y);
                            if (cell != null && cell.getTile() != null && swaps.containsKey(cell.getTile())) {
                                cell.setTile(swaps.get(cell.getTile()));
                            }
                        }
                    }
                }
                for (MapObject object : layer.getObjects()) {
                    if (object instanceof TiledMapTileMapObject t && swaps.containsKey(t.getTile())) {
                        t.setTile(swaps.get(t.getTile()));
                    }
                }
            }
            LOG.debug("Tiled map " + source + ": " + swaps.size() + " animated tile(s) on game time");
        }
        return shared;
    }

    private static void flatten(MapLayers layers, List<MapLayer> out) {
        for (MapLayer layer : layers) {
            if (layer instanceof MapGroupLayer group) {
                flatten(group.getLayers(), out);
            } else {
                out.add(layer);
            }
        }
    }
}
