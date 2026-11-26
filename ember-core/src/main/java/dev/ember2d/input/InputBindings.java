package dev.ember2d.input;

import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.internal.InputNames;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Parsed and validated input bindings (F-005); immutable. Every action and axis belongs to exactly one context, and
 * names are unique across the file. The {@value #GAMEPLAY} context always exists and comes first.
 *
 * <pre>{@code
 * { "version": 1,
 *   "contexts": {
 *     "gameplay": {
 *       "actions": { "fire": ["MOUSE_LEFT"], "pause": ["ESCAPE", "P"] },
 *       "axes": { "move": { "left": ["A"], "right": ["D"], "up": ["W"], "down": ["S"] },
 *                 "zoom": { "negative": ["Q"], "positive": ["E"] } } },
 *     "ui": { "actions": { "ui_back": ["ESCAPE", "BACK"] } } } }
 * }</pre>
 *
 * A 1D axis has {@code negative} and {@code positive}; a 2D axis has {@code left}, {@code right}, {@code up},
 * {@code down} and an optional {@code normalize} (default true: diagonals have length 1).
 *
 * <p>An optional root section {@code "touch": { "tapSlop": 10, "tapTime": 0.3, ... }} sets the gesture thresholds
 * ({@link TouchThresholds}); missing fields keep their defaults.
 */
@PublicApi
public final class InputBindings {

    /** The context at the bottom of the stack; always present. */
    public static final String GAMEPLAY = "gameplay";
    /** The only supported file version. */
    public static final int VERSION = 1;

    private static final List<String> DIRECTIONS_1D = List.of("negative", "positive");
    private static final List<String> DIRECTIONS_2D = List.of("left", "right", "up", "down");

    private record Action(String context, List<String> inputs) {
    }

    private record Axis(String context, boolean twoD, boolean normalize, Map<String, List<String>> directions) {
    }

    private static final List<String> TOUCH_FIELDS = List.of("tapSlop", "tapTime", "doubleTapTime", "longPressTime",
            "swipeDistance", "swipeTime");

    private final List<String> contexts;
    private final Map<String, Action> actions;
    private final Map<String, Axis> axes;
    private final TouchThresholds touch;
    private final TouchControlsConfig touchControls;

    private InputBindings(List<String> contexts, Map<String, Action> actions, Map<String, Axis> axes,
            TouchThresholds touch, TouchControlsConfig touchControls) {
        this.contexts = List.copyOf(contexts);
        this.actions = Collections.unmodifiableMap(actions);
        this.axes = Collections.unmodifiableMap(axes);
        this.touch = touch;
        this.touchControls = touchControls;
    }

    /** Bindings with only an empty {@value #GAMEPLAY} context. */
    public static InputBindings empty() {
        return new InputBindings(List.of(GAMEPLAY), new LinkedHashMap<>(), new LinkedHashMap<>(),
                TouchThresholds.DEFAULTS, null);
    }

    /**
     * Parses and validates a bindings file.
     *
     * @param source the file name used in error messages
     * @throws InputBindingsException listing every problem, each as {@code source: path: message}
     */
    public static InputBindings parse(JsonValue json, String source) {
        List<String> problems = new ArrayList<>();
        List<String> contexts = new ArrayList<>();
        Map<String, Action> actions = new LinkedHashMap<>();
        Map<String, Axis> axes = new LinkedHashMap<>();
        Parser p = new Parser(source, problems);

        if (json == null || !json.isObject()) {
            p.problem("(root)", "must be a JSON object");
            throw p.fail();
        }
        for (JsonValue field = json.child; field != null; field = field.next) {
            if (!"version".equals(field.name) && !"contexts".equals(field.name) && !"touch".equals(field.name)
                    && !"touchControls".equals(field.name)) {
                p.problem(field.name, "unknown field (expected version, contexts, touch, touchControls)");
            }
        }
        JsonValue version = json.get("version");
        if (version == null) {
            p.problem("version", "missing (expected " + VERSION + ")");
        } else if (!version.isLong() || version.asLong() != VERSION) {
            p.problem("version", "unsupported version " + (version.isValue() ? version.asString() : version.type())
                    + " (expected " + VERSION + ")");
        }
        JsonValue contextsJson = json.get("contexts");
        if (contextsJson == null || !contextsJson.isObject()) {
            p.problem("contexts", contextsJson == null ? "missing" : "must be an object of contexts");
        } else {
            if (contextsJson.get(GAMEPLAY) == null) {
                p.problem("contexts", "the \"" + GAMEPLAY + "\" context is required");
            }
            contexts.add(GAMEPLAY);
            for (JsonValue ctx = contextsJson.child; ctx != null; ctx = ctx.next) {
                if (!GAMEPLAY.equals(ctx.name)) {
                    contexts.add(ctx.name);
                }
            }
            for (JsonValue ctx = contextsJson.child; ctx != null; ctx = ctx.next) {
                p.context(ctx, actions, axes);
            }
        }
        TouchThresholds touch = p.touch(json.get("touch"));
        TouchControlsConfig controls = p.touchControls(json.get("touchControls"), actions.keySet(), axes);
        if (!problems.isEmpty()) {
            throw p.fail();
        }
        return new InputBindings(contexts, actions, axes, touch, controls);
    }

    /** The on-screen controls (F-022, I-122), or null if the file has no {@code touchControls} section. */
    public TouchControlsConfig touchControls() {
        return touchControls;
    }

    /** The gesture thresholds (defaults unless the file has a {@code touch} section). */
    public TouchThresholds touch() {
        return touch;
    }

    /** Context names; {@value #GAMEPLAY} first, then file order. */
    public List<String> contexts() {
        return contexts;
    }

    /** Action names in file order. */
    public Set<String> actions() {
        return actions.keySet();
    }

    /** Axis names in file order. */
    public Set<String> axes() {
        return axes.keySet();
    }

    /**
     * The context an action or axis belongs to.
     *
     * @throws IllegalArgumentException if the name is not defined
     */
    public String contextOf(String name) {
        Action action = actions.get(name);
        if (action != null) {
            return action.context();
        }
        return axis(name).context();
    }

    /**
     * The input names bound to an action, such as {@code ["SPACE", "Z"]}.
     *
     * @throws IllegalArgumentException if the action is not defined
     */
    public List<String> actionInputs(String action) {
        Action a = actions.get(action);
        if (a == null) {
            throw new IllegalArgumentException("Unknown input action \"" + action + "\"; defined: " + actions.keySet());
        }
        return a.inputs();
    }

    /** True for a 2D axis (left/right/up/down), false for a 1D axis (negative/positive). */
    public boolean isAxis2D(String axis) {
        return axis(axis).twoD();
    }

    /** True if a 2D axis normalises diagonals; false for 1D axes. */
    public boolean axisNormalized(String axis) {
        return axis(axis).normalize();
    }

    /**
     * The input names bound to one direction of an axis.
     *
     * @param direction {@code negative}/{@code positive} (1D) or {@code left}/{@code right}/{@code up}/{@code down}
     *     (2D)
     * @throws IllegalArgumentException if the axis or the direction is not defined
     */
    public List<String> axisInputs(String axis, String direction) {
        List<String> inputs = axis(axis).directions().get(direction);
        if (inputs == null) {
            throw new IllegalArgumentException("Axis \"" + axis + "\" has no direction \"" + direction + "\"");
        }
        return inputs;
    }

    /** True if any context binds the input name (an action or an axis direction). */
    public boolean binds(String inputName) {
        for (Action a : actions.values()) {
            if (a.inputs().contains(inputName)) {
                return true;
            }
        }
        for (Axis a : axes.values()) {
            for (List<String> inputs : a.directions().values()) {
                if (inputs.contains(inputName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Axis axis(String name) {
        Axis axis = axes.get(name);
        if (axis == null) {
            throw new IllegalArgumentException("Unknown input axis \"" + name + "\"; defined: " + axes.keySet());
        }
        return axis;
    }

    /** Validation helper: collects problems with their path. */
    private static final class Parser {
        private final String source;
        private final List<String> problems;
        private final Set<String> names = new LinkedHashSet<>();

        Parser(String source, List<String> problems) {
            this.source = source;
            this.problems = problems;
        }

        void problem(String path, String message) {
            problems.add(source + ": " + path + ": " + message);
        }

        InputBindingsException fail() {
            return new InputBindingsException("Invalid input bindings " + source, problems);
        }

        TouchControlsConfig touchControls(JsonValue json, Set<String> actionNames, Map<String, Axis> axes) {
            if (json == null) {
                return null;
            }
            String path = "touchControls";
            if (!json.isObject()) {
                problem(path, "must be an object with opacity, joystick, buttons");
                return null;
            }
            fields(json, path, List.of("opacity", "joystick", "buttons"));
            float opacity = number(json, "opacity", 0.35f, path, 0f, 1f);
            TouchControlsConfig.Joystick joystick = null;
            JsonValue j = json.get("joystick");
            if (j != null) {
                String jp = path + ".joystick";
                if (!j.isObject()) {
                    problem(jp, "must be an object");
                } else {
                    fields(j, jp, List.of("axis", "mode", "x", "y", "radius", "deadZone"));
                    String axis = j.getString("axis", null);
                    Axis a = axis == null ? null : axes.get(axis);
                    if (axis == null) {
                        problem(jp + ".axis", "required (a 2D axis name)");
                    } else if (a == null) {
                        problem(jp + ".axis", "unknown axis \"" + axis + "\"");
                    } else if (!a.twoD()) {
                        problem(jp + ".axis", "axis \"" + axis + "\" must be 2D (left/right/up/down)");
                    }
                    String mode = j.getString("mode", "floating");
                    if (!mode.equals("floating") && !mode.equals("fixed")) {
                        problem(jp + ".mode", "must be \"floating\" or \"fixed\"");
                    }
                    float dead = number(j, "deadZone", 0.2f, jp, 0f, 0.95f);
                    joystick = new TouchControlsConfig.Joystick(axis, mode.equals("floating"),
                            number(j, "x", 96f, jp, 0f, Float.MAX_VALUE), number(j, "y", 96f, jp, 0f, Float.MAX_VALUE),
                            positive(j, "radius", 56f, jp), dead);
                }
            }
            List<TouchControlsConfig.Button> buttons = new ArrayList<>();
            JsonValue b = json.get("buttons");
            if (b != null) {
                if (!b.isArray()) {
                    problem(path + ".buttons", "must be a list of buttons");
                } else {
                    if (b.size > TouchControlsConfig.MAX_BUTTONS) {
                        problem(path + ".buttons", "at most " + TouchControlsConfig.MAX_BUTTONS + " buttons (has "
                                + b.size + ")");
                    }
                    int i = 0;
                    for (JsonValue item = b.child; item != null; item = item.next, i++) {
                        String bp = path + ".buttons[" + i + "]";
                        if (!item.isObject()) {
                            problem(bp, "must be an object");
                            continue;
                        }
                        fields(item, bp, List.of("action", "label", "x", "y", "radius"));
                        String action = item.getString("action", null);
                        if (action == null) {
                            problem(bp + ".action", "required (an action name)");
                        } else if (!actionNames.contains(action)) {
                            problem(bp + ".action", "unknown action \"" + action + "\"");
                        }
                        JsonValue label = item.get("label");
                        if (label != null && !label.isString()) {
                            problem(bp + ".label", "must be a string");
                        }
                        if (item.get("x") == null || item.get("y") == null) {
                            problem(bp, "\"x\" and \"y\" are required");
                        }
                        buttons.add(new TouchControlsConfig.Button(action, label == null || !label.isString() ? ""
                                : label.asString(), number(item, "x", 0f, bp, 0f, Float.MAX_VALUE),
                                number(item, "y", 0f, bp, -Float.MAX_VALUE, Float.MAX_VALUE),
                                positive(item, "radius", 40f, bp)));
                    }
                }
            }
            return new TouchControlsConfig(opacity, joystick, buttons);
        }

        private void fields(JsonValue json, String path, List<String> allowed) {
            for (JsonValue f = json.child; f != null; f = f.next) {
                if (!allowed.contains(f.name)) {
                    problem(path + "." + f.name, "unknown field (expected " + String.join(", ", allowed) + ")");
                }
            }
        }

        private float number(JsonValue json, String key, float fallback, String path, float min, float max) {
            JsonValue v = json.get(key);
            if (v == null) {
                return fallback;
            }
            if (!v.isNumber() || v.asFloat() < min || v.asFloat() > max || Float.isInfinite(v.asFloat())) {
                problem(path + "." + key, "must be a number" + (min == 0f && max == 1f ? " from 0 to 1"
                        : min == 0f && max < 1f ? " from 0 to " + max : min == 0f ? " >= 0" : ""));
                return fallback;
            }
            return v.asFloat();
        }

        private float positive(JsonValue json, String key, float fallback, String path) {
            JsonValue v = json.get(key);
            if (v == null) {
                return fallback;
            }
            if (!v.isNumber() || !(v.asFloat() > 0f) || Float.isInfinite(v.asFloat())) {
                problem(path + "." + key, "must be a number > 0");
                return fallback;
            }
            return v.asFloat();
        }

        TouchThresholds touch(JsonValue json) {
            TouchThresholds d = TouchThresholds.DEFAULTS;
            if (json == null) {
                return d;
            }
            if (!json.isObject()) {
                problem("touch", "must be an object of thresholds (" + String.join(", ", TOUCH_FIELDS) + ")");
                return d;
            }
            float[] values = {d.tapSlop(), d.tapTime(), d.doubleTapTime(), d.longPressTime(), d.swipeDistance(),
                d.swipeTime()};
            for (JsonValue field = json.child; field != null; field = field.next) {
                int index = TOUCH_FIELDS.indexOf(field.name);
                if (index < 0) {
                    problem("touch." + field.name, "unknown field (expected " + String.join(", ", TOUCH_FIELDS) + ")");
                } else if (!field.isNumber() || !(field.asFloat() > 0f) || Float.isInfinite(field.asFloat())) {
                    problem("touch." + field.name, "must be a number > 0");
                } else {
                    values[index] = field.asFloat();
                }
            }
            return new TouchThresholds(values[0], values[1], values[2], values[3], values[4], values[5]);
        }

        void context(JsonValue ctx, Map<String, Action> actions, Map<String, Axis> axes) {
            String path = "contexts." + ctx.name;
            if (!ctx.isObject()) {
                problem(path, "must be an object with actions and/or axes");
                return;
            }
            for (JsonValue field = ctx.child; field != null; field = field.next) {
                if (!"actions".equals(field.name) && !"axes".equals(field.name)) {
                    problem(path + "." + field.name, "unknown field (expected actions, axes)");
                }
            }
            JsonValue actionsJson = ctx.get("actions");
            if (actionsJson != null) {
                if (!actionsJson.isObject()) {
                    problem(path + ".actions", "must be an object of action name to input list");
                } else {
                    for (JsonValue a = actionsJson.child; a != null; a = a.next) {
                        String actionPath = path + ".actions." + a.name;
                        if (name(a.name, actionPath)) {
                            actions.put(a.name, new Action(ctx.name, inputs(a, actionPath)));
                        }
                    }
                }
            }
            JsonValue axesJson = ctx.get("axes");
            if (axesJson != null) {
                if (!axesJson.isObject()) {
                    problem(path + ".axes", "must be an object of axis name to directions");
                } else {
                    for (JsonValue a = axesJson.child; a != null; a = a.next) {
                        String axisPath = path + ".axes." + a.name;
                        if (name(a.name, axisPath)) {
                            Axis axis = axis(a, ctx.name, axisPath);
                            if (axis != null) {
                                axes.put(a.name, axis);
                            }
                        }
                    }
                }
            }
        }

        private boolean name(String name, String path) {
            if (name == null || name.isBlank()) {
                problem(path, "empty name");
                return false;
            }
            if (!names.add(name)) {
                problem(path, "name \"" + name + "\" is already used by another action or axis");
                return false;
            }
            return true;
        }

        private Axis axis(JsonValue a, String context, String path) {
            if (!a.isObject()) {
                problem(path, "must be an object with negative/positive or left/right/up/down");
                return null;
            }
            boolean twoD = a.has("left") || a.has("right") || a.has("up") || a.has("down");
            List<String> expected = twoD ? DIRECTIONS_2D : DIRECTIONS_1D;
            Map<String, List<String>> directions = new LinkedHashMap<>();
            boolean normalize = twoD;
            for (JsonValue field = a.child; field != null; field = field.next) {
                if ("normalize".equals(field.name)) {
                    if (!twoD) {
                        problem(path + ".normalize", "only 2D axes (left/right/up/down) can be normalised");
                    } else if (!field.isBoolean()) {
                        problem(path + ".normalize", "must be true or false");
                    } else {
                        normalize = field.asBoolean();
                    }
                } else if (!expected.contains(field.name)) {
                    problem(path + "." + field.name, "unknown direction (expected " + String.join(", ", expected)
                            + (twoD ? ", normalize" : "") + ")");
                }
            }
            for (String direction : expected) {
                JsonValue d = a.get(direction);
                if (d == null) {
                    problem(path, "missing direction \"" + direction + "\"");
                    directions.put(direction, List.of());
                } else {
                    directions.put(direction, inputs(d, path + "." + direction));
                }
            }
            return new Axis(context, twoD, normalize, Collections.unmodifiableMap(directions));
        }

        private List<String> inputs(JsonValue list, String path) {
            if (!list.isArray()) {
                problem(path, "must be a list of input names");
                return List.of();
            }
            List<String> inputs = new ArrayList<>();
            int i = 0;
            for (JsonValue item = list.child; item != null; item = item.next, i++) {
                String itemPath = path + "[" + i + "]";
                if (!item.isString()) {
                    problem(itemPath, "must be an input name string");
                } else if (InputNames.code(item.asString()) < 0) {
                    problem(itemPath, "unknown input name \"" + item.asString() + "\"" + suggestion(item.asString()));
                } else if (!inputs.contains(item.asString())) {
                    inputs.add(item.asString());
                }
            }
            return List.copyOf(inputs);
        }

        private static String suggestion(String name) {
            String upper = name.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
            return InputNames.code(upper) >= 0 ? " (did you mean \"" + upper + "\"?)" : "";
        }
    }
}
