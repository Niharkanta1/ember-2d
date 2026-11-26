package dev.ember2d.input;

import dev.ember2d.core.PublicApi;
import java.util.List;

/**
 * Basic on-screen controls (F-022, I-122), from the optional {@code touchControls} section of the bindings file. Sizes
 * and positions are in density-independent pixels (dp).
 *
 * @param opacity alpha of the drawn controls, 0-1 (pressed parts are drawn brighter)
 * @param joystick the joystick, or null for none
 * @param buttons the action buttons (at most {@value #MAX_BUTTONS})
 */
@PublicApi
public record TouchControlsConfig(float opacity, Joystick joystick, List<Button> buttons) {

    /** Most buttons per game. */
    public static final int MAX_BUTTONS = 8;

    public TouchControlsConfig {
        buttons = List.copyOf(buttons);
    }

    /**
     * A joystick driving a 2D axis.
     *
     * @param axis the 2D axis it drives
     * @param floating true: appears where the thumb lands in the left half of the screen; false: fixed at (x, y)
     * @param x centre in dp from the left edge (the fixed position, or where the idle base is drawn)
     * @param y centre in dp from the bottom edge
     * @param radius how far the knob travels, in dp
     * @param deadZone fraction of the radius that reads as 0
     */
    public record Joystick(String axis, boolean floating, float x, float y, float radius, float deadZone) {
    }

    /**
     * A round button driving an action.
     *
     * @param action the action it presses
     * @param label short text drawn on it (may be empty)
     * @param x centre in dp from the right edge
     * @param y centre in dp from the bottom edge; negative: from the top edge
     * @param radius in dp
     */
    public record Button(String action, String label, float x, float y, float radius) {
    }
}
