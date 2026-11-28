package dev.ember2d.demo.gold;

/**
 * One of the four creatures (F-029): rat, slug, ghoul or cultist. Its values come from {@code creatures.json} by
 * {@link #kind}; {@link CreatureSystem} runs the original's state machine on the runtime fields below.
 */
public final class Creature {

    /** rat, slug, ghoul or cultist: the entry in {@code creatures.json} and the clip in {@code anim.creatures}. */
    public String kind = "rat";
    /** Gold dropped in a pouch on death. */
    public int gold = 5;
    /** A patrol loop in world units, "x,y;x,y;...", from the level; empty uses the creature's idle behaviour. */
    public String patrol = "";

    /** The original's states. */
    enum State { IDLE, ACTIVE, ATTACKING, STUNNED, DEAD }

    // Runtime (not data).
    State state = State.IDLE;
    boolean hasTarget;
    boolean winding;          // in an attack: true while winding up, false while recovering
    boolean attackHit;        // the dash has already hit
    float attackLeft;
    float cooldown;
    float stunLeft;
    float alertLeft;          // "!" shown
    boolean investigating;    // "?" shown
    float investigateX;
    float investigateY;
    float investigateLeft;
    float healthBarLeft;
    float deadFor = -1f;
    float homeX = Float.NaN;
    float homeY;
    float pointX;
    float pointY;
    float wait;
    int patrolIndex;
    float patrolPause;
    float[] route;
    float strafeLeft;
    int strafeDirection = 1;
    float wobble;
}
