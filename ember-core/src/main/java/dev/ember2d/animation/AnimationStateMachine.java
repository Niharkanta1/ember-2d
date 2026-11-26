package dev.ember2d.animation;

import com.badlogic.gdx.utils.ObjectFloatMap;
import com.badlogic.gdx.utils.ObjectMap;
import dev.ember2d.animation.AsmDefinition.ParamType;
import dev.ember2d.core.PublicApi;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Picks the entity's animation from parameters (F-007, I-135): game code sets parameters, the {@link AsmSystem}
 * evaluates the definition's transitions and plays the current state's clip through the entity's {@link Animator}.
 * {@code definition} is data (prefabs, Tiled); everything else is runtime state. Parameters set before the definition
 * is loaded are kept and checked when it is.
 */
@PublicApi
public final class AnimationStateMachine {

    /** Asset ID of the definition (manifest type {@code asm}). */
    public String definition;

    AsmDefinition bound;
    String state;
    float timeInState;
    String lastTransition;
    final ObjectFloatMap<String> values = new ObjectFloatMap<>();
    final ObjectMap<String, ParamType> pending = new ObjectMap<>();

    public void setBool(String param, boolean value) {
        set(param, ParamType.BOOL, value ? 1f : 0f);
    }

    public void setFloat(String param, float value) {
        set(param, ParamType.FLOAT, value);
    }

    public void setInt(String param, int value) {
        set(param, ParamType.INT, value);
    }

    /** Sets a trigger for the next evaluation; it is cleared after that evaluation, used or not. */
    public void trigger(String param) {
        set(param, ParamType.TRIGGER, 1f);
    }

    public boolean getBool(String param) {
        return get(param, ParamType.BOOL) != 0f;
    }

    public float getFloat(String param) {
        return get(param, ParamType.FLOAT);
    }

    public int getInt(String param) {
        return (int) get(param, ParamType.INT);
    }

    /** The current state, or null before the first update. */
    public String currentState() {
        return state;
    }

    /** Game seconds in the current state. */
    public float timeInState() {
        return timeInState;
    }

    /** The last change, as "from -> to (reason)", or null (for dev tools). */
    public String lastTransition() {
        return lastTransition;
    }

    /**
     * Parameter names and types ({@code "bool"}, {@code "float"}, {@code "int"}, {@code "trigger"}) of the bound
     * definition, in definition order; empty before the first update (dev tools, F-018).
     */
    public Map<String, String> parameterTypes() {
        if (bound == null) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, ParamType> e : bound.parameters().entrySet()) {
            out.put(e.getKey(), e.getValue().name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private void set(String param, ParamType type, float value) {
        if (bound != null) {
            check(param, type, bound);
        } else {
            pending.put(param, type);
        }
        values.put(param, value);
    }

    private float get(String param, ParamType type) {
        if (bound != null) {
            check(param, type, bound);
        }
        return values.get(param, 0f);
    }

    static void check(String param, ParamType type, AsmDefinition def) {
        ParamType actual = def.parameters().get(param);
        if (actual == null) {
            throw new IllegalArgumentException("State machine \"" + def.id() + "\" has no parameter \"" + param
                    + "\"; parameters: " + def.parameters().keySet());
        }
        if (actual != type) {
            throw new IllegalArgumentException("Parameter \"" + param + "\" of state machine \"" + def.id() + "\" is a "
                    + actual.name().toLowerCase(Locale.ROOT) + ", not a "
                    + type.name().toLowerCase(Locale.ROOT));
        }
    }
}
