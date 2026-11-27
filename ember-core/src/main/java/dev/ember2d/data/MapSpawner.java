package dev.ember2d.data;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.Sprite;
import dev.ember2d.tilemap.TileMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Spawns prefab instances from a Tiled map's object layers (F-009, F-010). Every object with a class (TMX
 * {@code type}) becomes an instance of that prefab; objects without a class are not entities but can be referenced.
 *
 * <ul>
 *   <li>Position: the object's bottom-centre in world units (a point: its position); Tiled rotation (clockwise
 *       degrees) becomes {@code Transform.rotation}; the object name becomes the entity name.</li>
 *   <li>Tile objects: the tile image is editor-only; flips set {@code Sprite.flipX/flipY}. A prefab without a
 *       {@code Sprite} gets one with the tile's image, anchored bottom-centre.</li>
 *   <li>Custom properties override fields: {@code Component.field}, a bare field name unique among the prefab's
 *       components, or a class-typed property named after a component (each member sets that component's field).</li>
 *   <li>Object references: an {@code EntityRef} field gets the spawned entity; {@code Vector2}, {@code Rectangle} and
 *       {@code float[]} fields get the referenced object's position, bounds or vertices in world units.</li>
 * </ul>
 *
 * Two passes: all entities are created first, then overrides and references are applied, then {@link EntitySpawned}
 * is published for each. Problems are collected for the whole map; if there are any, the spawned entities are
 * destroyed and a {@link PrefabException} lists them all.
 */
@PublicApi
public final class MapSpawner {

    private static final Set<String> RESERVED = Set.of("id", "x", "y", "width", "height", "type", "rotation", "gid");

    private final PrefabLibrary prefabs;

    public MapSpawner(PrefabLibrary prefabs) {
        this.prefabs = Objects.requireNonNull(prefabs, "prefabs");
    }

    /**
     * Spawns all placed objects of the map into the world.
     *
     * @return the spawned entities, in map order
     * @throws PrefabException listing every problem (nothing stays spawned)
     */
    public IntArray spawn(World world, TileMap map) {
        float ppu = map.properties().get("tilewidth", 1, Integer.class) / map.tileWidth();
        List<MapObject> objects = new ArrayList<>();
        collect(map.tiled().getLayers(), objects);

        List<String> problems = new ArrayList<>();
        Map<MapObject, PrefabLibrary.Instance> spawned = new IdentityHashMap<>();
        List<PrefabLibrary.Instance> order = new ArrayList<>();
        for (MapObject object : objects) {
            String prefab = object.getProperties().get("type", String.class);
            if (prefab == null || prefab.isBlank()) {
                continue;
            }
            MapProperties p = object.getProperties();
            float x = number(p, "x");
            float y = number(p, "y");
            float w = number(p, "width");
            try {
                PrefabLibrary.Instance instance = prefabs.create(world, prefab, (x + w / 2f) / ppu, y / ppu);
                place(world, instance, object, ppu);
                spawned.put(object, instance);
                order.add(instance);
            } catch (PrefabException e) {
                for (String problem : e.problems()) {
                    problems.add(where(map, object) + ": " + problem);
                }
            } catch (IllegalArgumentException e) {
                problems.add(where(map, object) + ": " + e.getMessage());
            }
        }

        for (MapObject object : objects) {
            PrefabLibrary.Instance instance = spawned.get(object);
            if (instance == null) {
                continue;
            }
            MapProperties p = object.getProperties();
            // Class-typed properties first, plain ones after: with a generated project (I-245) libGDX fills in the
            // object class's members as defaults, and a plain "Component.field" or "field" property must still win.
            for (int pass = 0; pass < 2; pass++) {
                for (Iterator<String> keys = p.getKeys(); keys.hasNext();) {
                    String key = keys.next();
                    Object value = p.get(key);
                    if (RESERVED.contains(key) || (value instanceof MapProperties) != (pass == 0)) {
                        continue;
                    }
                    String where = where(map, object) + ": property \"" + key + "\"";
                    if (value instanceof MapProperties members) {
                        classProperty(world, map, instance, key, members, spawned, ppu, problems, where);
                    } else {
                        set(world, map, instance, key, value, spawned, ppu, problems, where);
                    }
                }
            }
        }

        if (!problems.isEmpty()) {
            for (PrefabLibrary.Instance instance : order) {
                world.destroy(instance.entity);
            }
            throw new PrefabException("Cannot place entities from Tiled map " + map.source(), problems);
        }
        IntArray entities = new IntArray(order.size());
        for (PrefabLibrary.Instance instance : order) {
            prefabs.finish(world, instance);
            entities.add(instance.entity);
        }
        return entities;
    }

    private void place(World world, PrefabLibrary.Instance instance, MapObject object, float ppu) {
        if (object.getName() != null && !object.getName().isBlank()) {
            world.setName(instance.entity, object.getName());
        }
        Transform t = (Transform) instance.components.get("Transform");
        Float rotation = object.getProperties().get("rotation", Float.class);
        if (rotation != null) {
            t.rotation = -rotation;
            t.previousRotation = t.rotation;
        }
        if (object instanceof TiledMapTileMapObject tile) {
            Sprite sprite = (Sprite) instance.components.get("Sprite");
            if (sprite == null) {
                sprite = new Sprite();
                TextureRegion region = tile.getTile().getTextureRegion();
                sprite.region = region;
                sprite.origin.set(region.getRegionWidth() / 2f, 0f);
                instance.components.put("Sprite", sprite);
                world.add(instance.entity, sprite);
            }
            sprite.flipX = tile.isFlipHorizontally();
            sprite.flipY = tile.isFlipVertically();
        }
    }

    private void classProperty(World world, TileMap map, PrefabLibrary.Instance instance, String key,
            MapProperties members, Map<MapObject, PrefabLibrary.Instance> spawned, float ppu, List<String> problems,
            String where) {
        if (!instance.components.containsKey(key)) {
            problems.add(where + ": a class property must be named after a component of prefab \""
                    + instance.prefab.id() + "\" " + instance.components.keySet() + " (its members set that "
                    + "component's fields)");
            return;
        }
        for (Iterator<String> it = members.getKeys(); it.hasNext();) {
            String member = it.next();
            if (member.equals("type")) {
                continue;   // the class name, added by libGDX
            }
            set(world, map, instance, key + "." + member, members.get(member), spawned, ppu, problems,
                    where + " member \"" + member + "\"");
        }
    }

    private void set(World world, TileMap map, PrefabLibrary.Instance instance, String key, Object value,
            Map<MapObject, PrefabLibrary.Instance> spawned, float ppu, List<String> problems, String where) {
        if (value instanceof MapProperties) {
            problems.add(where + ": nested class properties are not supported");
            return;
        }
        if (value instanceof MapObject target) {
            FieldSetter setter = prefabs.setterFor(instance, key);
            if (setter != null) {
                switch (setter.kind) {
                    case ENTITY_REF -> {
                        PrefabLibrary.Instance other = spawned.get(target);
                        if (other == null) {
                            problems.add(where + ": references " + describe(target) + ", which has no class and is "
                                    + "not an entity");
                            return;
                        }
                        value = world.ref(other.entity);
                    }
                    case VECTOR2 -> value = new Vector2(number(target.getProperties(), "x") / ppu,
                            number(target.getProperties(), "y") / ppu);
                    case RECTANGLE -> value = bounds(target, ppu);
                    case FLOATS -> {
                        float[] vertices = vertices(target);
                        if (vertices == null) {
                            problems.add(where + ": references " + describe(target) + ", which is not a polyline or "
                                    + "polygon");
                            return;
                        }
                        for (int i = 0; i < vertices.length; i++) {
                            vertices[i] /= ppu;
                        }
                        value = vertices;
                    }
                    default -> {
                        problems.add(where + ": an object reference cannot set " + setter.typeName() + " field \""
                                + key + "\"");
                        return;
                    }
                }
            }
        }
        prefabs.override(instance, key, value, problems, where);
    }

    private static Rectangle bounds(MapObject object, float ppu) {
        if (object instanceof RectangleMapObject r) {
            Rectangle rect = r.getRectangle();
            return new Rectangle(rect.x / ppu, rect.y / ppu, rect.width / ppu, rect.height / ppu);
        }
        MapProperties p = object.getProperties();
        return new Rectangle(number(p, "x") / ppu, number(p, "y") / ppu, number(p, "width") / ppu,
                number(p, "height") / ppu);
    }

    private static float[] vertices(MapObject object) {
        if (object instanceof PolylineMapObject line) {
            return line.getPolyline().getTransformedVertices().clone();
        }
        if (object instanceof PolygonMapObject polygon) {
            return polygon.getPolygon().getTransformedVertices().clone();
        }
        return null;
    }

    private static float number(MapProperties p, String key) {
        Object v = p.get(key);
        return v instanceof Number n ? n.floatValue() : 0f;
    }

    private static String where(TileMap map, MapObject object) {
        return "Tiled map " + map.source() + ": " + describe(object);
    }

    private static String describe(MapObject object) {
        Object id = object.getProperties().get("id");
        return "object " + (object.getName() == null ? "(unnamed)" : "\"" + object.getName() + "\"") + " (id " + id + ")";
    }

    private static void collect(MapLayers layers, List<MapObject> out) {
        for (MapLayer layer : layers) {
            if (layer instanceof MapGroupLayer group) {
                collect(group.getLayers(), out);
            } else if (!(layer instanceof TiledMapTileLayer) && !(layer instanceof TiledMapImageLayer)) {
                for (MapObject object : layer.getObjects()) {
                    out.add(object);
                }
            }
        }
    }
}
