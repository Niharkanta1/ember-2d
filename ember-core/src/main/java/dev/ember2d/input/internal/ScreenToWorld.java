package dev.ember2d.input.internal;

import com.badlogic.gdx.math.Vector2;

/** Converts window pixels to world units; supplied by the composition root from the renderer (F-005). */
@FunctionalInterface
public interface ScreenToWorld {

    /** Writes the world position of a window pixel (y down) into {@code out} and returns it. */
    Vector2 toWorld(float screenX, float screenY, Vector2 out);
}
