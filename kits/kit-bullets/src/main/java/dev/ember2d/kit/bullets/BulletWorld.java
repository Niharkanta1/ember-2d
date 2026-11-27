package dev.ember2d.kit.bullets;

import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.World;

/**
 * The bullets of a scene (F-024, ADR-020): parallel arrays, no object per bullet. Slots run from 0 to
 * {@link #size()} - 1; removal swaps the last bullet into the freed slot, so slot numbers are not stable across steps.
 * When full, a new bullet replaces the oldest one (logged once). Usually driven by {@link BulletSystem}.
 */
@PublicApi
public final class BulletWorld {

    /** How many targets a piercing bullet remembers (so it hits each once). */
    static final int MAX_PIERCED = 4;
    private static final Log LOG = Log.get("bullets");

    private final BulletTypes types;
    private final int capacity;
    private final float[] x;
    private final float[] y;
    private final float[] vx;
    private final float[] vy;
    private final float[] accel;
    private final float[] turn;
    private final float[] age;
    private final float[] life;
    private final float[] radius;
    private final int[] type;
    private final int[] team;
    private final int[] damage;
    private final int[] target;
    private final boolean[] grazed;
    private final Emitter[] child;
    private final float[] childAfter;
    private final int[] pierced;
    private final int[] piercedCount;
    private final float[] lastX;
    private final float[] lastY;
    private int size;
    private Rectangle cull;
    private boolean warnedFull;

    public BulletWorld(int capacity, BulletTypes types) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 (was " + capacity + ")");
        }
        this.types = types;
        this.capacity = capacity;
        x = new float[capacity];
        y = new float[capacity];
        vx = new float[capacity];
        vy = new float[capacity];
        accel = new float[capacity];
        turn = new float[capacity];
        age = new float[capacity];
        life = new float[capacity];
        radius = new float[capacity];
        type = new int[capacity];
        team = new int[capacity];
        damage = new int[capacity];
        target = new int[capacity];
        grazed = new boolean[capacity];
        child = new Emitter[capacity];
        childAfter = new float[capacity];
        pierced = new int[capacity * MAX_PIERCED];
        piercedCount = new int[capacity];
        lastX = new float[capacity];
        lastY = new float[capacity];
    }

    /** A world with the default capacity of 4,096 bullets. */
    public BulletWorld(BulletTypes types) {
        this(4096, types);
    }

    public BulletTypes types() {
        return types;
    }

    /**
     * Adds a bullet of a type (see {@link BulletTypes#index}) moving at {@code speed} towards {@code angleDeg}
     * (0 right, 90 up). Returns its slot, valid until the next removal.
     */
    public int spawn(int bulletType, float px, float py, float angleDeg, float speed, int ownerTeam) {
        BulletType t = types.get(bulletType);
        int i;
        if (size < capacity) {
            i = size++;
        } else {
            i = oldest();
            if (!warnedFull) {
                warnedFull = true;
                LOG.warn("Bullet capacity " + capacity + " reached: new bullets replace the oldest (logged once)");
            }
        }
        x[i] = px;
        y[i] = py;
        lastX[i] = px;
        lastY[i] = py;
        double radians = Math.toRadians(angleDeg);   // exact: lookup-table trig is off by about 0.01 degrees
        vx[i] = (float) Math.cos(radians) * speed;
        vy[i] = (float) Math.sin(radians) * speed;
        accel[i] = 0f;
        turn[i] = 0f;
        age[i] = 0f;
        life[i] = t.lifetime;
        radius[i] = t.radius;
        type[i] = bulletType;
        team[i] = ownerTeam;
        damage[i] = t.damage;
        target[i] = World.NONE;
        grazed[i] = false;
        child[i] = null;
        childAfter[i] = 0f;
        piercedCount[i] = 0;
        return i;
    }

    /** Acceleration along the velocity, units per second squared (negative slows down, never below 0 speed). */
    public void setAccel(int slot, float unitsPerSecondSquared) {
        accel[slot] = unitsPerSecondSquared;
    }

    /** Turning, degrees per second (positive is counter-clockwise). */
    public void setTurn(int slot, float degreesPerSecond) {
        turn[slot] = degreesPerSecond;
    }

    /** An emitter the bullet runs at its position after {@code after} seconds; the bullet is then removed. */
    public void setChild(int slot, Emitter emitter, float after, int aimTarget) {
        child[slot] = emitter;
        childAfter[slot] = after;
        target[slot] = aimTarget;
    }

    /** Bullets outside this rectangle are removed at the next update; null to keep them (default). */
    public void setCullArea(Rectangle area) {
        this.cull = area;
    }

    /** Moves, turns and ages every bullet; removes expired and culled ones. */
    public void update(float dt) {
        for (int i = size - 1; i >= 0; i--) {
            lastX[i] = x[i];
            lastY[i] = y[i];
            if (turn[i] != 0f) {
                double radians = Math.toRadians(turn[i] * dt);
                float c = (float) Math.cos(radians);
                float s = (float) Math.sin(radians);
                float nx = vx[i] * c - vy[i] * s;
                vy[i] = vx[i] * s + vy[i] * c;
                vx[i] = nx;
            }
            if (accel[i] != 0f) {
                float speed = (float) Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i]);
                if (speed > 0f) {
                    float next = Math.max(0f, speed + accel[i] * dt);
                    vx[i] *= next / speed;
                    vy[i] *= next / speed;
                }
            }
            x[i] += vx[i] * dt;
            y[i] += vy[i] * dt;
            age[i] += dt;
            boolean expired = age[i] >= life[i] && child[i] == null;
            boolean culled = cull != null && !cull.contains(x[i], y[i]);
            if (expired || culled) {
                remove(i);
            }
        }
    }

    /** Removes the bullet in a slot (the last bullet moves into it). */
    public void remove(int slot) {
        int last = --size;
        if (slot != last) {
            x[slot] = x[last];
            y[slot] = y[last];
            vx[slot] = vx[last];
            vy[slot] = vy[last];
            accel[slot] = accel[last];
            turn[slot] = turn[last];
            age[slot] = age[last];
            life[slot] = life[last];
            radius[slot] = radius[last];
            type[slot] = type[last];
            team[slot] = team[last];
            damage[slot] = damage[last];
            target[slot] = target[last];
            grazed[slot] = grazed[last];
            child[slot] = child[last];
            childAfter[slot] = childAfter[last];
            piercedCount[slot] = piercedCount[last];
            System.arraycopy(pierced, last * MAX_PIERCED, pierced, slot * MAX_PIERCED, MAX_PIERCED);
            lastX[slot] = lastX[last];
            lastY[slot] = lastY[last];
        }
        child[last] = null;
    }

    /**
     * Removes every bullet of a team (-1 for all) and reports each position to the listener (may be null).
     * Returns how many were removed.
     */
    public int clear(int ownerTeam, BulletClearListener listener) {
        int removed = 0;
        for (int i = size - 1; i >= 0; i--) {
            if (ownerTeam < 0 || team[i] == ownerTeam) {
                if (listener != null) {
                    listener.cleared(x[i], y[i]);
                }
                remove(i);
                removed++;
            }
        }
        return removed;
    }

    private int oldest() {
        int best = 0;
        for (int i = 1; i < size; i++) {
            if (age[i] > age[best]) {
                best = i;
            }
        }
        return best;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    public float x(int i) {
        return x[i];
    }

    public float y(int i) {
        return y[i];
    }

    public float vx(int i) {
        return vx[i];
    }

    public float vy(int i) {
        return vy[i];
    }

    public float age(int i) {
        return age[i];
    }

    public float radius(int i) {
        return radius[i];
    }

    public int type(int i) {
        return type[i];
    }

    public int team(int i) {
        return team[i];
    }

    public int damage(int i) {
        return damage[i];
    }

    // ---- used by BulletSystem ----

    float lastX(int i) {
        return lastX[i];
    }

    float lastY(int i) {
        return lastY[i];
    }

    boolean grazed(int i) {
        return grazed[i];
    }

    void setGrazed(int i) {
        grazed[i] = true;
    }

    Emitter child(int i) {
        return child[i];
    }

    float childAfter(int i) {
        return childAfter[i];
    }

    int target(int i) {
        return target[i];
    }

    /** Whether a piercing bullet already hit the target; remembers it if not (up to {@link #MAX_PIERCED}). */
    boolean pierce(int i, int targetEntity) {
        int base = i * MAX_PIERCED;
        for (int k = 0; k < piercedCount[i]; k++) {
            if (pierced[base + k] == targetEntity) {
                return true;
            }
        }
        if (piercedCount[i] < MAX_PIERCED) {
            pierced[base + piercedCount[i]++] = targetEntity;
        } else {
            System.arraycopy(pierced, base + 1, pierced, base, MAX_PIERCED - 1);
            pierced[base + MAX_PIERCED - 1] = targetEntity;
        }
        return false;
    }
}
