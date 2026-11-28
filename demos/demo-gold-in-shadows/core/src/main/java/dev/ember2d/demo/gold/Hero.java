package dev.ember2d.demo.gold;

/**
 * The player (F-029): the rest is kit components (movement, aim, rifle, health). {@link HeroSystem} turns their state
 * into the original game's look: aim-facing, animations, blinking while invulnerable, the gun, the hands, the muzzle
 * flash, the reload icon and the death sequence.
 */
public final class Hero {

    /** Keys carried (each level has one, for its exit door). */
    public int keys;
    /** Runtime: seconds left of the hit animation. */
    public float hitLeft;
    /** Runtime: seconds since death, or -1 while alive. */
    public float deadFor = -1f;
    /** Runtime: the gun, hand and reload-icon entities. */
    public int gun = -1;
    public int leftHand = -1;
    public int rightHand = -1;
    public int reloadIcon = -1;
}
