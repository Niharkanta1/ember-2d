package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A parsed, validated animation state machine definition (F-007, I-135); immutable. Built by {@link AsmLibrary}.
 */
@PublicApi
public final class AsmDefinition {

    /** Parameter types. */
    public enum ParamType { BOOL, FLOAT, INT, TRIGGER }

    /** One state: the clip it plays and how. {@code clipDuration} is the clip's length, for {@code exitTime}. */
    public record State(String name, String clip, float speed, String speedParam, boolean exitOnFinish,
            float clipDuration) {
    }

    /** One condition term: {@code name}, {@code !name} or {@code name OP value}. */
    public record Term(String param, String op, float value) {

        /** True for a bare bool or trigger name. */
        public boolean bare() {
            return op == null;
        }

        /** True for {@code !name}. */
        public boolean negated() {
            return "!".equals(op);
        }
    }

    /**
     * One transition, in file order ({@code index}); {@code from} is a state name or {@code *}. {@code exitTime} is
     * NaN when not set.
     */
    public record Transition(int index, String from, String to, List<Term> when, int priority, float minTime,
            float exitTime) {
    }

    private final String id;
    private final String clipSet;
    private final Map<String, ParamType> parameters;
    private final String initial;
    private final Map<String, State> states;
    private final List<Transition> transitions;

    AsmDefinition(String id, String clipSet, Map<String, ParamType> parameters, String initial,
            Map<String, State> states, List<Transition> transitions) {
        this.id = id;
        this.clipSet = clipSet;
        this.parameters = Collections.unmodifiableMap(parameters);
        this.initial = initial;
        this.states = Collections.unmodifiableMap(states);
        this.transitions = List.copyOf(transitions);
    }

    public String id() {
        return id;
    }

    /** Asset ID of the clip set the states play. */
    public String clipSet() {
        return clipSet;
    }

    public Map<String, ParamType> parameters() {
        return parameters;
    }

    public String initial() {
        return initial;
    }

    public State state(String name) {
        return states.get(name);
    }

    public Set<String> stateNames() {
        return states.keySet();
    }

    /** All transitions in file order. */
    public List<Transition> transitions() {
        return transitions;
    }
}
