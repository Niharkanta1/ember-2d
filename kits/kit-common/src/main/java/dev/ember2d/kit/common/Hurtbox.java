package dev.ember2d.kit.common;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.PublicApi;

/** Receives damage from {@link Hitbox}es (F-021); the entity also needs {@link Health}. */
@PublicApi
public final class Hurtbox {
    /** Area relative to the entity's position; an empty box uses the entity's {@code Collider} box. */
    public final Rectangle box = new Rectangle();
}
