package dev.ember2d.input.internal;

/**
 * Reads the current gamepad once per frame (F-005, I-530). {@link DefaultInput} uses {@link GdxGamepads}; tests
 * inject their own.
 */
public interface GamepadSource {

    /**
     * Writes every pad input's value (0..1, in the order of {@link InputNames#PAD_NAMES}) into {@code values}, whose
     * length is {@link InputNames#PAD_COUNT}. Stick directions are raw (no dead zone); buttons are 0 or 1.
     *
     * @return the connected pad's name, or null when no pad is connected (then {@code values} is left as zeros)
     */
    String poll(float[] values);
}
