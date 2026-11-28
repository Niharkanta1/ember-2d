package dev.ember2d.demo.gold;

/** Something the player collects by touching it (F-029): coin, pile, pouch, medkit or key. Collected once. */
public final class Pickup {

    /** coin, pile, pouch, medkit or key. */
    public String kind = "coin";
    /** Gold for coin, pile and pouch; quarter hearts healed for a medkit. */
    public int amount = 10;
}
