package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Hit points (F-021). Changed through {@link HealthSystem}, which also counts down the invulnerability window. */
@PublicApi
public final class Health {
    /** Full health. */
    public int max = 3;
    /** Current health; -1 starts full. */
    public int current = -1;
    /** After a hit, further damage is ignored for this long (seconds). */
    public float invulnerableSeconds = 0.5f;
    /** Despawn the entity when it dies (see {@link Despawn}). */
    public boolean destroyOnDeath;
    /** Runtime: seconds of invulnerability left. */
    public float invulnerableLeft;
    /** Runtime: set once health reaches 0. */
    public boolean dead;
}
