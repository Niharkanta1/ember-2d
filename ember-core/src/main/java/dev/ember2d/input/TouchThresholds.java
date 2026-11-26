package dev.ember2d.input;

import dev.ember2d.core.PublicApi;

/**
 * Gesture thresholds (F-022), from the optional {@code touch} section of the bindings file. Distances are in
 * density-independent pixels (dp), times in seconds of real (unscaled) time.
 *
 * @param tapSlop how far a finger may move and still tap or long-press
 * @param tapTime longest touch that counts as a tap
 * @param doubleTapTime longest gap between the two taps of a double tap
 * @param longPressTime how long a finger must stay still for a long press
 * @param swipeDistance shortest movement that counts as a swipe
 * @param swipeTime longest touch that counts as a swipe
 */
@PublicApi
public record TouchThresholds(float tapSlop, float tapTime, float doubleTapTime, float longPressTime,
        float swipeDistance, float swipeTime) {

    /** The defaults: 10 dp, 0.3 s, 0.3 s, 0.5 s, 48 dp, 0.3 s. */
    public static final TouchThresholds DEFAULTS = new TouchThresholds(10f, 0.3f, 0.3f, 0.5f, 48f, 0.3f);
}
