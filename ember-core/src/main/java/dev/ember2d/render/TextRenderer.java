package dev.ember2d.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectSet;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.List;
import java.util.Objects;

/**
 * Draws every entity with a {@link Transform} and a {@link Text} (F-004, I-140) as a layer overlay of a
 * {@link SpriteRenderSystem}: in each render layer, after that layer's sprites. Text is drawn at the interpolated
 * position in world units, snapped to whole virtual pixels in the pixel-perfect modes. Glyphs are laid out again only
 * when the text, font, scale, alignment, wrap width or colour changes.
 */
@PublicApi
public final class TextRenderer implements LayerContent {

    private static final Log LOG = Log.get("render");

    private final World world;
    private final Family family;
    private final Renderer renderer;
    private final Assets assets;
    private final Time time;
    private final Vector2 position = new Vector2();
    private final ObjectSet<String> warned = new ObjectSet<>();

    public TextRenderer(World world, Renderer renderer, Assets assets, Time time) {
        this.world = Objects.requireNonNull(world, "world");
        this.family = world.family(Transform.class, Text.class);
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.time = Objects.requireNonNull(time, "time");
    }

    /** Registers this renderer as a layer overlay of the sprite system. Returns this. */
    public TextRenderer attachTo(SpriteRenderSystem sprites) {
        sprites.addLayerOverlay(this);
        return this;
    }

    @Override
    public void drawLayer(String layer, Batch batch) {
        IntArray entities = family.entities();
        List<RenderLayer> layers = null;
        for (int i = 0; i < entities.size; i++) {
            int entity = entities.get(i);
            Text text = world.get(entity, Text.class);
            if (layers == null) {
                layers = renderer.layers();
            }
            if (!layer.equals(resolveLayer(text.layer, layers)) || text.font == null || text.text == null
                    || text.text.isEmpty()) {
                continue;
            }
            draw(batch, entity, text);
        }
    }

    private void draw(Batch batch, int entity, Text text) {
        if (!text.font.equals(text.resolvedId) || text.resolvedGeneration != assets.generation()) {
            text.resolved = assets.get(text.font, BitmapFont.class);   // first use, another font, or a hot reload
            text.resolvedId = text.font;
            text.resolvedGeneration = assets.generation();
        }
        BitmapFont font = text.resolved;
        float ppu = renderer.pixelsPerUnit();
        float scale = text.scale / ppu;
        font.setUseIntegerPositions(false);
        font.getData().setScale(scale);
        float colour = text.color.toFloatBits();
        if (font != text.laidFont || !text.text.equals(text.laidText) || scale != text.laidScale
                || text.align != text.laidAlign || text.wrapWidth != text.laidWrap || colour != text.laidColor) {
            text.layout.setText(font, text.text, 0, text.text.length(), text.color, text.wrapWidth,
                    TextDraw.align(text.align), text.wrapWidth > 0f, null);
            text.laidFont = font;
            text.laidText = text.text;
            text.laidScale = scale;
            text.laidAlign = text.align;
            text.laidWrap = text.wrapWidth;
            text.laidColor = colour;
            text.layoutCount++;
        }
        world.get(entity, Transform.class).interpolated(time.alpha(), position);
        float x = TextDraw.boxX(position.x + text.offset.x, text.align, text.wrapWidth);
        float y = position.y + text.offset.y + font.getCapHeight();
        if (renderer.mode() != RenderMode.SMOOTH) {
            x = Math.round(x * ppu) / ppu;
            y = Math.round(y * ppu) / ppu;
        }
        font.draw(batch, text.layout, x, y);
    }

    private String resolveLayer(String name, List<RenderLayer> layers) {
        String fallback = layers.get(0).name();
        for (int i = 0; i < layers.size(); i++) {
            String n = layers.get(i).name();
            if (n.equals(name)) {
                return name;
            }
            if (n.equals("world")) {
                fallback = n;
            }
        }
        if (warned.add(String.valueOf(name))) {
            LOG.warn("Unknown render layer \"" + name + "\" for text; drawing on \"" + fallback + "\"");
        }
        return fallback;
    }
}
