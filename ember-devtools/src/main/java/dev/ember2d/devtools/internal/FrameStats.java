package dev.ember2d.devtools.internal;

/** Recent frame times for the Frame panel (F-018, I-160): a ring buffer of milliseconds. Render thread only. */
public final class FrameStats {

    private final float[] frameMs;
    private final float[] engineMs;
    private final float[] ordered;
    private int next;
    private int size;

    public FrameStats(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 (was " + capacity + ")");
        }
        frameMs = new float[capacity];
        engineMs = new float[capacity];
        ordered = new float[capacity];
    }

    /** One frame: its length and the engine's share of it, in milliseconds. */
    public void add(float frame, float engine) {
        frameMs[next] = frame;
        engineMs[next] = engine;
        next = (next + 1) % frameMs.length;
        size = Math.min(size + 1, frameMs.length);
    }

    public int size() {
        return size;
    }

    /** Frame times, oldest first, in a reused array; only the first {@link #size()} values are valid. */
    public float[] frameTimes() {
        for (int i = 0; i < size; i++) {
            ordered[i] = frameMs[Math.floorMod(next - size + i, frameMs.length)];
        }
        return ordered;
    }

    public float averageFrame() {
        return average(frameMs);
    }

    public float averageEngine() {
        return average(engineMs);
    }

    public float maxFrame() {
        float max = 0f;
        for (int i = 0; i < size; i++) {
            max = Math.max(max, frameMs[i]);
        }
        return max;
    }

    private float average(float[] values) {
        if (size == 0) {
            return 0f;
        }
        float sum = 0f;
        for (int i = 0; i < size; i++) {
            sum += values[i];
        }
        return sum / size;
    }
}
