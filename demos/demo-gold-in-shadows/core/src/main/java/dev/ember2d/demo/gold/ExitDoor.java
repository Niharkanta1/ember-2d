package dev.ember2d.demo.gold;

/** The level exit (F-029): two tiles in a north wall; the key unlocks it, walking through it ends the level. */
public final class ExitDoor {

    /** Needs the level's key. */
    public boolean locked = true;
    /** Runtime: true once opened. */
    public boolean open;
}
