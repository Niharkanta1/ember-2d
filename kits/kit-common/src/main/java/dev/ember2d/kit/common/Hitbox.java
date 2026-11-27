package dev.ember2d.kit.common;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.PublicApi;

/**
 * Deals damage to {@link Hurtbox}es it overlaps (F-021), once per target per activation. An activation starts when
 * {@link #active} turns true or {@link #restart()} is called; {@link HitSystem} does the checks.
 */
@PublicApi
public final class Hitbox {
    /** Area relative to the entity's position, in world units. */
    public final Rectangle box = new Rectangle(-0.5f, 0f, 1f, 1f);
    public int damage = 1;
    /** Length of the knockback vector reported with the damage (genre kits apply it). */
    public float knockback;
    public boolean active = true;
    /** With a value above 0, the hitbox turns itself off this many seconds after an activation starts. */
    public float activeSeconds;
    /** Despawn the hitbox entity after its first hit that dealt damage (projectiles). */
    public boolean destroyOnHit;
    /** Runtime: increases with every activation. */
    public int activation;

    /** Starts a new activation: targets hit before can be hit again. */
    public void restart() {
        active = true;
        activation++;
    }
}
