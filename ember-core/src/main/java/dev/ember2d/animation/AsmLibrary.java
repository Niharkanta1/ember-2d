package dev.ember2d.animation;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.animation.AsmDefinition.ParamType;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Animation state machine definitions by asset ID (F-007, I-135): manifest entries of type {@code asm}, parsed and
 * validated on first use (including that every state's clip exists in the clip set), then cached. {@link #reload}
 * replaces one at runtime; running machines switch on their next update.
 */
@PublicApi
public final class AsmLibrary {

    private static final List<String> ROOT = List.of("version", "clipSet", "parameters", "initial", "states",
            "transitions");
    private static final List<String> STATE = List.of("clip", "speed", "speedParam", "exitOnFinish");
    private static final List<String> TRANSITION = List.of("from", "to", "when", "priority", "minTime", "exitTime");
    private static final Pattern NAME = Pattern.compile("(!?)\\s*([A-Za-z_][A-Za-z0-9_.]*)");
    private static final Pattern COMPARISON =
            Pattern.compile("([A-Za-z_][A-Za-z0-9_.]*)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)");

    private static final Log LOG = Log.get("animation");

    private final Assets assets;
    private final AnimationLibrary clips;
    private final Map<String, AsmDefinition> cache = new HashMap<>();
    private int generation;

    public AsmLibrary(Assets assets, AnimationLibrary clips) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.clips = Objects.requireNonNull(clips, "clips");
    }

    /**
     * The definition loaded for a manifest ID.
     *
     * @throws AnimationException listing every problem
     * @throws IllegalArgumentException if the manifest does not list the ID
     */
    public AsmDefinition definition(String id) {
        if (assets.generation() != generation) {
            refresh();
        }
        AsmDefinition cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        if (!assets.contains(id)) {
            throw new IllegalArgumentException("Unknown state machine \"" + id + "\": not in the asset manifest");
        }
        AsmDefinition def = parse(id, assets.path(id), assets.get(id, JsonValue.class));
        cache.put(id, def);
        return def;
    }

    /**
     * Replaces a definition with new JSON (hot reload, I-150). Running machines switch on their next update.
     *
     * @throws AnimationException if the new JSON is invalid (the old definition stays)
     */
    public void reload(String id, JsonValue json) {
        cache.put(id, parse(id, assets.path(id), json));
    }

    /**
     * Hot reload (F-008): assets changed, so every cached definition is built again (its JSON or its clip set may
     * have changed). One that is now invalid keeps its old version and the problems are logged.
     */
    private void refresh() {
        generation = assets.generation();
        for (Map.Entry<String, AsmDefinition> e : cache.entrySet()) {
            String id = e.getKey();
            try {
                e.setValue(parse(id, assets.path(id), assets.get(id, JsonValue.class)));
            } catch (AnimationException ex) {
                LOG.error("Hot reload of state machine \"" + id + "\" failed; keeping the old version: "
                        + ex.problems());
            } catch (RuntimeException ex) {
                LOG.error("Hot reload of state machine \"" + id + "\" failed; keeping the old version: "
                        + ex.getMessage());
            }
        }
    }

    private AsmDefinition parse(String id, String file, JsonValue root) {
        List<String> problems = new ArrayList<>();
        String f = file + ": ";
        if (root == null || !root.isObject()) {
            throw new AnimationException("Invalid state machine \"" + id + "\"", List.of(f + "must be a JSON object"));
        }
        unknown(root, ROOT, f, problems);
        JsonValue version = root.get("version");
        if (version == null || !version.isLong() || version.asInt() != 1) {
            problems.add(f + "version: required, must be 1");
        }
        String clipSetId = root.getString("clipSet", null);
        ClipSet clipSet = null;
        if (clipSetId == null) {
            problems.add(f + "clipSet: required (a clip set asset ID)");
        } else {
            try {
                clipSet = clips.clipSet(clipSetId);
            } catch (AnimationException e) {
                problems.add(f + "clipSet: clip set \"" + clipSetId + "\" is invalid: " + e.problems());
            } catch (IllegalArgumentException e) {
                problems.add(f + "clipSet: " + e.getMessage());
            }
        }

        Map<String, ParamType> params = new LinkedHashMap<>();
        JsonValue p = root.get("parameters");
        if (p != null) {
            for (JsonValue v = p.child; v != null; v = v.next) {
                String type = v.isString() ? v.asString() : "";
                switch (type) {
                    case "bool" -> params.put(v.name, ParamType.BOOL);
                    case "float" -> params.put(v.name, ParamType.FLOAT);
                    case "int" -> params.put(v.name, ParamType.INT);
                    case "trigger" -> params.put(v.name, ParamType.TRIGGER);
                    default -> problems.add(f + "parameters." + v.name
                            + ": type must be \"bool\", \"float\", \"int\" or \"trigger\"");
                }
            }
        }

        Map<String, AsmDefinition.State> states = new LinkedHashMap<>();
        JsonValue st = root.get("states");
        if (st == null || !st.isObject() || st.size == 0) {
            problems.add(f + "states: required, an object of state name to state");
        } else {
            for (JsonValue s = st.child; s != null; s = s.next) {
                String w = f + "states." + s.name;
                if (!s.isObject()) {
                    problems.add(w + ": must be an object");
                    continue;
                }
                unknown(s, STATE, w + ".", problems);
                String clip = s.getString("clip", null);
                float duration = 0f;
                if (clip == null) {
                    problems.add(w + ".clip: required");
                } else if (clipSet != null && clipSet.clip(clip) == null) {
                    problems.add(w + ".clip: unknown clip \"" + clip + "\" in clip set \"" + clipSetId + "\" "
                            + clipSet.names());
                } else if (clipSet != null) {
                    duration = clipSet.clip(clip).totalDuration();
                }
                float speed = number(s, "speed", 1f, w + ".speed", 0f, problems);
                String speedParam = s.getString("speedParam", null);
                if (speedParam != null && params.get(speedParam) != ParamType.FLOAT
                        && params.get(speedParam) != ParamType.INT) {
                    problems.add(w + ".speedParam: \"" + speedParam + "\" must be a float or int parameter");
                }
                JsonValue exit = s.get("exitOnFinish");
                if (exit != null && !exit.isBoolean()) {
                    problems.add(w + ".exitOnFinish: must be true or false");
                }
                states.put(s.name, new AsmDefinition.State(s.name, clip, speed, speedParam,
                        exit != null && exit.isBoolean() && exit.asBoolean(), duration));
            }
        }
        String initial = root.getString("initial", null);
        if (initial == null) {
            problems.add(f + "initial: required (a state name)");
        } else if (!states.isEmpty() && !states.containsKey(initial)) {
            problems.add(f + "initial: unknown state \"" + initial + "\"");
        }

        List<AsmDefinition.Transition> transitions = new ArrayList<>();
        JsonValue tr = root.get("transitions");
        if (tr != null && !tr.isArray()) {
            problems.add(f + "transitions: must be a list");
        } else if (tr != null) {
            int i = 0;
            for (JsonValue t = tr.child; t != null; t = t.next, i++) {
                String w = f + "transitions[" + i + "]";
                if (!t.isObject()) {
                    problems.add(w + ": must be an object");
                    continue;
                }
                unknown(t, TRANSITION, w + ".", problems);
                String from = t.getString("from", null);
                String to = t.getString("to", null);
                if (from == null || (!from.equals("*") && !states.containsKey(from))) {
                    problems.add(w + ".from: " + (from == null ? "required" : "unknown state \"" + from + "\""));
                }
                if (to == null || !states.containsKey(to)) {
                    problems.add(w + ".to: " + (to == null ? "required" : "unknown state \"" + to + "\""));
                }
                List<AsmDefinition.Term> when = conditions(t.getString("when", null), params, w + ".when", problems);
                JsonValue pr = t.get("priority");
                int priority = 0;
                if (pr != null) {
                    if (!pr.isLong()) {
                        problems.add(w + ".priority: must be a whole number");
                    } else {
                        priority = pr.asInt();
                    }
                }
                float minTime = number(t, "minTime", 0f, w + ".minTime", 0f, problems);
                float exitTime = t.has("exitTime") ? number(t, "exitTime", 0f, w + ".exitTime", 0f, problems)
                        : Float.NaN;
                transitions.add(new AsmDefinition.Transition(i, from, to, when, priority, minTime, exitTime));
            }
        }
        if (!problems.isEmpty()) {
            throw new AnimationException("Invalid state machine \"" + id + "\"", problems);
        }
        return new AsmDefinition(id, clipSetId, params, initial, states, transitions);
    }

    private static List<AsmDefinition.Term> conditions(String when, Map<String, ParamType> params, String where,
            List<String> problems) {
        List<AsmDefinition.Term> terms = new ArrayList<>();
        if (when == null || when.isBlank()) {
            return terms;
        }
        for (String raw : when.split("&&", -1)) {
            String term = raw.trim();
            Matcher cmp = COMPARISON.matcher(term);
            Matcher name = NAME.matcher(term);
            if (cmp.matches()) {
                String param = cmp.group(1);
                ParamType type = params.get(param);
                if (type == null) {
                    problems.add(where + ": unknown parameter \"" + param + "\"");
                } else if (type == ParamType.BOOL || type == ParamType.TRIGGER) {
                    problems.add(where + ": \"" + param + "\" is a " + type.name().toLowerCase(java.util.Locale.ROOT)
                            + " and cannot be compared; write \"" + param + "\" or \"!" + param + "\"");
                } else {
                    terms.add(new AsmDefinition.Term(param, cmp.group(2), Float.parseFloat(cmp.group(3))));
                }
            } else if (name.matches()) {
                String param = name.group(2);
                boolean not = !name.group(1).isEmpty();
                ParamType type = params.get(param);
                if (type == null) {
                    problems.add(where + ": unknown parameter \"" + param + "\"");
                } else if (type == ParamType.FLOAT || type == ParamType.INT) {
                    problems.add(where + ": \"" + param + "\" is a number; compare it, for example \"" + param
                            + " > 0\"");
                } else if (not && type == ParamType.TRIGGER) {
                    problems.add(where + ": a trigger cannot be negated (\"!" + param + "\")");
                } else {
                    terms.add(new AsmDefinition.Term(param, not ? "!" : null, 0f));
                }
            } else {
                problems.add(where + ": cannot read \"" + term + "\" (expected name, !name or name OP number, "
                        + "joined by &&)");
            }
        }
        return terms;
    }

    private static float number(JsonValue json, String key, float fallback, String where, float min,
            List<String> problems) {
        JsonValue v = json.get(key);
        if (v == null) {
            return fallback;
        }
        if (!v.isNumber() || v.asFloat() < min) {
            problems.add(where + ": must be a number >= " + (min == 0f ? "0" : String.valueOf(min)));
            return fallback;
        }
        return v.asFloat();
    }

    private static void unknown(JsonValue json, List<String> allowed, String prefix, List<String> problems) {
        for (JsonValue f = json.child; f != null; f = f.next) {
            if (!allowed.contains(f.name)) {
                problems.add(prefix + f.name + ": unknown field (expected " + String.join(", ", allowed) + ")");
            }
        }
    }
}
