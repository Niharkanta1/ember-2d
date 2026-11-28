package dev.ember2d.kit.bullets;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;

/** One kind of bullet (F-024), from a bullet types file; see {@link BulletTypes}. */
@PublicApi
public final class BulletType {
    public final String name;
    /** Region ID for {@code assets.region}. */
    public final String region;
    /** Collision radius in world units. */
    public final float radius;
    public final int damage;
    /** Seconds before it disappears. */
    public final float lifetime;
    /** Dies on solid tiles and walls. */
    public final boolean walls;
    /** Survives hits (each target is hit once). */
    public final boolean pierce;
    /** Can be grazed. */
    public final boolean graze;
    /** Drawn rotated to its velocity. */
    public final boolean rotate;
    /** Drawing scale. */
    public final float scale;
    /** Degrees added to the drawn rotation, for art that does not point right (an upright bullet: -90). */
    public final float rotationOffset;
    /** Colour the region is drawn with (white: as drawn). */
    public final Color tint;
    /** Hidden where the renderer's visibility test says the player cannot see (fog of war, F-030). */
    public final boolean fogged;

    BulletType(String name, String region, float radius, int damage, float lifetime, boolean walls, boolean pierce,
            boolean graze, boolean rotate, float scale, float rotationOffset, Color tint, boolean fogged) {
        this.name = name;
        this.region = region;
        this.radius = radius;
        this.damage = damage;
        this.lifetime = lifetime;
        this.walls = walls;
        this.pierce = pierce;
        this.graze = graze;
        this.rotate = rotate;
        this.scale = scale;
        this.rotationOffset = rotationOffset;
        this.tint = new Color(tint);
        this.fogged = fogged;
    }

    @Override
    public String toString() {
        return "BulletType[" + name + "]";
    }
}
