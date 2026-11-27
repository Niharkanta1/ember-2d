package dev.ember2d.render.internal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import dev.ember2d.camera.CameraRig;
import dev.ember2d.core.Time;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.RenderMode;
import dev.ember2d.render.Renderer;
import dev.ember2d.render.SpriteMaterials;
import dev.ember2d.render.SortMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The engine's {@link Renderer} (F-004). Per frame the engine calls {@link #beginFrame()} before the render systems
 * and {@link #endFrame()} after them; {@link #resize(int, int)} when the window changes.
 *
 * <p>Pixel-perfect modes draw the world into a {@link RenderTarget} at the virtual resolution (wider in fill mode),
 * with the camera snapped to whole virtual pixels, then draw that image to the window at the layout's whole-number
 * scale with black bars. {@code SMOOTH} draws straight into the fitted window rectangle.
 */
public final class GdxRenderer implements Renderer, Disposable {

    private final MaterialShader materials = new MaterialShader();

    /** Libgdx's viewport with its fields set by the layout (never applied: the renderer sets GL state itself). */
    private static final class LayoutViewport extends Viewport {
        LayoutViewport(OrthographicCamera camera) {
            setCamera(camera);
        }

        void set(float worldWidth, float worldHeight, ScreenLayout layout) {
            setWorldSize(worldWidth, worldHeight);
            setScreenBounds(layout.x(), layout.y(), layout.width(), layout.height());
        }
    }

    private final int virtualWidth;
    private final int virtualHeight;
    private final float pixelsPerUnit;
    private final RenderMode mode;
    private final Color background;
    private final Time time;
    private final Batch batch;
    private final RenderTarget target;
    private final OrthographicCamera worldCamera = new OrthographicCamera();
    private final LayoutViewport viewport = new LayoutViewport(worldCamera);
    private final CameraRig rig = new CameraRig();
    private final Matrix4 screenProjection = new Matrix4();
    private List<RenderLayer> layers = List.of(new RenderLayer("world", SortMode.Z));
    private ScreenLayout layout;
    private int screenWidth;
    private int screenHeight;
    private int renderCallsLastFrame;
    private int insetLeft;
    private int insetTop;
    private int insetRight;
    private int insetBottom;

    public GdxRenderer(int virtualWidth, int virtualHeight, float pixelsPerUnit, RenderMode mode, Color background,
            Time time, Batch batch, RenderTarget target) {
        this.virtualWidth = virtualWidth;
        this.virtualHeight = virtualHeight;
        this.pixelsPerUnit = pixelsPerUnit;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.background = new Color(Objects.requireNonNull(background, "background"));
        this.time = Objects.requireNonNull(time, "time");
        this.batch = Objects.requireNonNull(batch, "batch");
        this.target = mode == RenderMode.SMOOTH ? null : Objects.requireNonNull(target, "target");
        resize(virtualWidth, virtualHeight);
    }

    /** The real renderer, or null when there is no OpenGL (headless backends). */
    public static GdxRenderer create(int virtualWidth, int virtualHeight, float pixelsPerUnit, RenderMode mode,
            Color background, Time time) {
        if (Gdx.gl == null) {
            return null;
        }
        GdxRenderer renderer = new GdxRenderer(virtualWidth, virtualHeight, pixelsPerUnit, mode, background, time,
                new SpriteBatch(), mode == RenderMode.SMOOTH ? null : new FrameBufferTarget());
        renderer.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        return renderer;
    }

    /** Recomputes the layout for a window size in back-buffer pixels. */
    public void resize(int width, int height) {
        screenWidth = Math.max(1, width);
        screenHeight = Math.max(1, height);
        layout = ScreenLayout.compute(screenWidth, screenHeight, virtualWidth, virtualHeight, mode);
        if (target != null) {
            target.resize(layout.worldWidth(), layout.worldHeight());
        }
        float worldWidth = layout.worldWidth() / pixelsPerUnit;
        float worldHeight = layout.worldHeight() / pixelsPerUnit;
        rig.viewSize.set(worldWidth, worldHeight);
        viewport.set(worldWidth, worldHeight, layout);
        screenProjection.setToOrtho2D(0, 0, screenWidth, screenHeight);
    }

    /** Applies the camera rig and starts the world pass; the batch is begun when this returns. */
    public void beginFrame() {
        if (batch instanceof SpriteBatch sprites) {
            renderCallsLastFrame = -sprites.totalRenderCalls;   // completed in endFrame
        }
        rig.update(time.delta());
        float x = rig.position.x + rig.shakeOffset().x;
        float y = rig.position.y + rig.shakeOffset().y;
        if (mode != RenderMode.SMOOTH) {
            x = snap(x);
            y = snap(y);
        }
        worldCamera.viewportWidth = layout.worldWidth() / pixelsPerUnit;
        worldCamera.viewportHeight = layout.worldHeight() / pixelsPerUnit;
        worldCamera.zoom = rig.zoom;
        worldCamera.position.set(x, y, 0f);
        worldCamera.update();

        GL20 gl = Gdx.gl;
        if (target != null) {
            target.begin();
            gl.glClearColor(background.r, background.g, background.b, background.a);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        } else {
            gl.glViewport(0, 0, screenWidth, screenHeight);
            gl.glClearColor(0f, 0f, 0f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            gl.glEnable(GL20.GL_SCISSOR_TEST);
            gl.glScissor(layout.x(), layout.y(), layout.width(), layout.height());
            gl.glClearColor(background.r, background.g, background.b, background.a);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            gl.glDisable(GL20.GL_SCISSOR_TEST);
            gl.glViewport(layout.x(), layout.y(), layout.width(), layout.height());
        }
        batch.setColor(Color.WHITE);
        batch.setProjectionMatrix(worldCamera.combined);
        batch.begin();
    }

    /** Ends the world pass and draws the world image to the window. */
    public void endFrame() {
        batch.end();
        GL20 gl = Gdx.gl;
        if (target != null) {
            target.end();
            gl.glViewport(0, 0, screenWidth, screenHeight);
            gl.glClearColor(0f, 0f, 0f, 1f);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setColor(Color.WHITE);
            batch.setProjectionMatrix(screenProjection);
            batch.begin();
            batch.draw(target.region(), layout.x(), layout.y(), layout.width(), layout.height());
            batch.end();
        }
        gl.glViewport(0, 0, screenWidth, screenHeight);
        if (batch instanceof SpriteBatch sprites) {
            renderCallsLastFrame += sprites.totalRenderCalls;
        }
    }

    /** Engine: the safe-area insets in back-buffer pixels (I-225). */
    public void setSafeInsets(int left, int top, int right, int bottom) {
        insetLeft = Math.max(0, left);
        insetTop = Math.max(0, top);
        insetRight = Math.max(0, right);
        insetBottom = Math.max(0, bottom);
    }

    @Override
    public Rectangle safeArea(Rectangle out) {
        return out.set(insetLeft, insetBottom, Math.max(0, screenWidth - insetLeft - insetRight),
                Math.max(0, screenHeight - insetTop - insetBottom));
    }

    /** Draw calls the batch issued in the last frame (0 for batches that do not count them). */
    public int renderCallsLastFrame() {
        return Math.max(0, renderCallsLastFrame);
    }

    /** Window width in back-buffer pixels. */
    public int screenWidth() {
        return screenWidth;
    }

    /** Window height in back-buffer pixels. */
    public int screenHeight() {
        return screenHeight;
    }

    /** Rounds a world coordinate to a whole virtual pixel. */
    public float snap(float worldCoordinate) {
        return Math.round(worldCoordinate * pixelsPerUnit) / pixelsPerUnit;
    }

    public ScreenLayout layout() {
        return layout;
    }

    @Override
    public Batch batch() {
        return batch;
    }

    @Override
    public SpriteMaterials materials() {
        return materials;
    }

    @Override
    public OrthographicCamera worldCamera() {
        return worldCamera;
    }

    @Override
    public Viewport viewport() {
        return viewport;
    }

    @Override
    public CameraRig camera() {
        return rig;
    }

    @Override
    public void setLayers(RenderLayer... newLayers) {
        if (newLayers.length == 0) {
            throw new IllegalArgumentException("At least one render layer is required");
        }
        Set<String> names = new HashSet<>();
        for (RenderLayer layer : newLayers) {
            if (!names.add(layer.name())) {
                throw new IllegalArgumentException("Render layer \"" + layer.name() + "\" is listed twice");
            }
        }
        layers = List.of(newLayers);
    }

    @Override
    public List<RenderLayer> layers() {
        return layers;
    }

    @Override
    public Vector2 screenToWorld(float screenX, float screenY, Vector2 out) {
        float fromBottom = screenHeight - screenY;
        float viewWidth = worldCamera.viewportWidth * worldCamera.zoom;
        float viewHeight = worldCamera.viewportHeight * worldCamera.zoom;
        return out.set(
                worldCamera.position.x + ((screenX - layout.x()) / layout.width() - 0.5f) * viewWidth,
                worldCamera.position.y + ((fromBottom - layout.y()) / layout.height() - 0.5f) * viewHeight);
    }

    @Override
    public int scale() {
        return layout.scale();
    }

    @Override
    public float pixelsPerUnit() {
        return pixelsPerUnit;
    }

    @Override
    public RenderMode mode() {
        return mode;
    }

    @Override
    public void dispose() {
        materials.dispose();
        batch.dispose();
        if (target != null) {
            target.dispose();
        }
    }
}
