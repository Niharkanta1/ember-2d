package dev.ember2d.render;

import com.badlogic.gdx.graphics.g2d.Batch;
import dev.ember2d.core.PublicApi;

/**
 * Something other than sprites that draws into render layers (F-004), such as tile map layers (F-009). Registered with
 * {@link SpriteRenderSystem#addLayerContent}; for each render layer, in the renderer's layer order, the system first
 * calls every content, then draws that layer's sprites.
 */
@PublicApi
public interface LayerContent {

    /** Draws this content's part of a render layer. The batch is begun with the world camera's projection. */
    void drawLayer(String layer, Batch batch);
}
