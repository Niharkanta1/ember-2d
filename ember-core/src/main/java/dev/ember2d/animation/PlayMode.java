package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;

/** How a clip plays (F-007). */
@PublicApi
public enum PlayMode {
    /** From the first frame to the last, then again. */
    LOOP,
    /** Once; stays on the last frame and reports finished. */
    ONCE,
    /** Forward, then backward, without repeating the end frames. */
    PINGPONG
}
