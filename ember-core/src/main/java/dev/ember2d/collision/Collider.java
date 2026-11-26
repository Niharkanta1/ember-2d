package dev.ember2d.collision;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.PublicApi;

/**
 * A collision box (F-006): {@code box.x/y} is the offset from the entity's {@code Transform.position}, and
 * {@code box.width/height} the size, in world units. {@code layer} is a bit index (0-31); {@code mask} the layers this
 * collider collides with or detects. A trigger never blocks; it reports actors entering and leaving.
 */
@PublicApi
public final class Collider {
    public final Rectangle box = new Rectangle();
    public int layer;
    public int mask = ~0;
    public boolean trigger;
}
