package dev.ember2d.ecs;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import java.util.Objects;

/**
 * Logic that runs over the entities of one {@link Family} in one {@link Phase} (F-003). Systems run by phase,
 * then in registration order. Structural changes made while a system runs (create, destroy, add, remove) are
 * applied after the system finishes, so no system sees a half-changed entity.
 *
 * <p>Delta per phase: {@code FIXED} gets the fixed step; {@code INPUT}, {@code UPDATE} and {@code LATE} get the
 * scaled frame delta; {@code RENDER} gets the unscaled delta. While the game is paused, {@code UPDATE} and
 * {@code LATE} systems are skipped unless {@link #runsWhenPaused()} is true; such systems always get the unscaled
 * delta in those phases.
 */
@PublicApi
public abstract class EntitySystem {

    private final Phase phase;
    private final Family family;
    private World world;

    /** Set to false to skip the system without removing it. */
    public boolean enabled = true;

    protected EntitySystem(Phase phase, Family family) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.family = Objects.requireNonNull(family, "family");
    }

    /** Called once per entity in the family, in family order. */
    protected abstract void process(int entity, float delta);

    /** Called before the first entity of a run. */
    protected void begin() {
    }

    /** Called after the last entity of a run. */
    protected void end() {
    }

    /** Whether the system keeps running in {@code UPDATE}/{@code LATE} while the game is paused (pause menus). */
    public boolean runsWhenPaused() {
        return false;
    }

    public final Phase phase() {
        return phase;
    }

    public final Family family() {
        return family;
    }

    /** The world this system was added to. */
    protected final World world() {
        if (world == null) {
            throw new IllegalStateException(getClass().getName() + " has not been added to a World");
        }
        return world;
    }

    void attach(World world) {
        this.world = world;
    }
}
