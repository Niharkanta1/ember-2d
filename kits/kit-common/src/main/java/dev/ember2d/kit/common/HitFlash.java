package dev.ember2d.kit.common;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;

/** Flashes the entity's sprite when it takes damage (F-026): {@code Sprite.flash} is 1 for {@link #seconds}. */
@PublicApi
public final class HitFlash {
    public float seconds = 0.1f;
    public final Color color = new Color(Color.WHITE);
    /** Runtime: seconds of flash left. */
    public float left;
}
