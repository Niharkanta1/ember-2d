package dev.ember2d.input.internal;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectIntMap;
import dev.ember2d.core.Time;
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputCapture;
import dev.ember2d.input.InputContextChanged;
import dev.ember2d.input.TouchControlsConfig;
import dev.ember2d.input.TouchLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The engine's {@link Input} (F-005). The engine drives it: {@link #beginFrame()} at the start of the {@code INPUT}
 * phase applies the events recorded by {@link #source()}, and {@link #beginFixedStep()}/{@link #endFixedStep()}
 * bracket each fixed step. All state lives in arrays indexed by action, axis and physical code; nothing is allocated
 * per frame.
 */
public final class DefaultInput implements Input {

    private static final float DIAGONAL = (float) (1.0 / Math.sqrt(2.0));

    private final Time time;
    private final EventBus events;
    private final GdxInputSource source = new GdxInputSource();
    private final TouchTracker touch = new TouchTracker();
    private final TouchControls controls = new TouchControls();
    private int stickAxis = -1;
    private int aimAxis = -1;
    private int[] buttonActions = new int[0];
    private final boolean[] buttonLive = new boolean[TouchControlsConfig.MAX_BUTTONS];
    private boolean autoHide;
    private boolean haptics = true;
    private final TouchTracker.Sink touchSink = this::touchInput;
    private final Vector2 dragA = new Vector2();
    private final Vector2 dragB = new Vector2();
    private float density = 1f;
    private boolean keyboardCaptured;
    private boolean pointerCaptured;
    private ScreenToWorld screenToWorld;
    private InputCapture capture;
    private InputBindings bindings;
    private InputBindings fileBindings;
    private final Map<String, List<String>> overrides = new LinkedHashMap<>();

    // Physical inputs (InputNames codes)
    private final boolean[] physicalDown = new boolean[InputNames.COUNT];
    private final boolean[] stale = new boolean[InputNames.COUNT];   // held across a context change: up until re-pressed
    private final int[] owner = new int[InputNames.COUNT];           // context index that receives the input, or -1

    // Contexts
    private List<String> contexts;
    private final ObjectIntMap<String> contextIndex = new ObjectIntMap<>();
    private boolean[][] contextBinds;
    private final IntArray stack = new IntArray(4);

    // Actions
    private final ObjectIntMap<String> actionIndex = new ObjectIntMap<>();
    private int[] actionContext;
    private int[][] actionCodes;
    private int[][] codeActions;
    private boolean[] actionDown;
    private boolean[] framePressed;
    private boolean[] frameReleased;
    private boolean[] fixedPressed;
    private boolean[] fixedReleased;
    private float[] held;

    // Axes: codes per direction (1D: negative, positive; 2D: left, right, up, down)
    private final ObjectIntMap<String> axisIndex = new ObjectIntMap<>();
    private int[] axisContext;
    private boolean[] axis2D;
    private boolean[] axisNormalize;
    private int[][][] axisCodes;

    private boolean inFixedStep;
    private boolean releasePending;

    public DefaultInput(Time time, EventBus events) {
        this.time = Objects.requireNonNull(time, "time");
        this.events = Objects.requireNonNull(events, "events");
        setBindings(InputBindings.empty());
    }

    /** The libGDX input processor feeding this input. */
    public GdxInputSource source() {
        return source;
    }

    /** The current bindings. */
    public InputBindings bindings() {
        return bindings;
    }

    /**
     * Replaces the bindings file (boot, hot reload); resets all state and the context stack. The player's overrides
     * that still fit the new file are applied over it; others are dropped.
     */
    public void setBindings(InputBindings bindings) {
        fileBindings = Objects.requireNonNull(bindings, "bindings");
        InputBindings effective = bindings;
        for (Iterator<Map.Entry<String, List<String>>> it = overrides.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, List<String>> e = it.next();
            try {
                effective = effective.withActionInputs(e.getKey(), e.getValue());
            } catch (IllegalArgumentException ex) {
                it.remove();
            }
        }
        apply(effective);
    }

    @Override
    public void rebindAction(String action, List<String> inputs) {
        InputBindings next = bindings.withActionInputs(action, inputs);   // validates
        overrides.put(action, List.copyOf(next.actionInputs(action)));
        reapplyKeepingContexts(next);
    }

    @Override
    public void resetBinding(String action) {
        if (overrides.remove(action) != null) {
            InputBindings effective = fileBindings;
            for (Map.Entry<String, List<String>> e : overrides.entrySet()) {
                effective = effective.withActionInputs(e.getKey(), e.getValue());
            }
            reapplyKeepingContexts(effective);
        }
    }

    @Override
    public Map<String, List<String>> bindingOverrides() {
        return new LinkedHashMap<>(overrides);
    }

    @Override
    public List<String> actionInputs(String action) {
        if (!bindings.actions().contains(action)) {
            throw new IllegalArgumentException("Unknown action \"" + action + "\"");
        }
        return bindings.actionInputs(action);
    }

    private void reapplyKeepingContexts(InputBindings next) {
        int[] kept = stack.toArray();
        apply(next);
        stack.clear();
        for (int index : kept) {
            stack.add(index);
        }
        updateOwners();
    }

    private void apply(InputBindings bindings) {
        this.bindings = bindings;
        touch.configure(bindings.touch(), density);
        touch.releaseAll();
        controls.configure(bindings.touchControls(), density);
        contexts = bindings.contexts();
        contextIndex.clear();
        for (int i = 0; i < contexts.size(); i++) {
            contextIndex.put(contexts.get(i), i);
        }
        contextBinds = new boolean[contexts.size()][InputNames.COUNT];

        List<String> actionNames = new ArrayList<>(bindings.actions());
        int actionCount = actionNames.size();
        actionIndex.clear();
        actionContext = new int[actionCount];
        actionCodes = new int[actionCount][];
        actionDown = new boolean[actionCount];
        framePressed = new boolean[actionCount];
        frameReleased = new boolean[actionCount];
        fixedPressed = new boolean[actionCount];
        fixedReleased = new boolean[actionCount];
        held = new float[actionCount];
        IntArray[] perCode = new IntArray[InputNames.COUNT];
        for (int a = 0; a < actionCount; a++) {
            String name = actionNames.get(a);
            actionIndex.put(name, a);
            actionContext[a] = contextIndex.get(bindings.contextOf(name), 0);
            actionCodes[a] = withButtons(codes(bindings.actionInputs(name)), name, bindings.touchControls());
            for (int code : actionCodes[a]) {
                contextBinds[actionContext[a]][code] = true;
                if (perCode[code] == null) {
                    perCode[code] = new IntArray(2);
                }
                perCode[code].add(a);
            }
        }
        codeActions = new int[InputNames.COUNT][];
        for (int code = 0; code < InputNames.COUNT; code++) {
            codeActions[code] = perCode[code] == null ? new int[0] : perCode[code].toArray();
        }

        List<String> axisNames = new ArrayList<>(bindings.axes());
        int axisCount = axisNames.size();
        axisIndex.clear();
        axisContext = new int[axisCount];
        axis2D = new boolean[axisCount];
        axisNormalize = new boolean[axisCount];
        axisCodes = new int[axisCount][][];
        for (int x = 0; x < axisCount; x++) {
            String name = axisNames.get(x);
            axisIndex.put(name, x);
            axisContext[x] = contextIndex.get(bindings.contextOf(name), 0);
            axis2D[x] = bindings.isAxis2D(name);
            axisNormalize[x] = bindings.axisNormalized(name);
            String[] directions = axis2D[x] ? new String[] {"left", "right", "up", "down"}
                    : new String[] {"negative", "positive"};
            axisCodes[x] = new int[directions.length][];
            for (int d = 0; d < directions.length; d++) {
                axisCodes[x][d] = codes(bindings.axisInputs(name, directions[d]));
                for (int code : axisCodes[x][d]) {
                    contextBinds[axisContext[x]][code] = true;
                }
            }
        }

        stickAxis = -1;
        if (bindings.touchControls() != null && bindings.touchControls().joystick() != null) {
            stickAxis = axisIndex.get(bindings.touchControls().joystick().axis(), -1);
        } else if (bindings.touchControls() != null && bindings.touchControls().dpad() != null) {
            stickAxis = axisIndex.get(bindings.touchControls().dpad().axis(), -1);
        }
        aimAxis = -1;
        if (bindings.touchControls() != null && bindings.touchControls().aimStick() != null) {
            aimAxis = axisIndex.get(bindings.touchControls().aimStick().axis(), -1);
        }
        buttonActions = new int[bindings.touchControls() == null ? 0 : bindings.touchControls().buttons().size()];
        for (int b = 0; b < buttonActions.length; b++) {
            buttonActions[b] = actionIndex.get(bindings.touchControls().buttons().get(b).action(), -1);
        }
        Arrays.fill(physicalDown, false);
        Arrays.fill(stale, false);
        stack.clear();
        stack.add(0);
        updateOwners();
        releasePending = false;
        inFixedStep = false;
    }

    /** Sets the screen density (dp to pixels) used for touch thresholds; values <= 0 mean 1. */
    public void setDensity(float density) {
        this.density = density > 0f ? density : 1f;
        touch.configure(bindings.touch(), this.density);
        controls.configure(bindings.touchControls(), this.density);
    }

    /** The window size in pixels (as touch coordinates report it), for placing the on-screen controls. */
    public void setScreenSize(float width, float height) {
        controls.setScreen(width, height);
    }

    /** Safe-area insets in window pixels; the on-screen controls stay inside them (I-225). */
    public void setSafeInsets(float left, float top, float right, float bottom) {
        controls.setInsets(left, top, right, bottom);
    }

    /** {@code AUTO} touch controls: hide after keyboard use, show again on the next touch (I-225). */
    public void setAutoHide(boolean autoHide) {
        this.autoHide = autoHide;
        if (!autoHide) {
            controls.setAutoHidden(false);
        }
    }

    /** Shows or hides the on-screen controls (hiding lets go of them). */
    public void setTouchControlsVisible(boolean visible) {
        if (!visible && controls.active()) {
            releaseAll();
        }
        controls.setVisible(visible);
    }

    /** The on-screen controls, for drawing. */
    public TouchControls touchControls() {
        return controls;
    }

    /** Sets how window pixels map to world units; {@code null} keeps window pixels. */
    public void setScreenToWorld(ScreenToWorld screenToWorld) {
        this.screenToWorld = screenToWorld;
    }

    // ---- driven by the engine ----

    /** Start of the {@code INPUT} phase: applies pending releases and recorded events, advances held times. */
    public void beginFrame() {
        Arrays.fill(framePressed, false);
        Arrays.fill(frameReleased, false);
        if (releasePending) {
            releasePending = false;
            touch.releaseAll();
            controls.reset();
            for (int code = 0; code < InputNames.COUNT; code++) {
                if (physicalDown[code]) {
                    apply(code, false, false, false);
                }
            }
        }
        keyboardCaptured = capture != null && capture.capturesKeyboard();
        pointerCaptured = capture != null && capture.capturesPointer();
        IntArray recorded = source.events();
        for (int i = 0; i < recorded.size; i++) {
            int event = recorded.items[i];
            int code = event >>> 1;
            boolean down = (event & 1) == 1;
            if (autoHide && down && code < InputNames.MOUSE_BASE && code != com.badlogic.gdx.Input.Keys.BACK && !controls.autoHidden()
                    && controls.active()) {
                releaseControls();
                controls.setAutoHidden(true);   // keyboard in use: hide the on-screen controls
            }
            apply(code, down, keyboardCaptured, pointerCaptured);
        }
        recorded.clear();
        if (controls.autoHidden() && hasTouchDown(source.touches())) {
            controls.setAutoHidden(false);   // a touch brings them back; the touch itself acts normally
        }
        updateLiveControls();
        controls.process(source.touches(), pointerCaptured, touchSink);   // claims its touches first
        touch.process(source.touches(), time.unscaledElapsed(), pointerCaptured, touchSink);
        float delta = time.delta();
        for (int a = 0; a < actionDown.length; a++) {
            if (actionDown[a] && !framePressed[a]) {
                held[a] += delta;
            }
        }
        if (time.paused()) {   // the fixed step does not run while paused: keep no edges for it
            clearFixedEdges();
        }
    }

    /** Before the systems of one fixed step. */
    public void beginFixedStep() {
        inFixedStep = true;
    }

    /** After one fixed step: its edges are consumed. */
    public void endFixedStep() {
        inFixedStep = false;
        clearFixedEdges();
    }

    // ---- Input ----

    @Override
    public boolean pressed(String action) {
        return actionDown[action(action)];
    }

    @Override
    public boolean justPressed(String action) {
        int a = action(action);
        return inFixedStep ? fixedPressed[a] : framePressed[a];
    }

    @Override
    public boolean justReleased(String action) {
        int a = action(action);
        return inFixedStep ? fixedReleased[a] : frameReleased[a];
    }

    @Override
    public float heldTime(String action) {
        int a = action(action);
        return actionDown[a] ? held[a] : 0f;
    }

    @Override
    public float axis(String axis) {
        int x = axis(axis, false);
        int ctx = axisContext[x];
        return direction(axisCodes[x][1], ctx) - direction(axisCodes[x][0], ctx);
    }

    @Override
    public Vector2 axis2(String axis, Vector2 out) {
        int x = axis(axis, true);
        int ctx = axisContext[x];
        if (x == stickAxis && controls.stickHeld() && stack.peek() == ctx) {
            return out.set(controls.valueX(), controls.valueY());   // analog, already within the unit circle
        }
        if (x == aimAxis && controls.aimHeld() && stack.peek() == ctx) {
            return out.set(controls.aimValueX(), controls.aimValueY());   // the aim stick (I-320)
        }
        float dx = direction(axisCodes[x][1], ctx) - direction(axisCodes[x][0], ctx);
        float dy = direction(axisCodes[x][2], ctx) - direction(axisCodes[x][3], ctx);
        if (axisNormalize[x] && dx != 0f && dy != 0f) {
            dx *= DIAGONAL;
            dy *= DIAGONAL;
        }
        return out.set(dx, dy);
    }

    @Override
    public Vector2 pointerWorld(Vector2 out) {
        return toWorld(source.pointerX(), source.pointerY(), out);
    }

    @Override
    public Vector2 pointerScreen(Vector2 out) {
        return out.set(source.pointerX(), source.pointerY());
    }

    @Override
    public int maxPointers() {
        return GdxInputSource.MAX_POINTERS;
    }

    @Override
    public boolean pointerDown(int pointer) {
        return touch.down(pointer(pointer));
    }

    @Override
    public boolean pointerJustDown(int pointer) {
        return touch.justDown(pointer(pointer));
    }

    @Override
    public boolean pointerJustUp(int pointer) {
        return touch.justUp(pointer(pointer));
    }

    @Override
    public Vector2 pointerScreen(int pointer, Vector2 out) {
        int p = pointer(pointer);
        return out.set(touch.x(p), touch.y(p));
    }

    @Override
    public Vector2 pointerWorld(int pointer, Vector2 out) {
        int p = pointer(pointer);
        return toWorld(touch.x(p), touch.y(p), out);
    }

    @Override
    public Vector2 dragDelta(Vector2 out) {
        int p = touch.dragPointer();
        if (p < 0) {
            return out.setZero();
        }
        toWorld(touch.x(p), touch.y(p), dragA);
        toWorld(touch.previousX(p), touch.previousY(p), dragB);
        return out.set(dragA).sub(dragB);
    }

    @Override
    public boolean touchControlsActive() {
        return controls.active();
    }

    @Override
    public void setTouchLayout(TouchLayout layout) {
        controls.setLayout(layout.copy());
    }

    @Override
    public TouchLayout touchLayout() {
        return controls.layout().copy();
    }

    @Override
    public void setTouchButtonVisible(String action, boolean visible) {
        if (!controls.setButtonHidden(action, !visible)) {
            throw new IllegalArgumentException("No on-screen button drives the action \"" + action + "\"");
        }
        if (!visible) {
            updateLiveControls();
        }
    }

    @Override
    public List<String> touchControlNames() {
        List<String> names = new ArrayList<>();
        if (controls.hasStick()) {
            names.add(controls.stickName());
        }
        for (int b = 0; b < controls.buttonCount(); b++) {
            names.add(controls.buttonName(b));
        }
        return names;
    }

    @Override
    public boolean touchControlCircle(String control, Vector3 out) {
        if (controls.hasStick() && controls.stickName().equals(control)) {
            out.set(controls.stickHomeX(), controls.stickHomeY(), controls.stickRadius());
            return true;
        }
        for (int b = 0; b < controls.buttonCount(); b++) {
            if (controls.buttonName(b).equals(control)) {
                out.set(controls.buttonX(b), controls.buttonY(b), controls.buttonRadius(b));
                return true;
            }
        }
        return false;
    }

    @Override
    public void moveTouchControl(String control, float windowX, float windowY) {
        TouchControls c = controls;
        float w = c.screenWidth();
        float h = c.screenHeight();
        float x = Math.max(c.insetLeft(), Math.min(w - c.insetRight(), windowX));
        float y = Math.max(c.insetBottom(), Math.min(h - c.insetTop(), windowY));
        float d = c.density();
        TouchLayout layout = c.layout();
        if (c.hasStick() && c.stickName().equals(control)) {
            layout.move(control, (x - c.insetLeft()) / d, (y - c.insetBottom()) / d);
            return;
        }
        for (int b = 0; b < c.buttonCount(); b++) {
            if (c.buttonName(b).equals(control)) {
                float fromTop = (y - (h - c.insetTop())) / d;   // negative: from the top edge
                layout.move(control, (w - c.insetRight() - x) / d, y > h / 2f ? Math.min(-0.001f, fromTop)
                        : (y - c.insetBottom()) / d);
                return;
            }
        }
    }

    @Override
    public void setTouchControlsEditing(boolean editing) {
        if (editing) {
            releaseControls();
        }
        controls.setEditing(editing);
    }

    @Override
    public void vibrate(int milliseconds) {
        if (haptics && milliseconds > 0 && Gdx.input != null) {
            Gdx.input.vibrate(milliseconds);
        }
    }

    @Override
    public void setHapticsEnabled(boolean enabled) {
        haptics = enabled;
    }

    @Override
    public boolean hapticsEnabled() {
        return haptics;
    }

    @Override
    public Vector2 joystickValue(Vector2 out) {
        return out.set(controls.valueX(), controls.valueY());
    }

    @Override
    public void pushContext(String context) {
        int index = contextIndex.get(context, -1);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown input context \"" + context + "\"; defined: " + contexts);
        }
        stack.add(index);
        contextsChanged();
    }

    @Override
    public void popContext() {
        if (stack.size <= 1) {
            throw new IllegalStateException("Cannot pop the \"" + InputBindings.GAMEPLAY + "\" input context");
        }
        stack.pop();
        contextsChanged();
    }

    @Override
    public String topContext() {
        return contexts.get(stack.peek());
    }

    @Override
    public boolean hasAction(String name) {
        return actionIndex.containsKey(name) || axisIndex.containsKey(name);
    }

    @Override
    public void setCapture(InputCapture capture) {
        this.capture = capture;
    }

    @Override
    public void releaseAll() {
        releasePending = true;
    }

    // ---- internals ----

    /** Each control works only while its context is on top (I-225); buttons let go report a release. */
    private void updateLiveControls() {
        int top = stack.peek();
        boolean stick = stickAxis >= 0 && axisContext[stickAxis] == top;
        for (int b = 0; b < buttonActions.length; b++) {
            buttonLive[b] = buttonActions[b] >= 0 && actionContext[buttonActions[b]] == top;
        }
        boolean aim = aimAxis >= 0 && axisContext[aimAxis] == top;
        controls.setLive(stick, buttonLive, aim, touchSink);
    }

    /** Lets go of every pressed on-screen control, reporting button releases. */
    private void releaseControls() {
        boolean[] none = new boolean[TouchControlsConfig.MAX_BUTTONS];
        controls.setLive(false, none, false, touchSink);
        controls.reset();
    }

    private static boolean hasTouchDown(IntArray touches) {
        for (int i = 0; i + 3 < touches.size; i += 4) {
            if (touches.items[i] == GdxInputSource.TOUCH_DOWN) {
                return true;
            }
        }
        return false;
    }

    private void touchInput(int code, boolean down) {
        apply(code, down, keyboardCaptured, pointerCaptured);
    }

    private Vector2 toWorld(float x, float y, Vector2 out) {
        return screenToWorld == null ? out.set(x, y) : screenToWorld.toWorld(x, y, out);
    }

    private static int pointer(int pointer) {
        if (pointer < 0 || pointer >= GdxInputSource.MAX_POINTERS) {
            throw new IllegalArgumentException("Pointer index must be 0.." + (GdxInputSource.MAX_POINTERS - 1)
                    + " (was " + pointer + ")");
        }
        return pointer;
    }

    private void apply(int code, boolean down, boolean keyboardCaptured, boolean pointerCaptured) {
        if (down) {
            boolean pointer = code >= InputNames.MOUSE_BASE;
            if (physicalDown[code] || (pointer ? pointerCaptured : keyboardCaptured)) {
                return;
            }
            physicalDown[code] = true;
        } else {
            if (!physicalDown[code]) {
                return;
            }
            physicalDown[code] = false;
        }
        stale[code] = false;
        for (int a : codeActions[code]) {
            refresh(a);
        }
    }

    private void contextsChanged() {
        int[] previous = owner.clone();   // only on push/pop, not per frame
        updateOwners();
        for (int code = 0; code < InputNames.COUNT; code++) {
            if (physicalDown[code] && previous[code] != owner[code]) {
                stale[code] = true;
            }
        }
        for (int a = 0; a < actionDown.length; a++) {
            refresh(a);
        }
        events.publish(new InputContextChanged(topContext()));
    }

    private void updateOwners() {
        for (int code = 0; code < InputNames.COUNT; code++) {
            owner[code] = -1;
            for (int i = stack.size - 1; i >= 0; i--) {
                if (contextBinds[stack.get(i)][code]) {
                    owner[code] = stack.get(i);
                    break;
                }
            }
        }
    }

    private void refresh(int a) {
        boolean down = false;
        for (int code : actionCodes[a]) {
            if (live(code, actionContext[a])) {
                down = true;
                break;
            }
        }
        if (down == actionDown[a]) {
            return;
        }
        actionDown[a] = down;
        held[a] = 0f;
        if (down) {
            framePressed[a] = true;
            fixedPressed[a] = true;
        } else {
            frameReleased[a] = true;
            fixedReleased[a] = true;
        }
    }

    private boolean live(int code, int context) {
        return physicalDown[code] && !stale[code] && owner[code] == context;
    }

    private float direction(int[] codes, int context) {
        for (int code : codes) {
            if (live(code, context)) {
                return 1f;
            }
        }
        return 0f;
    }

    private void clearFixedEdges() {
        Arrays.fill(fixedPressed, false);
        Arrays.fill(fixedReleased, false);
    }

    private int action(String name) {
        int a = actionIndex.get(name, -1);
        if (a < 0) {
            if (axisIndex.containsKey(name)) {
                throw new IllegalArgumentException("\"" + name + "\" is an input axis; read it with axis() or axis2()");
            }
            throw new IllegalArgumentException("Unknown input action \"" + name + "\"; defined: " + bindings.actions());
        }
        return a;
    }

    private int axis(String name, boolean twoD) {
        int x = axisIndex.get(name, -1);
        if (x < 0) {
            if (actionIndex.containsKey(name)) {
                throw new IllegalArgumentException("\"" + name + "\" is an input action, not an axis");
            }
            throw new IllegalArgumentException("Unknown input axis \"" + name + "\"; defined: " + bindings.axes());
        }
        if (axis2D[x] != twoD) {
            throw new IllegalArgumentException("Input axis \"" + name + "\" is " + (axis2D[x] ? "2D; use axis2()"
                    : "1D; use axis()"));
        }
        return x;
    }

    /** Adds the virtual codes of the on-screen buttons that press this action. */
    private static int[] withButtons(int[] codes, String action, dev.ember2d.input.TouchControlsConfig config) {
        if (config == null) {
            return codes;
        }
        IntArray all = new IntArray(codes);
        for (int b = 0; b < config.buttons().size(); b++) {
            if (action.equals(config.buttons().get(b).action())) {
                all.add(InputNames.VIRTUAL_BASE + b);
            }
        }
        if (config.aimStick() != null && action.equals(config.aimStick().fireAction())) {
            all.add(InputNames.AIM_FIRE);
        }
        return all.toArray();
    }

    private static int[] codes(List<String> names) {
        int[] codes = new int[names.size()];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = InputNames.code(names.get(i));
        }
        return codes;
    }
}
