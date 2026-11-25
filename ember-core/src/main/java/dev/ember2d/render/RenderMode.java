package dev.ember2d.render;

import dev.ember2d.core.PublicApi;

/** How the virtual resolution is fitted to the window (F-004). */
@PublicApi
public enum RenderMode {
    /** Largest whole-number scale that fits; letterboxed with black bars; Nearest filtering. */
    PIXEL_PERFECT,
    /** Whole-number scale; the visible world is extended sideways to fill wide screens. */
    PIXEL_PERFECT_FILL,
    /** Fractional scale fitted to the window, linear filtering, no snapping. */
    SMOOTH
}
