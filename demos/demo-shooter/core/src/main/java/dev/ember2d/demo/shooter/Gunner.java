package dev.ember2d.demo.shooter;

/** An enemy that fires a bullet pattern at the player every few seconds (GunnerSystem). */
public final class Gunner {
    public String pattern = "";
    public float interval = 3f;
    /** Runtime: seconds to the next volley; -1 starts with a random wait. */
    public float wait = -1f;
}
