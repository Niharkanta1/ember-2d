package dev.ember2d.demo.gold;

/**
 * The original game's camera (F-029): follows the player with exponential smoothing and a little look-ahead in the
 * direction of movement, and shakes on hits. {@link GoldCameraSystem} drives the scene's camera rig from it.
 */
public final class GoldCamera {

    /** How quickly the camera catches up (the original's 8 per second). */
    public float smoothing = 8f;
    /** Seconds of the player's velocity to look ahead (the original's 0.15). */
    public float lookAhead = 0.15f;
    /** Runtime: the shake's strength (world units), length and time left. */
    public float shakeStrength;
    public float shakeSeconds;
    public float shakeLeft;
}
