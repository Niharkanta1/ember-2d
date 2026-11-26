package dev.ember2d.ui;

import dev.ember2d.core.PublicApi;

/** Insets from the edges, for safe areas (F-022, I-225): in UI units or, where stated, window pixels. */
@PublicApi
public record Insets(float left, float top, float right, float bottom) {

    public static final Insets NONE = new Insets(0f, 0f, 0f, 0f);

    public Insets {
        left = Math.max(0f, left);
        top = Math.max(0f, top);
        right = Math.max(0f, right);
        bottom = Math.max(0f, bottom);
    }
}
