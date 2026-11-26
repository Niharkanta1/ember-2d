package dev.ember2d.input.internal;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectIntMap;
import dev.ember2d.core.Time;
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.input.InputBindings;
import dev.ember2d.input.InputCapture;
import dev.ember2d.input.InputContextChanged;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private final TouchTracker.Sink touchSink = this::touchInput;
    private final Vector2 dragA = new Vector2();
    private final Vector2 dragB = new Vector2();
    private float density = 1f;
    private boolean keyboardCaptured;
    private boolean pointerCaptured;
    private ScreenToWorld screenToWorld;
    private InputCapture capture;
    private InputBindings bindings;

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

    /** Replaces the bindings; resets all state and the context stack (called at boot). */
    public void setBindings(InputBindings bindings) {
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        touch.configure(bindings.touch(), density);
        touch.releaseAll();
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
            actionCodes[a] = codes(bindings.actionInputs(name));
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
            apply(event >>> 1, (event & 1) == 1, keyboardCaptured, pointerCaptured);
        }
        recorded.clear();
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

    private static int[] codes(List<String> names) {
        int[] codes = new int[names.size()];
        for (int i = 0; i < codes.length; i++) {
            codes[i] = InputNames.code(names.get(i));
        }
        return codes;
    }
}
