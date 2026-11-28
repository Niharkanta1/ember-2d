package dev.ember2d.demo.gold;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Solid;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.tilemap.TileMap;

/**
 * Builds the level's walls from the map's {@code walls} object layer (F-029): one {@code Solid} box per rectangle,
 * exactly the original game's collision boxes (see {@code assets-src/demo-gold-in-shadows/ConvertLevels.java}).
 * Boxes with {@code opaque} block sight too ({@link #SIGHT_LAYER}); low props (barrels, crates, vases, chests) only
 * block movement ({@link #PROP_LAYER}). The exit door's leaf is tagged {@link #DOOR_TAG} and removed when it opens.
 */
final class Walls {

    /** Collision layer of walls that block sight. */
    static final int SIGHT_LAYER = 3;
    /** Collision layer of low props: they block movement, not sight. */
    static final int PROP_LAYER = 0;
    /** Mask of everything that blocks movement. */
    static final int MOVE_MASK = (1 << SIGHT_LAYER) | (1 << PROP_LAYER);
    /** Mask of what blocks sight. */
    static final int SIGHT_MASK = 1 << SIGHT_LAYER;
    static final String DOOR_TAG = "door";

    private Walls() {
    }

    /**
     * A tile grid for path finding (kit-topdown's A*): a cell is blocked when wall boxes cover at least half of it.
     * Thin wall overlays and low props leave their cells open (movement still slides along their exact boxes); the
     * level format keeps passages at least two tiles wide, so routes always exist where the original had them.
     */
    static TileGrid navigation(TileMap map) {
        MapLayer layer = map.tiled().getLayers().get("walls");
        float tileUnits = map.tileWidth();
        float tilePixels = map.tiled().getProperties().get("tilewidth", 16, Integer.class);
        float scale = tileUnits / tilePixels;
        int w = map.width();
        int h = map.height();
        float[] covered = new float[w * h];
        for (MapObject object : layer.getObjects()) {
            if (!(object instanceof RectangleMapObject r)) {
                continue;
            }
            Rectangle px = r.getRectangle();
            float x0 = px.x * scale;
            float y0 = px.y * scale;
            float x1 = x0 + px.width * scale;
            float y1 = y0 + px.height * scale;
            for (int cy = Math.max(0, (int) Math.floor(y0)); cy < Math.min(h, (int) Math.ceil(y1)); cy++) {
                for (int cx = Math.max(0, (int) Math.floor(x0)); cx < Math.min(w, (int) Math.ceil(x1)); cx++) {
                    float ox = Math.min(x1, cx + 1f) - Math.max(x0, cx);
                    float oy = Math.min(y1, cy + 1f) - Math.max(y0, cy);
                    if (ox > 0f && oy > 0f) {
                        covered[cy * w + cx] += ox * oy;
                    }
                }
            }
        }
        TileGrid grid = new TileGrid(w, h, tileUnits, tileUnits);
        for (int cy = 0; cy < h; cy++) {
            for (int cx = 0; cx < w; cx++) {
                grid.setSolid(cx, cy, covered[cy * w + cx] >= 0.5f);
            }
        }
        return grid;
    }

    /** Adds every wall box of the map as an entity; returns how many. */
    static int build(World world, TileMap map) {
        MapLayer layer = map.tiled().getLayers().get("walls");
        if (layer == null) {
            throw new IllegalStateException(map.source() + ": no \"walls\" object layer");
        }
        float tileUnits = map.tileWidth();   // one tile in world units
        float tilePixels = map.tiled().getProperties().get("tilewidth", 16, Integer.class);
        float scale = tileUnits / tilePixels;   // map pixels to world units
        int count = 0;
        for (MapObject object : layer.getObjects()) {
            if (!(object instanceof RectangleMapObject r)) {
                continue;
            }
            Rectangle px = r.getRectangle();
            int e = world.create();
            world.add(e, new Transform()).teleport(px.x * scale, px.y * scale);
            Collider c = world.add(e, new Collider());
            c.box.set(0f, 0f, px.width * scale, px.height * scale);
            boolean opaque = object.getProperties().get("opaque", Boolean.TRUE, Boolean.class);
            c.layer = opaque ? SIGHT_LAYER : PROP_LAYER;
            world.add(e, new Solid());
            if (object.getProperties().get("door", Boolean.FALSE, Boolean.class)) {
                world.tag(e, DOOR_TAG);
            }
            count++;
        }
        return count;
    }
}
