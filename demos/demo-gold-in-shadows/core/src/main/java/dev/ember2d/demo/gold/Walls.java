package dev.ember2d.demo.gold;

import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Solid;
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
