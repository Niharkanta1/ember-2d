package dev.ember2d.input.internal;

import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.input.TouchControlsConfig;
import java.util.List;

/**
 * Basic on-screen controls (F-022, I-122): a joystick and round action buttons. Runs before gesture recognition: a
 * touch that starts on a control is claimed until it lifts, and its events are removed from the event list, so it
 * never becomes a gesture. Positions here are window pixels with y up (libGDX reports y down; converted on entry).
 * Buttons report presses as the virtual input codes {@link InputNames#VIRTUAL_BASE}{@code + index}.
 */
public final class TouchControls {

    private TouchControlsConfig config;
    private boolean visible;
    private float density = 1f;
    private float screenWidth = 1f;
    private float screenHeight = 1f;

    private int stickPointer = -1;
    private float originX;
    private float originY;
    private float knobX;
    private float knobY;
    private float valueX;
    private float valueY;

    private final int[] buttonPointer = new int[TouchControlsConfig.MAX_BUTTONS];
    private final boolean[] pressed = new boolean[TouchControlsConfig.MAX_BUTTONS];

    TouchControls() {
        java.util.Arrays.fill(buttonPointer, -1);
    }

    void configure(TouchControlsConfig config, float density) {
        this.config = config;
        this.density = density > 0f ? density : 1f;
        reset();
    }

    void setScreen(float width, float height) {
        screenWidth = Math.max(1f, width);
        screenHeight = Math.max(1f, height);
    }

    void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            reset();
        }
    }

    /** True while the controls are configured and shown. */
    public boolean active() {
        return visible && config != null;
    }

    public TouchControlsConfig config() {
        return config;
    }

    // ---- drawing data (window pixels, y up) ----

    public boolean stickHeld() {
        return stickPointer >= 0;
    }

    /** Where the stick base is drawn: its touch origin while held, its default position while idle. */
    public float baseX() {
        return stickHeld() ? originX : config.joystick().x() * density;
    }

    public float baseY() {
        return stickHeld() ? originY : config.joystick().y() * density;
    }

    public float knobX() {
        return stickHeld() ? knobX : baseX();
    }

    public float knobY() {
        return stickHeld() ? knobY : baseY();
    }

    public float stickRadius() {
        return config.joystick().radius() * density;
    }

    /** The stick value, x right and y up, length 0-1; zero while idle. */
    public float valueX() {
        return valueX;
    }

    public float valueY() {
        return valueY;
    }

    public int buttonCount() {
        return config == null ? 0 : config.buttons().size();
    }

    public float buttonX(int i) {
        return screenWidth - config.buttons().get(i).x() * density;
    }

    public float buttonY(int i) {
        float y = config.buttons().get(i).y();
        return y >= 0f ? y * density : screenHeight + y * density;
    }

    public float buttonRadius(int i) {
        return config.buttons().get(i).radius() * density;
    }

    public boolean pressed(int i) {
        return pressed[i];
    }

    public float density() {
        return density;
    }

    // ---- per frame ----

    /** Handles the frame's touch events: claims new touches on controls and removes all claimed events. */
    void process(IntArray touches, boolean captured, TouchTracker.Sink sink) {
        if (!active()) {
            return;
        }
        int write = 0;
        for (int i = 0; i + 3 < touches.size; i += 4) {
            int type = touches.items[i];
            int p = touches.items[i + 1];
            float x = touches.items[i + 2];
            float y = screenHeight - touches.items[i + 3];
            boolean claimed = switch (type) {
                case GdxInputSource.TOUCH_DOWN -> !captured && down(p, x, y, sink);
                case GdxInputSource.TOUCH_DRAG -> drag(p, x, y);
                default -> up(p, sink);
            };
            if (!claimed) {
                touches.items[write] = type;
                touches.items[write + 1] = p;
                touches.items[write + 2] = touches.items[i + 2];
                touches.items[write + 3] = touches.items[i + 3];
                write += 4;
            }
        }
        touches.size = write;
    }

    /** Lets go of every control without reporting (the caller releases the inputs). */
    void reset() {
        stickPointer = -1;
        valueX = 0f;
        valueY = 0f;
        java.util.Arrays.fill(buttonPointer, -1);
        java.util.Arrays.fill(pressed, false);
    }

    private boolean down(int p, float x, float y, TouchTracker.Sink sink) {
        List<TouchControlsConfig.Button> buttons = config.buttons();
        for (int b = 0; b < buttons.size(); b++) {
            float dx = x - buttonX(b);
            float dy = y - buttonY(b);
            float r = buttonRadius(b);
            if (dx * dx + dy * dy <= r * r) {
                buttonPointer[b] = p;
                if (!pressed[b]) {
                    pressed[b] = true;
                    sink.input(InputNames.VIRTUAL_BASE + b, true);
                }
                return true;
            }
        }
        TouchControlsConfig.Joystick j = config.joystick();
        if (j == null || stickPointer >= 0) {
            return false;
        }
        if (j.floating()) {
            if (x >= screenWidth / 2f) {
                return false;
            }
            originX = x;
            originY = y;
        } else {
            float dx = x - j.x() * density;
            float dy = y - j.y() * density;
            float reach = stickRadius() * 1.5f;
            if (dx * dx + dy * dy > reach * reach) {
                return false;
            }
            originX = j.x() * density;
            originY = j.y() * density;
        }
        stickPointer = p;
        drag(p, x, y);
        return true;
    }

    private boolean drag(int p, float x, float y) {
        if (p == stickPointer) {
            float dx = x - originX;
            float dy = y - originY;
            float r = stickRadius();
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len > r) {
                dx *= r / len;
                dy *= r / len;
                len = r;
            }
            knobX = originX + dx;
            knobY = originY + dy;
            float magnitude = len / r;
            float dead = config.joystick().deadZone();
            if (magnitude <= dead || len == 0f) {
                valueX = 0f;
                valueY = 0f;
            } else {
                float scaled = (magnitude - dead) / (1f - dead);
                valueX = dx / len * scaled;
                valueY = dy / len * scaled;
            }
            return true;
        }
        return owns(p);
    }

    private boolean up(int p, TouchTracker.Sink sink) {
        if (p == stickPointer) {
            stickPointer = -1;
            valueX = 0f;
            valueY = 0f;
            return true;
        }
        boolean claimed = false;
        for (int b = 0; b < buttonPointer.length; b++) {
            if (buttonPointer[b] == p) {
                buttonPointer[b] = -1;
                pressed[b] = false;
                sink.input(InputNames.VIRTUAL_BASE + b, false);
                claimed = true;
            }
        }
        return claimed;
    }

    private boolean owns(int p) {
        for (int b : buttonPointer) {
            if (b == p) {
                return true;
            }
        }
        return false;
    }
}
