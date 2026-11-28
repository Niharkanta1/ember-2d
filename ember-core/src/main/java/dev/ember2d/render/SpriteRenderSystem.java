package dev.ember2d.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectSet;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.internal.EntitySort;
import java.util.List;
import java.util.Objects;

/**
 * Draws every entity with a {@link Transform} and a {@link Sprite} in {@code RENDER} (F-004): grouped by layer in
 * the renderer's layer order, sorted per layer, at the interpolated transform. In the pixel-perfect modes draw
 * positions are snapped to whole virtual pixels. Sorting and drawing do not allocate per frame.
 *
 * <p>Other content joins the same layer order: {@link #addLayerContent} (tile map layers) is drawn before a layer's
 * sprites, {@link #addLayerOverlay} (text) after them, each in registration order.
 */
@PublicApi
public final class SpriteRenderSystem extends EntitySystem {

    private static final Log LOG = Log.get("render");

    private final Renderer renderer;
    private final Assets assets;
    private final Time time;
    private final Array<IntArray> buckets = new Array<>(true, 4);
    private final Array<FloatArray> keys = new Array<>(true, 4);
    private final EntitySort sorter = new EntitySort();
    private final ObjectSet<String> warnedLayers = new ObjectSet<>();
    private final Vector2 position = new Vector2();
    private final Array<LayerContent> contents = new Array<>(true, 2, LayerContent[]::new);
    private final Array<LayerContent> overlays = new Array<>(true, 2, LayerContent[]::new);
    private List<RenderLayer> layers;
    private int fallbackLayer;
    private boolean warnedMissingRegion;

    public SpriteRenderSystem(World world, Renderer renderer, Assets assets, Time time) {
        super(Phase.RENDER, world.family(Transform.class, Sprite.class));
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.assets = assets;
        this.time = Objects.requireNonNull(time, "time");
    }

    /** Adds content drawn before the sprites of each layer (no effect if already added). */
    public void addLayerContent(LayerContent content) {
        Objects.requireNonNull(content, "content");
        if (!contents.contains(content, true)) {
            contents.add(content);
        }
    }

    /** Removes content added with {@link #addLayerContent}. */
    public void removeLayerContent(LayerContent content) {
        contents.removeValue(content, true);
    }

    /** Adds content drawn after the sprites of each layer, such as text (no effect if already added). */
    public void addLayerOverlay(LayerContent overlay) {
        Objects.requireNonNull(overlay, "overlay");
        if (!overlays.contains(overlay, true)) {
            overlays.add(overlay);
        }
    }

    /** Removes an overlay added with {@link #addLayerOverlay}. */
    public void removeLayerOverlay(LayerContent overlay) {
        overlays.removeValue(overlay, true);
    }

    @Override
    protected void begin() {
        layers = renderer.layers();
        while (buckets.size < layers.size()) {
            buckets.add(new IntArray(true, 64));
            keys.add(new FloatArray(true, 64));
        }
        fallbackLayer = 0;
        for (int i = 0; i < layers.size(); i++) {
            buckets.get(i).clear();
            keys.get(i).clear();
            if (layers.get(i).name().equals("world")) {
                fallbackLayer = i;
            }
        }
    }

    @Override
    protected void process(int entity, float delta) {
        Sprite sprite = world().get(entity, Sprite.class);
        int layer = layerIndex(sprite.layer);
        float key = switch (layers.get(layer).sort()) {
            case Z -> sprite.z;
            case Y_SORT -> -world().get(entity, Transform.class).position.y;
            case NONE -> 0f;
        };
        buckets.get(layer).add(entity);
        keys.get(layer).add(key);
    }

    @Override
    protected void end() {
        Batch batch = renderer.batch();
        for (int l = 0; l < layers.size(); l++) {
            String layerName = layers.get(l).name();
            for (int c = 0; c < contents.size; c++) {
                batch.setColor(Color.WHITE);   // content may read the batch colour (libGDX's tile renderer does)
                contents.items[c].drawLayer(layerName, batch);
            }
            IntArray bucket = buckets.get(l);
            if (layers.get(l).sort() != SortMode.NONE) {
                sorter.sort(bucket, keys.get(l));
            }
            for (int i = 0; i < bucket.size; i++) {
                draw(batch, bucket.get(i));
            }
            for (int c = 0; c < overlays.size; c++) {
                batch.setColor(Color.WHITE);
                overlays.items[c].drawLayer(layerName, batch);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void draw(Batch batch, int entity) {
        Sprite sprite = world().get(entity, Sprite.class);
        if (!sprite.visible) {
            return;   // hidden (fog of war, I-610): skipped, keeps its region and tint
        }
        TextureRegion region = sprite.region;
        if (region != null && region == sprite.resolvedRegion && assets != null
                && sprite.resolvedGeneration != assets.generation()) {
            region = null;   // filled from regionId before a hot reload: resolve it again
        }
        if (region == null && sprite.regionId != null && assets != null) {
            region = assets.region(sprite.regionId);
            sprite.region = region;
            sprite.resolvedRegion = region;
            sprite.resolvedGeneration = assets.generation();
        }
        if (region == null) {
            if (!warnedMissingRegion) {
                warnedMissingRegion = true;
                LOG.warn("A Sprite has neither region nor regionId; it is not drawn (logged once)");
            }
            return;
        }
        Transform transform = world().get(entity, Transform.class);
        float ppu = renderer.pixelsPerUnit();
        transform.interpolated(time.alpha(), position);
        // Whitespace-stripped atlas regions (and trimmed animation frames) sit inside their original frame: the origin
        // is relative to the original frame, the quad covers only the packed pixels at their offset.
        float offsetX = 0f;
        float offsetY = 0f;
        if (region instanceof TextureAtlas.AtlasRegion atlasRegion) {
            boolean flipX = sprite.flipX != region.isFlipX();
            boolean flipY = sprite.flipY != region.isFlipY();
            offsetX = flipX ? atlasRegion.originalWidth - atlasRegion.offsetX - atlasRegion.packedWidth
                    : atlasRegion.offsetX;
            offsetY = flipY ? atlasRegion.originalHeight - atlasRegion.offsetY - atlasRegion.packedHeight
                    : atlasRegion.offsetY;
        }
        float originX = (sprite.origin.x - offsetX) / ppu;
        float originY = (sprite.origin.y - offsetY) / ppu;
        float x = position.x - originX;
        float y = position.y - originY;
        if (renderer.mode() != RenderMode.SMOOTH) {
            x = Math.round(x * ppu) / ppu;
            y = Math.round(y * ppu) / ppu;
        }
        // Materials (F-026): only sprites with an effect switch shaders; the rest keep batching.
        boolean material = sprite.hasMaterial() && renderer.materials().begin(batch, sprite, region,
                sprite.paletteId == null || assets == null ? null : assets.get(sprite.paletteId, Texture.class));
        batch.setColor(sprite.tint);
        batch.draw(region.getTexture(), x, y, originX, originY,
                region.getRegionWidth() / ppu, region.getRegionHeight() / ppu,
                transform.scale.x, transform.scale.y, transform.interpolatedRotation(time.alpha()),
                region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
                sprite.flipX != region.isFlipX(), sprite.flipY != region.isFlipY());
        if (material) {
            renderer.materials().end(batch);
        }
    }

    private int layerIndex(String name) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).name().equals(name)) {
                return i;
            }
        }
        if (warnedLayers.add(String.valueOf(name))) {
            LOG.warn("Unknown render layer \"" + name + "\"; drawing on \"" + layers.get(fallbackLayer).name() + "\"");
        }
        return fallbackLayer;
    }
}
