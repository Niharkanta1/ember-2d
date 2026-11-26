package dev.ember2d.animation;

import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectMap;
import dev.ember2d.animation.AsmDefinition.ParamType;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs every {@link AnimationStateMachine} in {@code UPDATE} (F-007, I-135). Register it after the game systems that
 * set parameters and before the {@link AnimationSystem}. Per entity and update: a finished {@code exitOnFinish} state
 * returns to {@code initial}; then the valid transition with the highest priority wins (ties: file order; a {@code *}
 * transition never re-enters the current state); triggers are cleared; the state's clip plays through the
 * {@link Animator}, with {@code speedParam} scaling its speed. State changes queue {@link AnimationStateExited} and
 * {@link AnimationStateEntered}. A reloaded definition is picked up on the next update.
 */
@PublicApi
public final class AsmSystem extends EntitySystem {

    private final AsmLibrary library;
    private final EventBus events;

    public AsmSystem(World world, AsmLibrary library, EventBus events) {
        super(Phase.UPDATE, world.family(AnimationStateMachine.class, Animator.class));
        this.library = Objects.requireNonNull(library, "library");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    protected void process(int entity, float delta) {
        AnimationStateMachine m = world().get(entity, AnimationStateMachine.class);
        if (m.definition == null) {
            return;
        }
        Animator a = world().get(entity, Animator.class);
        AsmDefinition def = library.definition(m.definition);
        if (def != m.bound) {
            bind(m, a, def);
        }
        if (m.state == null) {
            change(entity, m, a, def, def.initial(), "initial");
        } else {
            m.timeInState += delta;
        }
        AsmDefinition.State current = def.state(m.state);
        if (current.exitOnFinish() && a.isFinished() && !m.state.equals(def.initial())) {
            change(entity, m, a, def, def.initial(), "finished");
        }
        AsmDefinition.Transition best = null;
        for (AsmDefinition.Transition t : def.transitions()) {
            if (valid(t, m, a, def) && (best == null || t.priority() > best.priority())) {
                best = t;
            }
        }
        if (best != null) {
            change(entity, m, a, def, best.to(), "transitions[" + best.index() + "]");
        }
        for (Map.Entry<String, ParamType> p : def.parameters().entrySet()) {
            if (p.getValue() == ParamType.TRIGGER) {
                m.values.put(p.getKey(), 0f);
            }
        }
        AsmDefinition.State state = def.state(m.state);
        float scale = state.speedParam() == null ? 1f : Math.abs(m.values.get(state.speedParam(), 0f));
        a.speed = state.speed() * scale;
    }

    private static boolean valid(AsmDefinition.Transition t, AnimationStateMachine m, Animator a, AsmDefinition def) {
        boolean any = t.from().equals("*");
        if (any ? t.to().equals(m.state) : !t.from().equals(m.state)) {
            return false;
        }
        if (m.timeInState < t.minTime()) {
            return false;
        }
        if (!Float.isNaN(t.exitTime())) {
            float length = def.state(m.state).clipDuration();
            float normalised = length > 0f ? a.time() / length : 1f;
            if (!(a.isFinished() || normalised >= t.exitTime())) {
                return false;
            }
        }
        List<AsmDefinition.Term> when = t.when();
        for (int i = 0; i < when.size(); i++) {
            if (!holds(when.get(i), m.values)) {
                return false;
            }
        }
        return true;
    }

    private static boolean holds(AsmDefinition.Term term, ObjectFloatMap<String> values) {
        float v = values.get(term.param(), 0f);
        if (term.bare()) {
            return v != 0f;
        }
        return switch (term.op()) {
            case "!" -> v == 0f;
            case ">" -> v > term.value();
            case ">=" -> v >= term.value();
            case "<" -> v < term.value();
            case "<=" -> v <= term.value();
            case "==" -> v == term.value();
            default -> v != term.value();   // "!="
        };
    }

    private void change(int entity, AnimationStateMachine m, Animator a, AsmDefinition def, String to,
            String reason) {
        String from = m.state;
        if (from != null) {
            events.queue(new AnimationStateExited(entity, from));
        }
        m.state = to;
        m.timeInState = 0f;
        m.lastTransition = (from == null ? "(start)" : from) + " -> " + to + " (" + reason + ")";
        a.play(def.state(to).clip());
        events.queue(new AnimationStateEntered(entity, to));
    }

    /** First use or a reloaded definition: check early parameters, keep what still fits, point the animator. */
    private static void bind(AnimationStateMachine m, Animator a, AsmDefinition def) {
        for (ObjectMap.Entry<String, ParamType> p : m.pending) {
            AnimationStateMachine.check(p.key, p.value, def);
        }
        m.pending.clear();
        com.badlogic.gdx.utils.Array<String> stale = new com.badlogic.gdx.utils.Array<>();
        for (ObjectFloatMap.Entry<String> v : m.values) {
            if (!def.parameters().containsKey(v.key)
                    || (m.bound != null && m.bound.parameters().get(v.key) != def.parameters().get(v.key))) {
                stale.add(v.key);
            }
        }
        for (String key : stale) {
            m.values.remove(key, 0f);
        }
        if (m.state != null && def.state(m.state) == null) {
            m.state = null;   // the state is gone: start again from initial
        } else if (m.state != null) {
            a.playIfNotPlaying(def.state(m.state).clip());
        }
        m.bound = def;
        a.clipSet = def.clipSet();
    }
}
