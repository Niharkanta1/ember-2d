package dev.ember2d.demo.gold;

/** A spike trap (F-029): rises and falls in a cycle; hurts while up. The level sets the phase so traps move in waves. */
public final class SpikeTrap {

    /** Seconds into the cycle at the start (waves: neighbours are a little apart). */
    public float phase;
    /** Runtime: whether the spikes are up. */
    public boolean up;
}
