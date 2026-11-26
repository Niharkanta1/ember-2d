package dev.ember2d.app;

import dev.ember2d.core.PublicApi;

/** When the on-screen controls (F-022, I-122) are shown, if the bindings file configures them. */
@PublicApi
public enum TouchControlsMode {
    /** On touch devices (Android, iOS). */
    AUTO,
    /** Everywhere; on desktop the mouse is pointer 0, so the controls can be tried without a phone. */
    ALWAYS,
    /** Never. */
    NEVER
}
