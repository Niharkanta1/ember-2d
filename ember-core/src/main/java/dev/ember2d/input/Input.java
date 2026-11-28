package dev.ember2d.input;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import dev.ember2d.core.PublicApi;
import java.util.List;
import java.util.Map;

/**
 * Named actions and axes (F-005), available as {@code context.input()}. Gameplay code never reads raw keys.
 *
 * <p><b>Edges and the fixed step.</b> Inside the {@code FIXED} phase, {@link #justPressed} and {@link #justReleased}
 * are true in the first fixed step after the edge, even if that step runs one or more frames later; with several
 * fixed steps in one frame only the first sees it. In every other phase they are true in the frame the event arrived.
 * A press and release within one frame give both edges, with {@link #pressed} false. While the game is paused no
 * edges are kept for the fixed step.
 *
 * <p><b>Contexts.</b> Every action and axis belongs to one context. Contexts form a stack with
 * {@value InputBindings#GAMEPLAY} at the bottom; an input bound in a higher context is consumed and reads as up in
 * the contexts below. Actions of contexts that are not on the stack read as up, their axes as 0. An input that is
 * held while the stack changes counts as up for its new owner until it is pressed again.
 *
 * <p>Querying a name that the bindings do not define throws {@link IllegalArgumentException}.
 */
@PublicApi
public interface Input {

    /** True while any input bound to the action is held (and not consumed by a higher context). */
    boolean pressed(String action);

    /** True when the action went down: this frame, or in the first fixed step after it inside {@code FIXED}. */
    boolean justPressed(String action);

    /** True when the action went up: this frame, or in the first fixed step after it inside {@code FIXED}. */
    boolean justReleased(String action);

    /** Game seconds the action has been held (follows time scale, stops while paused); 0 while up. */
    float heldTime(String action);

    /** A 1D axis: -1, 0 or 1 (opposite directions held together cancel). */
    float axis(String axis);

    /** A 2D axis: x right, y up; length at most 1 when the axis is normalised. Returns {@code out}. */
    Vector2 axis2(String axis, Vector2 out);

    /** The mouse (pointer 0) position in world units (the renderer's camera); window pixels without a renderer. */
    Vector2 pointerWorld(Vector2 out);

    /** The mouse position in window pixels, y down (libGDX convention). */
    Vector2 pointerScreen(Vector2 out);

    /** Number of touch pointers tracked (F-022): 5. Pointer indexes are {@code 0..maxPointers()-1}. */
    int maxPointers();

    /**
     * True while the finger (or, on desktop, the left mouse button as pointer 0) is down. Pointer queries are frame
     * view only: read them in {@code INPUT}, {@code UPDATE} or {@code LATE}; fixed-step code should use actions.
     *
     * @throws IllegalArgumentException if the pointer index is out of range
     */
    boolean pointerDown(int pointer);

    /** True in the frame the pointer touched down. */
    boolean pointerJustDown(int pointer);

    /** True in the frame the pointer lifted (a short tap gives both {@code justDown} and {@code justUp}). */
    boolean pointerJustUp(int pointer);

    /** The last position of a touch pointer in window pixels, y down. Returns {@code out}. */
    Vector2 pointerScreen(int pointer, Vector2 out);

    /** The last position of a touch pointer in world units. Returns {@code out}. */
    Vector2 pointerWorld(int pointer, Vector2 out);

    /** World units moved this frame by the lowest-numbered dragging finger; zero when nothing drags. */
    Vector2 dragDelta(Vector2 out);

    /** True while the on-screen controls (F-022, I-122) are configured and shown. */
    boolean touchControlsActive();

    /** True while a gamepad is connected (F-005, I-530); for button hints in the UI. */
    boolean gamepadConnected();

    /** The connected gamepad's name, or "" when none is connected. */
    String gamepadName();

    /** The on-screen joystick's value (x right, y up, length 0-1), zero while it is not held. Returns {@code out}. */
    Vector2 joystickValue(Vector2 out);

    /**
     * Rebinds an action to other inputs (F-013, I-230), over the bindings file. The context stack and other actions are
     * kept; the override survives a reload of the bindings file.
     *
     * @throws IllegalArgumentException for an unknown action, an empty list or an unknown input name
     */
    void rebindAction(String action, List<String> inputs);

    /** Drops an action's override: it uses the bindings file again. */
    void resetBinding(String action);

    /** The player's overrides, action to inputs, in the order they were made (a copy). */
    Map<String, List<String>> bindingOverrides();

    /** The inputs an action is bound to now (with any override). */
    List<String> actionInputs(String action);

    /** Applies the player's layout of the on-screen controls over the bindings file's defaults (I-225). */
    void setTouchLayout(TouchLayout layout);

    /** The current on-screen controls layout (a copy to change and set again). */
    TouchLayout touchLayout();

    /**
     * Shows or hides one on-screen button from game code, for example until an ability is unlocked (I-225).
     *
     * @throws IllegalArgumentException if no on-screen button drives that action
     */
    void setTouchButtonVisible(String action, boolean visible);

    /** The layout names of the configured on-screen controls: {@code joystick} or {@code dpad}, then buttons. */
    List<String> touchControlNames();

    /**
     * A configured control's circle in window pixels, y up: x, y (centre) and z (radius). Returns false (and leaves
     * {@code out}) for an unknown name.
     */
    boolean touchControlCircle(String control, Vector3 out);

    /**
     * Moves a control so its centre is at a window position (pixels, y up), storing it in the layout in dp from its
     * corner; buttons in the upper half are stored from the top edge. Unknown names do nothing.
     */
    void moveTouchControl(String control, float windowX, float windowY);

    /** While editing, every control is drawn but none reacts to touches (the layout editor, I-225). */
    void setTouchControlsEditing(boolean editing);

    /** A short vibration on phones (I-225); nothing on desktop or while haptics are off. */
    void vibrate(int milliseconds);

    void setHapticsEnabled(boolean enabled);

    boolean hapticsEnabled();

    /**
     * Puts a context on top of the stack.
     *
     * @throws IllegalArgumentException if the bindings do not define the context
     */
    void pushContext(String context);

    /**
     * Removes the top context.
     *
     * @throws IllegalStateException if only {@value InputBindings#GAMEPLAY} is left
     */
    void popContext();

    /** The context on top of the stack. */
    String topContext();

    /** True if the bindings define an action or axis with this name. */
    boolean hasAction(String name);

    /** Sets the capture hook asked once per frame; {@code null} means nothing captures. */
    void setCapture(InputCapture capture);

    /**
     * Treats every held input as released; the {@link #justReleased} edges appear in the next frame. The engine calls
     * this when the app is suspended and (desktop) when the window loses focus.
     */
    void releaseAll();
}
