package dev.ember2d.demo.gold;

/** Something the player collects by touching it (F-029): coin, pile, pouch, medkit or key. Collected once. */
public final class Pickup {

    /** coin, pile, pouch, medkit or key. */
    public String kind = "coin";
    /** Gold for coin, pile and pouch; quarter hearts healed for a medkit. */
    public int amount = 10;

    /** Seconds before it can be taken (dropped items: the original's 0.45 s). */
    public float delay;
    /** Launch velocity in world units per second (dropped items fly out and slow down). */
    public float vx;
    public float vy;

    // Runtime: the bob's phase.
    float bob = Float.NaN;
}
