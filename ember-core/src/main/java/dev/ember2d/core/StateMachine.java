package dev.ember2d.core;

import com.badlogic.gdx.utils.Array;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A small finite state machine over an enum (F-002), owned by game code: enemy behaviour, menu flow, boss
 * phases. Register enter/exit actions, call {@link #update(float)} every frame and {@link #change(Enum)} to
 * switch state.
 *
 * <ul>
 *   <li>{@code change(next)} runs the exit actions of the current state, then the enter actions of
 *       {@code next}, and resets {@link #timeInState()}.</li>
 *   <li>Changing to the current state does nothing.</li>
 *   <li>A {@code change} called from inside an enter or exit action is applied after the running change
 *       completes.</li>
 *   <li>The initial state's enter actions are not run (they are registered after construction); do the
 *       initial state's setup directly.</li>
 * </ul>
 *
 * @param <S> the enum of states
 */
@PublicApi
public final class StateMachine<S extends Enum<S>> {

    private final Map<S, Array<Runnable>> enterActions;
    private final Map<S, Array<Runnable>> exitActions;
    private S current;
    private float timeInState;
    private boolean changing;
    private S pending;

    public StateMachine(S initial) {
        Objects.requireNonNull(initial, "initial");
        Class<S> type = initial.getDeclaringClass();
        this.enterActions = new EnumMap<>(type);
        this.exitActions = new EnumMap<>(type);
        this.current = initial;
    }

    /** Adds an action run when {@code state} is entered. */
    public void onEnter(S state, Runnable action) {
        add(enterActions, state, action);
    }

    /** Adds an action run when {@code state} is left. */
    public void onExit(S state, Runnable action) {
        add(exitActions, state, action);
    }

    /** Switches to {@code next}: exit actions of the current state, then enter actions of {@code next}. */
    public void change(S next) {
        Objects.requireNonNull(next, "next");
        if (changing) {
            pending = next;
            return;
        }
        S target = next;
        while (target != null) {
            if (target != current) {
                changing = true;
                try {
                    run(exitActions, current);
                    current = target;
                    timeInState = 0f;
                    run(enterActions, target);
                } finally {
                    changing = false;
                }
            }
            target = pending;
            pending = null;
        }
    }

    public S current() {
        return current;
    }

    /** Seconds spent in the current state, as accumulated by {@link #update(float)}. */
    public float timeInState() {
        return timeInState;
    }

    /** Advances the time in the current state; call once per frame with the frame's delta. */
    public void update(float delta) {
        timeInState += delta;
    }

    private void add(Map<S, Array<Runnable>> actions, S state, Runnable action) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(action, "action");
        actions.computeIfAbsent(state, s -> new Array<>(2)).add(action);
    }

    private void run(Map<S, Array<Runnable>> actions, S state) {
        Array<Runnable> list = actions.get(state);
        if (list != null) {
            for (int i = 0; i < list.size; i++) {
                list.get(i).run();
            }
        }
    }
}
