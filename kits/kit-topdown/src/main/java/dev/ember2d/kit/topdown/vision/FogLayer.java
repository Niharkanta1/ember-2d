package dev.ember2d.kit.topdown.vision;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.camera.CameraRig;
import dev.ember2d.core.PublicApi;
import dev.ember2d.render.LayerContent;
import java.util.Objects;

/**
 * The fog outside the field of view (F-030), drawn as content of one render layer above the world. It is plain
 * geometry in the world pass, no frame buffer: per ray, a band that fades from clear to {@code darkness} over the last
 * {@link VisionSettings#edgeSoftness} of the ray, then solid fog out past the edge of the view. In the pixel-perfect
 * render modes the world pass is drawn at the virtual resolution, so the fog edge is pixelated like the art.
 *
 * <p>Attach it with {@code sprites.addLayerContent(fog)} and declare its layer after the world layers. It owns a 1x1
 * white texture (managed, so it survives an Android context loss); dispose it with the scene.
 */
@PublicApi
public final class FogLayer implements LayerContent, Disposable {

    private static final int FLOATS_PER_QUAD = 20;   // SpriteBatch: x, y, colour, u, v for 4 corners

    private final VisionField field;
    private final CameraRig camera;
    private final String layer;
    private final float[] vertices;
    private Texture white;
    /** False draws nothing (and {@link VisionField#enabled} false shows everything as visible). */
    public boolean enabled = true;

    public FogLayer(VisionField field, CameraRig camera, String layer) {
        this.field = Objects.requireNonNull(field, "field");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.layer = Objects.requireNonNull(layer, "layer");
        this.vertices = new float[field.rayCount() * 2 * FLOATS_PER_QUAD];
    }

    @Override
    public void drawLayer(String layerName, Batch batch) {
        if (!enabled || !field.enabled || !layer.equals(layerName)) {
            return;
        }
        if (white == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            white = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        }
        VisionSettings s = field.settings();
        Color c = s.fogColor;
        float clear = Color.toFloatBits(c.r, c.g, c.b, 0f);
        float dark = Color.toFloatBits(c.r, c.g, c.b, s.darkness);
        float ox = field.originX();
        float oy = field.originY();
        float far = farRadius(ox, oy);
        int rays = field.rayCount();
        int v = 0;
        for (int i = 0; i < rays; i++) {
            int j = (i + 1) % rays;
            float di = field.distance(i);
            float dj = field.distance(j);
            float ii = Math.max(0f, di - s.edgeSoftness);
            float ij = Math.max(0f, dj - s.edgeSoftness);
            float xi = field.directionX(i);
            float yi = field.directionY(i);
            float xj = field.directionX(j);
            float yj = field.directionY(j);
            // the soft edge: clear inside, fog at the ray's end
            v = corner(v, ox + xi * ii, oy + yi * ii, clear);
            v = corner(v, ox + xj * ij, oy + yj * ij, clear);
            v = corner(v, ox + xj * dj, oy + yj * dj, dark);
            v = corner(v, ox + xi * di, oy + yi * di, dark);
            // solid fog beyond, past the edge of the view
            v = corner(v, ox + xi * di, oy + yi * di, dark);
            v = corner(v, ox + xj * dj, oy + yj * dj, dark);
            v = corner(v, ox + xj * far, oy + yj * far, dark);
            v = corner(v, ox + xi * far, oy + yi * far, dark);
        }
        batch.draw(white, vertices, 0, v);
    }

    private int corner(int v, float x, float y, float colour) {
        vertices[v] = x;
        vertices[v + 1] = y;
        vertices[v + 2] = colour;
        vertices[v + 3] = 0.5f;
        vertices[v + 4] = 0.5f;
        return v + 5;
    }

    /** A radius from the viewer that reaches past every corner of the view (the rays must cover the screen). */
    private float farRadius(float ox, float oy) {
        float halfW = camera.viewWidth() / 2f * Math.max(1f, camera.zoom) + 1f;
        float halfH = camera.viewHeight() / 2f * Math.max(1f, camera.zoom) + 1f;
        float dx = Math.abs(ox - camera.position.x) + halfW;
        float dy = Math.abs(oy - camera.position.y) + halfH;
        return (float) Math.sqrt(dx * dx + dy * dy) + 2f;
    }

    @Override
    public void dispose() {
        if (white != null) {
            white.dispose();
            white = null;
        }
    }
}
