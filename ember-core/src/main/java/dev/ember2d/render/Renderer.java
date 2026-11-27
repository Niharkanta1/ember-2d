package dev.ember2d.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;
import dev.ember2d.camera.CameraRig;
import dev.ember2d.core.PublicApi;
import java.util.List;

/**
 * The world renderer (F-004), available as {@code context.renderer()}. During {@code RENDER} the batch is already
 * begun with the world camera: render systems just draw. The engine then scales the world image to the window.
 */
@PublicApi
public interface Renderer {

    /** The sprite batch, begun with the world camera during {@code RENDER}. */
    Batch batch();

    /** Draws sprites with material effects (F-026); {@link SpriteMaterials#NONE} when the renderer has no shaders. */
    default SpriteMaterials materials() {
        return SpriteMaterials.NONE;
    }

    /** The libGDX camera the world is drawn with; driven by {@link #camera()} every frame. */
    OrthographicCamera worldCamera();

    /** World size and screen bounds of the world image (for libGDX code that expects a viewport). */
    Viewport viewport();

    /** The game camera: position, zoom, shake. */
    CameraRig camera();

    /**
     * Replaces the render layers; they are drawn in this order. The default is one {@code "world"} layer sorted by z.
     *
     * @throws IllegalArgumentException if empty or a name repeats
     */
    void setLayers(RenderLayer... layers);

    List<RenderLayer> layers();

    /** Converts a window position (pixels, origin top-left, as libGDX input reports it) to world units. */
    Vector2 screenToWorld(float screenX, float screenY, Vector2 out);

    /**
     * The part of the window clear of notches, cut-outs and rounded corners (F-022, I-225), in back-buffer pixels with
     * y up. The whole window when the device reports no insets. Returns {@code out}.
     */
    Rectangle safeArea(Rectangle out);

    /** Whole-number scale of the world image (1 when scaling down, and in {@code SMOOTH}). */
    int scale();

    /** Virtual pixels per world unit. */
    float pixelsPerUnit();

    RenderMode mode();
}
