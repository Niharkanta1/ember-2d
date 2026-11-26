package dev.ember2d.input;

import dev.ember2d.core.PublicApi;

/**
 * Lets an in-game UI (dev tools, menus) take the keyboard or the pointer away from actions (F-005). While a capture
 * reports true, new presses of that kind are ignored; releases are always applied, so nothing stays held. Asked once
 * per frame.
 */
@PublicApi
public interface InputCapture {

    /** True while key presses must not reach actions. */
    boolean capturesKeyboard();

    /** True while mouse-button presses must not reach actions. */
    boolean capturesPointer();
}
