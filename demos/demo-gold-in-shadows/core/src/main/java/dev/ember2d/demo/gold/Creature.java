package dev.ember2d.demo.gold;

/**
 * One of the four creatures (F-029): rat, slug, ghoul or cultist. The prefab holds the values from the original's
 * catalog; {@code CreatureSystem} (I-630) runs its behaviour.
 */
public final class Creature {

    /** rat, slug, ghoul or cultist: the clip in {@code anim.creatures} and the behaviour. */
    public String kind = "rat";
    /** Gold dropped in a pouch on death. */
    public int gold = 5;
    /** A patrol loop in world units, "x,y;x,y;...", from the level; empty uses the creature's idle behaviour. */
    public String patrol = "";
}
