package dev.ember2d.input;

import com.badlogic.gdx.Input.Keys;
import dev.ember2d.core.PublicApi;
import dev.ember2d.input.internal.InputNames;

/** Where an input comes from (F-005, I-535): used to rebind one device's inputs and to listen for a new one. */
@PublicApi
public enum InputDevice {
    KEYBOARD,
    MOUSE,
    TOUCH,
    GAMEPAD;

    /** The device of a binding name ({@code SPACE}, {@code MOUSE_LEFT}, {@code TAP}, {@code PAD_A}), or null. */
    public static InputDevice of(String name) {
        int code = InputNames.code(name);
        if (code < 0) {
            return null;
        }
        if (InputNames.isPad(code)) {
            return GAMEPAD;
        }
        if (code <= Keys.MAX_KEYCODE) {
            return KEYBOARD;
        }
        return code < InputNames.TOUCH_BASE ? MOUSE : TOUCH;
    }
}
