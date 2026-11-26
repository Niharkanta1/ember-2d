package dev.ember2d.input.internal;

import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.input.TouchControlsConfig;
import dev.ember2d.input.TouchLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * On-screen controls (F-022, I-122, I-225): a joystick or a D-pad, and round action buttons. Runs before gesture
 * recognition: a touch that starts on a control is claimed until it lifts, and its events are removed from the event
 * list, so it never becomes a gesture. Positions here are window pixels with y up (libGDX reports y down; converted on
 * entry), inside the safe area. Buttons report presses as the virtual input codes
 * {@link InputNames#VIRTUAL_BASE}{@code + index}.
 *
 * <p>A control works and is drawn only while it is <em>live</em> (its action's or axis's context is on top), visible
 * in the {@link TouchLayout}, and (buttons) not hidden by the game. The whole set can be hidden by auto-hide after
 * keyboard use. While the layout editor runs ({@code editing}), controls are drawn but claim no touches.
 */
public final class TouchControls {

    /** Names of the stick controls in a {@link TouchLayout}. */
    public static final String JOYSTICK = "joystick";
    public static final String DPAD = "dpad";
    /** Fraction of the D-pad radius that reads as no direction. */
    static final float DPAD_DEAD = 0.25f;
    private static final float DIAGONAL = (float) Math.sqrt(0.5);

    private TouchControlsConfig config;
    private TouchLayout layout = TouchLayout.defaults();
    private boolean visible;
    private boolean autoHidden;
    private boolean editing;
    private float density = 1f;
    private float screenWidth = 1f;
    private float screenHeight = 1f;
    private float insetLeft;
    private float insetTop;
    private float insetRight;
    private float insetBottom;

    private int stickPointer = -1;
    private boolean stickIsDpad;
    private float originX;
    private float originY;
    private float knobX;
    private float knobY;
    private float valueX;
    private float valueY;
    private boolean stickLive = true;

    private final int[] buttonPointer = new int[TouchControlsConfig.MAX_BUTTONS];
    private final boolean[] pressed = new boolean[TouchControlsConfig.MAX_BUTTONS];
    private final boolean[] buttonLive = new boolean[TouchControlsConfig.MAX_BUTTONS];
    private final boolean[] buttonHidden = new boolean[TouchControlsConfig.MAX_BUTTONS];

    TouchControls() {
        Arrays.fill(buttonPointer, -1);
        Arrays.fill(buttonLive, true);
    }

    void configure(TouchControlsConfig config, float density) {
        this.config = config;
        this.density = density > 0f ? density : 1f;
        Arrays.fill(buttonHidden, false);
        reset();
    }

    void setScreen(float width, float height) {
        screenWidth = Math.max(1f, width);
        screenHeight = Math.max(1f, height);
    }

    /** Safe-area insets in window pixels (I-225). */
    void setInsets(float left, float top, float right, float bottom) {
        insetLeft = Math.max(0f, left);
        insetTop = Math.max(0f, top);
        insetRight = Math.max(0f, right);
        insetBottom = Math.max(0f, bottom);
    }

    void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible) {
            reset();
        }
    }

    void setAutoHidden(boolean hidden) {
        this.autoHidden = hidden;
        if (hidden) {
            reset();
        }
    }

    boolean autoHidden() {
        return autoHidden;
    }

    void setEditing(boolean editing) {
        this.editing = editing;
        reset();
    }

    public boolean editing() {
        return editing;
    }

    void setLayout(TouchLayout layout) {
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    TouchLayout layout() {
        return layout;
    }

    /** Hides or shows a button from game code; returns false for an unknown action. */
    boolean setButtonHidden(String action, boolean hidden) {
        int i = buttonIndex(action);
        if (i < 0) {
            return false;
        }
        buttonHidden[i] = hidden;
        return true;
    }

    /**
     * The controls whose context is on top; a control that stops being live is let go of (buttons report the
     * release through the sink).
     */
    void setLive(boolean stick, boolean[] buttons, TouchTracker.Sink sink) {
        if (!stick && stickLive && stickPointer >= 0) {
            releaseStick();
        }
        stickLive = stick;
        for (int b = 0; b < buttonCount(); b++) {
            if (!buttons[b] && buttonLive[b] && pressed[b]) {
                releaseButton(b, sink);
            }
            buttonLive[b] = buttons[b];
        }
    }

    /** True while the controls are configured, shown and not auto-hidden. */
    public boolean active() {
        return visible && config != null && !autoHidden;
    }

    public TouchControlsConfig config() {
        return config;
    }

    public float opacity() {
        return config == null ? 0f : layout.opacity(config.opacity());
    }

    // ---- drawing and editing data (window pixels, y up) ----

    public boolean hasStick() {
        return config != null && (config.joystick() != null || config.dpad() != null);
    }

    public boolean isDpad() {
        return config != null && config.dpad() != null;
    }

    /** The layout name of the stick control. */
    public String stickName() {
        return isDpad() ? DPAD : JOYSTICK;
    }

    /** Whether the stick (joystick or D-pad) is drawn now. */
    public boolean stickShown() {
        return hasStick() && layout.visibleOf(stickName()) && (editing || stickLive);
    }

    public boolean stickHeld() {
        return stickPointer >= 0;
    }

    private float stickDefaultX() {
        return isDpad() ? config.dpad().x() : config.joystick().x();
    }

    private float stickDefaultY() {
        return isDpad() ? config.dpad().y() : config.joystick().y();
    }

    /** The stick's resting centre: its default (or moved) position inside the safe area. */
    public float stickHomeX() {
        TouchLayout.Entry e = layout.get(stickName());
        return insetLeft + (e != null && e.moved() ? e.x() : stickDefaultX()) * density;
    }

    public float stickHomeY() {
        TouchLayout.Entry e = layout.get(stickName());
        return insetBottom + (e != null && e.moved() ? e.y() : stickDefaultY()) * density;
    }

    /** Where the stick base is drawn: its touch origin while a floating joystick is held, else its home. */
    public float baseX() {
        return stickHeld() && !isDpad() ? originX : stickHomeX();
    }

    public float baseY() {
        return stickHeld() && !isDpad() ? originY : stickHomeY();
    }

    public float knobX() {
        return stickHeld() && !isDpad() ? knobX : baseX();
    }

    public float knobY() {
        return stickHeld() && !isDpad() ? knobY : baseY();
    }

    public float stickRadius() {
        float r = isDpad() ? config.dpad().radius() : config.joystick().radius();
        return r * density * layout.scaleOf(stickName());
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

    public String buttonName(int i) {
        return config.buttons().get(i).action();
    }

    /** Whether button {@code i} is drawn now. */
    public boolean buttonShown(int i) {
        return !buttonHidden[i] && layout.visibleOf(buttonName(i)) && (editing || buttonLive[i]);
    }

    public float buttonX(int i) {
        TouchLayout.Entry e = layout.get(buttonName(i));
        float x = e != null && e.moved() ? e.x() : config.buttons().get(i).x();
        return screenWidth - insetRight - x * density;
    }

    public float buttonY(int i) {
        TouchLayout.Entry e = layout.get(buttonName(i));
        float y = e != null && e.moved() ? e.y() : config.buttons().get(i).y();
        return y >= 0f ? insetBottom + y * density : screenHeight - insetTop + y * density;
    }

    public float buttonRadius(int i) {
        return config.buttons().get(i).radius() * density * layout.scaleOf(buttonName(i));
    }

    public boolean pressed(int i) {
        return pressed[i];
    }

    public float density() {
        return density;
    }

    public float screenWidth() {
        return screenWidth;
    }

    public float screenHeight() {
        return screenHeight;
    }

    public float insetLeft() {
        return insetLeft;
    }

    public float insetTop() {
        return insetTop;
    }

    public float insetRight() {
        return insetRight;
    }

    public float insetBottom() {
        return insetBottom;
    }

    // ---- per frame ----

    /** Handles the frame's touch events: claims new touches on controls and removes all claimed events. */
    void process(IntArray touches, boolean captured, TouchTracker.Sink sink) {
        if (!active() || editing) {
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
        Arrays.fill(buttonPointer, -1);
        Arrays.fill(pressed, false);
    }

    private boolean down(int p, float x, float y, TouchTracker.Sink sink) {
        List<TouchControlsConfig.Button> buttons = config.buttons();
        for (int b = 0; b < buttons.size(); b++) {
            if (!buttonShown(b)) {
                continue;
            }
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
        if (!stickShown() || stickPointer >= 0) {
            return false;
        }
        if (isDpad()) {
            float dx = x - stickHomeX();
            float dy = y - stickHomeY();
            float reach = stickRadius() * 1.2f;
            if (dx * dx + dy * dy > reach * reach) {
                return false;
            }
            stickIsDpad = true;
            originX = stickHomeX();
            originY = stickHomeY();
        } else if (config.joystick().floating()) {
            if (x >= insetLeft + (screenWidth - insetLeft - insetRight) / 2f || x < insetLeft) {
                return false;
            }
            stickIsDpad = false;
            originX = x;
            originY = y;
        } else {
            float dx = x - stickHomeX();
            float dy = y - stickHomeY();
            float reach = stickRadius() * 1.5f;
            if (dx * dx + dy * dy > reach * reach) {
                return false;
            }
            stickIsDpad = false;
            originX = stickHomeX();
            originY = stickHomeY();
        }
        stickPointer = p;
        drag(p, x, y);
        return true;
    }

    private boolean drag(int p, float x, float y) {
        if (p == stickPointer) {
            if (stickIsDpad) {
                dpad(x - originX, y - originY);
            } else {
                stick(x - originX, y - originY);
            }
            return true;
        }
        return owns(p);
    }

    private void stick(float dx, float dy) {
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
    }

    /** Digital directions: 4 sectors of 90°, or 8 of 45° with diagonals. */
    private void dpad(float dx, float dy) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= stickRadius() * DPAD_DEAD) {
            valueX = 0f;
            valueY = 0f;
            return;
        }
        double angle = Math.atan2(dy, dx);
        if (config.dpad().diagonals()) {
            int sector = (int) Math.floorMod(Math.round(angle / (Math.PI / 4)), 8L);
            int sx = switch (sector) {
                case 0, 1, 7 -> 1;
                case 3, 4, 5 -> -1;
                default -> 0;
            };
            int sy = switch (sector) {
                case 1, 2, 3 -> 1;
                case 5, 6, 7 -> -1;
                default -> 0;
            };
            float n = sx != 0 && sy != 0 ? DIAGONAL : 1f;
            valueX = sx * n;
            valueY = sy * n;
        } else if (Math.abs(dx) >= Math.abs(dy)) {
            valueX = Math.signum(dx);
            valueY = 0f;
        } else {
            valueX = 0f;
            valueY = Math.signum(dy);
        }
    }

    private boolean up(int p, TouchTracker.Sink sink) {
        if (p == stickPointer) {
            releaseStick();
            return true;
        }
        boolean claimed = false;
        for (int b = 0; b < buttonPointer.length; b++) {
            if (buttonPointer[b] == p) {
                releaseButton(b, sink);
                claimed = true;
            }
        }
        return claimed;
    }

    private void releaseStick() {
        stickPointer = -1;
        valueX = 0f;
        valueY = 0f;
    }

    private void releaseButton(int b, TouchTracker.Sink sink) {
        buttonPointer[b] = -1;
        if (pressed[b]) {
            pressed[b] = false;
            sink.input(InputNames.VIRTUAL_BASE + b, false);
        }
    }

    private int buttonIndex(String action) {
        for (int i = 0; i < buttonCount(); i++) {
            if (buttonName(i).equals(action)) {
                return i;
            }
        }
        return -1;
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
