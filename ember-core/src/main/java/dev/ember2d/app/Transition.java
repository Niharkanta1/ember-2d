package dev.ember2d.app;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;

/**
 * How {@link SceneManager} switches scenes (F-010, I-240). A fade covers the screen with a colour over the first half
 * of its duration, switches scenes at full cover once the incoming scene's asset group has loaded (showing the loading
 * screen if that takes a while), and uncovers over the second half. {@link #cut()} is a fade of 0 seconds. Durations
 * are real (unscaled) time.
 */
@PublicApi
public final class Transition {

    private static final Transition CUT = new Transition(0f, Color.BLACK);

    private final float seconds;
    private final Color color;

    private Transition(float seconds, Color color) {
        this.seconds = seconds;
        this.color = new Color(color);
    }

    /** Switches at the next frame; the incoming group loads in the background behind the loading screen. */
    public static Transition cut() {
        return CUT;
    }

    /** A fade through black. */
    public static Transition fade(float seconds) {
        return fade(seconds, Color.BLACK);
    }

    /**
     * A fade through a colour (copied).
     *
     * @throws IllegalArgumentException for a negative or non-finite duration
     */
    public static Transition fade(float seconds, Color color) {
        if (!(seconds >= 0f) || Float.isInfinite(seconds)) {
            throw new IllegalArgumentException("A transition needs a finite duration >= 0 (was " + seconds + ")");
        }
        if (color == null) {
            throw new IllegalArgumentException("color is null");
        }
        return new Transition(seconds, color);
    }

    /** The whole duration: half covering, half uncovering. */
    public float seconds() {
        return seconds;
    }

    /** A copy of the cover colour. */
    public Color color() {
        return new Color(color);
    }

    Color colorInternal() {
        return color;
    }

    @Override
    public String toString() {
        return seconds == 0f ? "cut" : "fade(" + seconds + "s, " + color + ")";
    }
}
