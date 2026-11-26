package dev.ember2d.demo.platformer;

/** Hurts the player on contact; landing on it from above defeats it (F-015, I-170). */
public final class Enemy {
    /** Seconds the hit clip plays before the enemy is removed. */
    public float defeatSeconds = 0.4f;
    /** Defeated and waiting to be removed (runtime). */
    public boolean defeated;
    /** Seconds since it was defeated (runtime). */
    public float defeatedTime;
}
