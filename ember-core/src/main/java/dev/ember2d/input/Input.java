package dev.ember2d.input;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.PublicApi;

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
