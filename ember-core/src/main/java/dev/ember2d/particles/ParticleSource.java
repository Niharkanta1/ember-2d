package dev.ember2d.particles;

import dev.ember2d.core.PublicApi;

/**
 * Attaches a particle effect to an entity (F-014, I-510): {@link ParticleSystem} starts it, keeps it at the entity's
 * position plus the offset, and frees it when the entity goes. Named "source" so it does not clash with libGDX's
 * {@code ParticleEmitter}.
 */
@PublicApi
public final class ParticleSource {
    /** The {@code particle} asset ID. */
    public String effect = "";
    public float offsetX;
    public float offsetY;
    /** Destroy the entity when the effect completes (a one-shot effect placed as an entity). */
    public boolean despawnWhenDone;
    /** Runtime: set when the effect has completed (it is not started again). */
    public boolean finished;
}
