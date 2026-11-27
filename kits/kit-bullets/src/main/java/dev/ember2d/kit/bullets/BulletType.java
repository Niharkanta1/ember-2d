package dev.ember2d.kit.bullets;

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

    BulletType(String name, String region, float radius, int damage, float lifetime, boolean walls, boolean pierce,
            boolean graze, boolean rotate, float scale) {
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
    }

    @Override
    public String toString() {
        return "BulletType[" + name + "]";
    }
}
