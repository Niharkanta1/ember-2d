package dev.ember2d.camera;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.PublicApi;

/**
 * The game camera's position, zoom and shake (F-004), available as {@code context.renderer().camera()}. Move it
 * directly or with {@link CameraFollowSystem}; the renderer applies it to its OpenGL camera every frame, adding
 * the shake offset and snapping to whole virtual pixels in the pixel-perfect modes.
 */
@PublicApi
public final class CameraRig implements CameraShake {

    /** Centre of the view, in world units. */
    public final Vector2 position = new Vector2();
    /** 1 is normal; 2 shows twice as much of the world. */
    public float zoom = 1f;
    /** Largest shake offset at full trauma, in world units. */
    public float maxShakeOffset = 0.5f;
    /** Trauma lost per second of game time. */
    public float traumaDecay = 1.5f;
    /** Visible world size at zoom 1, in world units; set by the renderer when the window changes. */
    public final Vector2 viewSize = new Vector2();

    private final Vector2 shakeOffset = new Vector2();
    private float trauma;

    @Override
    public void addTrauma(float amount) {
        trauma = MathUtils.clamp(trauma + amount, 0f, 1f);
    }

    public float trauma() {
        return trauma;
    }

    /** The current shake offset (set by {@link #update(float)}); read-only. */
    public Vector2 shakeOffset() {
        return shakeOffset;
    }

    /** Visible world width at the current zoom, in world units. */
    public float viewWidth() {
        return viewSize.x * zoom;
    }

    /** Visible world height at the current zoom, in world units. */
    public float viewHeight() {
        return viewSize.y * zoom;
    }

    /** Decays trauma and picks this frame's shake offset; the renderer calls it once per frame with scaled delta. */
    public void update(float delta) {
        trauma = Math.max(0f, trauma - traumaDecay * delta);
        float strength = maxShakeOffset * trauma * trauma;
        if (strength > 0f) {
            shakeOffset.set(MathUtils.random(-1f, 1f) * strength, MathUtils.random(-1f, 1f) * strength);
        } else {
            shakeOffset.setZero();
        }
    }
}
