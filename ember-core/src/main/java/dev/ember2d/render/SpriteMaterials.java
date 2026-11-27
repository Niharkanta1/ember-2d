package dev.ember2d.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.core.PublicApi;

/**
 * Draws sprites with material effects (F-026): hit flash, outline and palette swap. {@link SpriteRenderSystem} calls
 * {@link #begin} before a sprite with an effect and {@link #end} after it. The engine's renderer provides the
 * built-in implementation ({@link Renderer#materials()}).
 */
@PublicApi
public interface SpriteMaterials {

    /**
     * Prepares the batch for one sprite with an effect.
     *
     * @param palette the sprite's palette texture, or null
     * @return false when the effect cannot be drawn (no shader); the sprite is then drawn plainly
     */
    boolean begin(Batch batch, Sprite sprite, TextureRegion region, Texture palette);

    /** Restores the batch after the sprite. */
    void end(Batch batch);

    /** Materials that draw nothing special (tests, renderers without shaders). */
    SpriteMaterials NONE = new SpriteMaterials() {
        @Override
        public boolean begin(Batch batch, Sprite sprite, TextureRegion region, Texture palette) {
            return false;
        }

        @Override
        public void end(Batch batch) {
        }
    };
}
