package dev.ember2d.input;

import dev.ember2d.core.PublicApi;

/** A gamepad became the current pad (F-005, I-530); queued at the start of the frame. */
@PublicApi
public record GamepadConnected(String name) {
}
