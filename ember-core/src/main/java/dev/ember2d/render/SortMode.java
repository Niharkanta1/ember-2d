package dev.ember2d.render;

import dev.ember2d.core.PublicApi;

/** How sprites within one render layer are ordered (F-004). */
@PublicApi
public enum SortMode {
    /** Family order. */
    NONE,
    /** By {@code Sprite.z}, ascending; equal z keeps family order. */
    Z,
    /** By {@code Transform} y, descending: higher y is drawn first (further back). For top-down games. */
    Y_SORT
}
