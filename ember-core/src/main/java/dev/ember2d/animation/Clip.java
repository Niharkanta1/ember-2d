package dev.ember2d.animation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.core.PublicApi;

/** An immutable frame sequence with per-frame durations, a play mode and frame events (F-007). */
@PublicApi
public final class Clip {

    private final String name;
    private final TextureRegion[] frames;
    private final float[] durations;
    private final String[] events;
    private final PlayMode mode;
    private final float total;

    Clip(String name, TextureRegion[] frames, float[] durations, String[] events, PlayMode mode) {
        this.name = name;
        this.frames = frames;
        this.durations = durations;
        this.events = events;
        this.mode = mode;
        float sum = 0f;
        for (float d : durations) {
            sum += d;
        }
        this.total = sum;
    }

    public String name() {
        return name;
    }

    public int frameCount() {
        return frames.length;
    }

    public TextureRegion frame(int index) {
        return frames[index];
    }

    /** Seconds the frame is shown. */
    public float duration(int index) {
        return durations[index];
    }

    /** Seconds for one pass through all frames. */
    public float totalDuration() {
        return total;
    }

    public PlayMode mode() {
        return mode;
    }

    /** The event fired when the frame is entered, or null. */
    public String eventAt(int index) {
        return events[index];
    }
}
