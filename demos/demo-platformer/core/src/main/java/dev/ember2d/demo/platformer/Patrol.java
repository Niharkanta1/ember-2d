package dev.ember2d.demo.platformer;

/** Walks back and forth between two limits under gravity, turning at walls (Phase 1 motion check, Phase 2 actor). */
public final class Patrol {
    /** World units per second. */
    public float speed = 6f;
    public float minX = -12f;
    public float maxX = 12f;
    /**
     * Walks this far to each side of where it was placed (I-170), so it can be moved in Tiled without new limits;
     * 0 uses {@code minX} and {@code maxX}.
     */
    public float range;
    /** The sprite art faces left (flip it to walk right). */
    public boolean artFacesLeft;
    /** +1 right, -1 left. */
    public float direction = 1f;
    public float gravity = 40f;
    /** Current vertical speed (runtime). */
    public float vy;
    /** {@code range} was turned into limits (runtime). */
    boolean limitsSet;
}
