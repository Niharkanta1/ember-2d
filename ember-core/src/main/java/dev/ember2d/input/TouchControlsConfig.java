package dev.ember2d.input;

import dev.ember2d.core.PublicApi;
import java.util.List;

/**
 * Basic on-screen controls (F-022, I-122), from the optional {@code touchControls} section of the bindings file. Sizes
 * and positions are in density-independent pixels (dp).
 *
 * @param opacity alpha of the drawn controls, 0-1 (pressed parts are drawn brighter)
 * @param joystick the joystick, or null for none
 * @param dpad the D-pad (I-225), or null for none; a game has a joystick or a D-pad, not both
 * @param buttons the action buttons (at most {@value #MAX_BUTTONS})
 * @param aimStick a second stick on the right half for twin-stick aiming (I-320), or null for none
 */
@PublicApi
public record TouchControlsConfig(float opacity, Joystick joystick, Dpad dpad, List<Button> buttons, AimStick aimStick) {

    /** Most buttons per game. */
    public static final int MAX_BUTTONS = 8;

    public TouchControlsConfig {
        buttons = List.copyOf(buttons);
    }

    public TouchControlsConfig(float opacity, Joystick joystick, List<Button> buttons) {
        this(opacity, joystick, null, buttons, null);
    }

    public TouchControlsConfig(float opacity, Joystick joystick, Dpad dpad, List<Button> buttons) {
        this(opacity, joystick, dpad, buttons, null);
    }

    /**
     * A floating stick on the right half of the screen for twin-stick aiming (I-320): it appears where the thumb lands
     * (outside the buttons) and drives a 2D axis; pushed past {@code fireAt} it also holds an action.
     *
     * @param axis the 2D axis it drives (for example {@code aim})
     * @param x idle centre in dp from the right edge (where the faint base is drawn)
     * @param y idle centre in dp from the bottom edge
     * @param radius how far the knob travels, in dp
     * @param deadZone fraction of the radius that reads as 0
     * @param fireAction the action held while pushed past {@code fireAt}, or null for none
     * @param fireAt fraction of the radius, 0-1
     */
    public record AimStick(String axis, float x, float y, float radius, float deadZone, String fireAction,
            float fireAt) {
    }

    /**
     * A D-pad driving a 2D axis with digital directions (I-225).
     *
     * @param axis the 2D axis it drives
     * @param x centre in dp from the left edge
     * @param y centre in dp from the bottom edge
     * @param radius in dp
     * @param diagonals true: eight directions (diagonals normalised); false: four
     */
    public record Dpad(String axis, float x, float y, float radius, boolean diagonals) {
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
