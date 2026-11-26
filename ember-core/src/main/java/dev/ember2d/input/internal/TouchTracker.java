package dev.ember2d.input.internal;

import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.input.TouchThresholds;

/**
 * Gesture recognition for up to {@link GdxInputSource#MAX_POINTERS} fingers (F-022). Each finger is recognised on its
 * own. Held inputs ({@code TOUCH}, {@code LONG_PRESS}, {@code DRAG}) are counted over fingers and reported when the
 * count changes between 0 and 1; momentary inputs ({@code TAP}, {@code DOUBLE_TAP}, {@code SWIPE_*}) are reported as a
 * press immediately followed by a release. Times are real seconds; distances are window pixels.
 */
final class TouchTracker {

    /** Receives input changes as physical codes. */
    interface Sink {
        void input(int code, boolean down);
    }

    private static final int N = GdxInputSource.MAX_POINTERS;

    private final boolean[] down = new boolean[N];
    private final boolean[] ignored = new boolean[N];   // started while captured, or released by releaseAll
    private final boolean[] justDown = new boolean[N];
    private final boolean[] justUp = new boolean[N];
    private final boolean[] moved = new boolean[N];
    private final boolean[] longPress = new boolean[N];
    private final int[] x = new int[N];
    private final int[] y = new int[N];
    private final int[] previousX = new int[N];
    private final int[] previousY = new int[N];
    private final int[] startX = new int[N];
    private final int[] startY = new int[N];
    private final double[] startTime = new double[N];
    private int touching;
    private int longPressing;
    private int dragging;
    private double lastTapTime = Double.NEGATIVE_INFINITY;
    private int lastTapX;
    private int lastTapY;

    private TouchThresholds thresholds = TouchThresholds.DEFAULTS;
    private float density = 1f;

    void configure(TouchThresholds thresholds, float density) {
        this.thresholds = thresholds;
        this.density = density > 0f ? density : 1f;
    }

    /** Applies one frame of finger events at real time {@code now}, then starts long presses that are due. */
    void process(IntArray touches, double now, boolean captured, Sink sink) {
        for (int p = 0; p < N; p++) {
            justDown[p] = false;
            justUp[p] = false;
            previousX[p] = x[p];
            previousY[p] = y[p];
        }
        for (int i = 0; i + 3 < touches.size; i += 4) {
            int type = touches.items[i];
            int p = touches.items[i + 1];
            int px = touches.items[i + 2];
            int py = touches.items[i + 3];
            switch (type) {
                case GdxInputSource.TOUCH_DOWN -> down(p, px, py, now, captured, sink);
                case GdxInputSource.TOUCH_DRAG -> drag(p, px, py, sink);
                default -> up(p, px, py, now, sink);
            }
        }
        touches.clear();
        for (int p = 0; p < N; p++) {
            if (down[p] && !moved[p] && !longPress[p] && now - startTime[p] >= thresholds.longPressTime()) {
                longPress[p] = true;
                if (longPressing++ == 0) {
                    sink.input(InputNames.LONG_PRESS, true);
                }
            }
        }
    }

    /** Ends every touch without gestures; later events of those fingers are ignored until they touch again. */
    void releaseAll() {
        for (int p = 0; p < N; p++) {
            if (down[p]) {
                ignored[p] = true;
            }
            down[p] = false;
            moved[p] = false;
            longPress[p] = false;
        }
        touching = 0;
        longPressing = 0;
        dragging = 0;
    }

    boolean down(int p) {
        return down[p];
    }

    boolean justDown(int p) {
        return justDown[p];
    }

    boolean justUp(int p) {
        return justUp[p];
    }

    int x(int p) {
        return x[p];
    }

    int y(int p) {
        return y[p];
    }

    int previousX(int p) {
        return previousX[p];
    }

    int previousY(int p) {
        return previousY[p];
    }

    /** The lowest pointer that is dragging, or -1. */
    int dragPointer() {
        for (int p = 0; p < N; p++) {
            if (down[p] && moved[p]) {
                return p;
            }
        }
        return -1;
    }

    private void down(int p, int px, int py, double now, boolean captured, Sink sink) {
        if (down[p]) {
            return;
        }
        if (captured) {
            ignored[p] = true;
            return;
        }
        ignored[p] = false;
        down[p] = true;
        justDown[p] = true;
        moved[p] = false;
        longPress[p] = false;
        x[p] = px;
        y[p] = py;
        previousX[p] = px;
        previousY[p] = py;
        startX[p] = px;
        startY[p] = py;
        startTime[p] = now;
        if (touching++ == 0) {
            sink.input(InputNames.TOUCH, true);
        }
    }

    private void drag(int p, int px, int py, Sink sink) {
        if (!down[p]) {
            return;
        }
        x[p] = px;
        y[p] = py;
        checkMoved(p, sink);
    }

    private void up(int p, int px, int py, double now, Sink sink) {
        if (!down[p]) {
            ignored[p] = false;
            return;
        }
        x[p] = px;
        y[p] = py;
        checkMoved(p, sink);
        down[p] = false;
        justUp[p] = true;
        double elapsed = now - startTime[p];
        float dx = px - startX[p];
        float dy = py - startY[p];
        if (moved[p]) {
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance >= thresholds.swipeDistance() * density && elapsed <= thresholds.swipeTime()) {
                int code = Math.abs(dx) >= Math.abs(dy) ? (dx > 0 ? InputNames.SWIPE_RIGHT : InputNames.SWIPE_LEFT)
                        : (dy > 0 ? InputNames.SWIPE_DOWN : InputNames.SWIPE_UP);   // screen y grows downwards
                momentary(code, sink);
            }
            if (--dragging == 0) {
                sink.input(InputNames.DRAG, false);
            }
        } else if (!longPress[p] && elapsed <= thresholds.tapTime()) {
            momentary(InputNames.TAP, sink);
            float slop = thresholds.tapSlop() * density;
            float tx = px - lastTapX;
            float ty = py - lastTapY;
            if (now - lastTapTime <= thresholds.doubleTapTime() && tx * tx + ty * ty <= slop * slop) {
                momentary(InputNames.DOUBLE_TAP, sink);
                lastTapTime = Double.NEGATIVE_INFINITY;   // a third tap starts a new pair
            } else {
                lastTapTime = now;
                lastTapX = px;
                lastTapY = py;
            }
        }
        if (longPress[p]) {
            longPress[p] = false;
            if (--longPressing == 0) {
                sink.input(InputNames.LONG_PRESS, false);
            }
        }
        moved[p] = false;
        if (--touching == 0) {
            sink.input(InputNames.TOUCH, false);
        }
    }

    private void checkMoved(int p, Sink sink) {
        if (moved[p]) {
            return;
        }
        float slop = thresholds.tapSlop() * density;
        float dx = x[p] - startX[p];
        float dy = y[p] - startY[p];
        if (dx * dx + dy * dy > slop * slop) {
            moved[p] = true;
            if (dragging++ == 0) {
                sink.input(InputNames.DRAG, true);
            }
        }
    }

    private static void momentary(int code, Sink sink) {
        sink.input(code, true);
        sink.input(code, false);
    }
}
