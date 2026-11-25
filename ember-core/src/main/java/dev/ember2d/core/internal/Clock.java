package dev.ember2d.core.internal;

/** Source of frame durations for the {@link GameLoop}; replaced by a fake clock in tests. */
public interface Clock {

    /** Duration of the frame that just ended, in nanoseconds. */
    long frameDeltaNanos();
}
