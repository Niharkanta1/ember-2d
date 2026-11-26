package dev.ember2d.tilemap;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.ObjectSet;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.render.LayerContent;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.Renderer;
import dev.ember2d.render.SpriteRenderSystem;
import java.util.List;
import java.util.Objects;

/**
 * Draws a {@link TileMap}'s tile and image layers inside the engine's render layers (F-009). Each Tiled layer goes to
 * the render layer named by its {@code renderLayer} property (or its group's), default {@code world}; in each render
 * layer, map layers are drawn in Tiled order before that layer's sprites. Hidden layers and the
 * {@value TileMap#COLLISION_LAYER} layer are not drawn. Parallax, offsets and opacity set in Tiled are applied by
 * libGDX's {@link OrthogonalTiledMapRenderer}, which draws through the renderer's batch; animated tiles follow game
 * time. Nothing is allocated per frame.
 */
@PublicApi
public final class TileMapRenderer implements LayerContent {

    /** The Tiled layer property naming the render layer. */
    public static final String RENDER_LAYER = "renderLayer";
    /** The render layer used when a Tiled layer names none. */
    public static final String DEFAULT_LAYER = "world";

    private static final Log LOG = Log.get("tilemap");

    private final TileMap map;
    private final Renderer renderer;
    private final Time time;
    private final OrthogonalTiledMapRenderer tiles;
    private final MapLayer[] layers;
    private final String[] targets;
    private final ObjectSet<String> warned = new ObjectSet<>();

    public TileMapRenderer(TileMap map, Renderer renderer, Time time) {
        this.map = Objects.requireNonNull(map, "map");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.time = Objects.requireNonNull(time, "time");
        this.tiles = new OrthogonalTiledMapRenderer(map.tiled(), 1f / renderer.pixelsPerUnit(), renderer.batch());
        List<MapLayer> leaves = map.leafLayers();
        this.layers = new MapLayer[leaves.size()];
        this.targets = new String[leaves.size()];
        for (int i = 0; i < layers.length; i++) {
            layers[i] = leaves.get(i);
            targets[i] = target(layers[i]);
        }
    }

    /** Registers this renderer with a sprite system so map layers join its layer order. Returns this. */
    public TileMapRenderer attachTo(SpriteRenderSystem sprites) {
        sprites.addLayerContent(this);
        return this;
    }

    /** The map being drawn. */
    public TileMap map() {
        return map;
    }

    @Override
    public void drawLayer(String layer, Batch batch) {
        map.clock().set(time.elapsed());
        boolean viewSet = false;
        List<RenderLayer> renderLayers = renderer.layers();
        for (int i = 0; i < layers.length; i++) {
            MapLayer mapLayer = layers[i];
            if (!layer.equals(resolve(targets[i], renderLayers)) || !visible(mapLayer)
                    || TileMap.COLLISION_LAYER.equals(mapLayer.getName())) {
                continue;
            }
            if (!viewSet) {
                setViewBounds();
                viewSet = true;
            }
            if (mapLayer instanceof TiledMapTileLayer tileLayer) {
                tiles.renderTileLayer(tileLayer);
            } else if (mapLayer instanceof TiledMapImageLayer imageLayer) {
                tiles.renderImageLayer(imageLayer);
            }
        }
    }

    /**
     * Sets the culling bounds from the world camera, like {@code setView(camera)} but without touching the shared
     * batch's projection (that would flush the batch every frame).
     */
    private void setViewBounds() {
        OrthographicCamera camera = renderer.worldCamera();
        float width = camera.viewportWidth * camera.zoom;
        float height = camera.viewportHeight * camera.zoom;
        float w = width * Math.abs(camera.up.y) + height * Math.abs(camera.up.x);
        float h = height * Math.abs(camera.up.y) + width * Math.abs(camera.up.x);
        tiles.getViewBounds().set(camera.position.x - w / 2, camera.position.y - h / 2, w, h);
    }

    private String resolve(String target, List<RenderLayer> renderLayers) {
        String fallback = null;
        for (int i = 0; i < renderLayers.size(); i++) {
            String name = renderLayers.get(i).name();
            if (name.equals(target)) {
                return target;
            }
            if (fallback == null && name.equals(DEFAULT_LAYER)) {
                fallback = name;
            }
        }
        if (fallback == null) {
            fallback = renderLayers.get(0).name();
        }
        if (warned.add(target)) {
            LOG.warn("Tiled map " + map.source() + ": unknown render layer \"" + target + "\"; drawing on \""
                    + fallback + "\"");
        }
        return fallback;
    }

    private static boolean visible(MapLayer layer) {
        for (MapLayer l = layer; l != null; l = l.getParent()) {
            if (!l.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static String target(MapLayer layer) {
        for (MapLayer l = layer; l != null; l = l.getParent()) {
            Object value = l.getProperties().get(RENDER_LAYER);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return DEFAULT_LAYER;
    }
}
