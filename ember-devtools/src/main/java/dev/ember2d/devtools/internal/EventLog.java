package dev.ember2d.devtools.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The last events for the Events panel (F-018, I-160): a ring buffer, newest first. Render thread only. */
public final class EventLog {

    /** One logged event. */
    public record Entry(long frame, String type, String text) {
    }

    /** Longest kept {@code toString()}; longer text is cut. */
    static final int MAX_TEXT = 200;

    private final Entry[] ring;
    private int next;
    private int size;
    private boolean paused;

    public EventLog(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1 (was " + capacity + ")");
        }
        ring = new Entry[capacity];
    }

    public void record(long frame, Object event) {
        if (paused) {
            return;
        }
        String text = String.valueOf(event);
        if (text.length() > MAX_TEXT) {
            text = text.substring(0, MAX_TEXT) + "...";
        }
        ring[next] = new Entry(frame, event.getClass().getSimpleName(), text);
        next = (next + 1) % ring.length;
        size = Math.min(size + 1, ring.length);
    }

    /** Entries whose type contains {@code filter} (ignoring case; empty = all), newest first. */
    public List<Entry> entries(String filter) {
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Entry e = ring[Math.floorMod(next - 1 - i, ring.length)];
            if (f.isEmpty() || e.type().toLowerCase(Locale.ROOT).contains(f)) {
                out.add(e);
            }
        }
        return out;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void clear() {
        java.util.Arrays.fill(ring, null);
        next = 0;
        size = 0;
    }
}
