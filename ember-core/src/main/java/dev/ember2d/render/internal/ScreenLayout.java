package dev.ember2d.render.internal;

import dev.ember2d.render.RenderMode;

/**
 * Where and how big the world image is drawn in the window (F-004). A pure function of window size, virtual size
 * and mode.
 *
 * @param scale whole-number scale in the pixel-perfect modes (1 when scaling down); 1 in {@code SMOOTH}
 * @param worldWidth visible world width in virtual pixels (wider than the virtual width in fill mode)
 * @param worldHeight visible world height in virtual pixels
 * @param x left edge of the world image in the window, in window pixels
 * @param y bottom edge of the world image in the window, in window pixels
 * @param width width of the world image in the window, in window pixels
 * @param height height of the world image in the window, in window pixels
 */
public record ScreenLayout(int scale, int worldWidth, int worldHeight, int x, int y, int width, int height) {

    public static ScreenLayout compute(int screenWidth, int screenHeight, int virtualWidth, int virtualHeight,
            RenderMode mode) {
        int sw = Math.max(1, screenWidth);
        int sh = Math.max(1, screenHeight);
        int whole = Math.min(sw / virtualWidth, sh / virtualHeight);
        if (mode == RenderMode.SMOOTH || whole < 1) {
            float fit = Math.min((float) sw / virtualWidth, (float) sh / virtualHeight);
            int width = Math.round(virtualWidth * fit);
            int height = Math.round(virtualHeight * fit);
            return new ScreenLayout(1, virtualWidth, virtualHeight, (sw - width) / 2, (sh - height) / 2, width, height);
        }
        int worldWidth = mode == RenderMode.PIXEL_PERFECT_FILL ? sw / whole : virtualWidth;
        int width = worldWidth * whole;
        int height = virtualHeight * whole;
        return new ScreenLayout(whole, worldWidth, virtualHeight, (sw - width) / 2, (sh - height) / 2, width, height);
    }
}
