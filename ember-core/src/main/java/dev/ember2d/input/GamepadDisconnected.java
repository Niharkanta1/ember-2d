package dev.ember2d.input;

import dev.ember2d.core.PublicApi;

/** The current gamepad went away; every pad input was released (F-005, I-530). Queued at the start of the frame. */
@PublicApi
public record GamepadDisconnected(String name) {
}
