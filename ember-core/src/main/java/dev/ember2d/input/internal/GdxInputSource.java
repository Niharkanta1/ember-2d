package dev.ember2d.input.internal;

import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.IntArray;

/**
 * The libGDX input processor of the engine (F-005, F-022). Records, in arrival order and in reused arrays:
 * <ul>
 *   <li>key and mouse-button changes as {@code (code << 1) | down};</li>
 *   <li>finger events for pointers {@code 0..MAX_POINTERS-1} as {@code type, pointer, x, y} (only the primary button;
 *       on desktop that is the left mouse button acting as pointer 0);</li>
 *   <li>the latest mouse position.</li>
 * </ul>
 * {@link DefaultInput} applies the events once per frame. Returns false from every callback so other processors still
 * see the events. Tests feed events through the same methods ({@code keyDown(Keys.SPACE)}).
 */
public final class GdxInputSource extends InputAdapter {

    /** Pointers tracked for touch. */
    public static final int MAX_POINTERS = 5;
    static final int TOUCH_DOWN = 0;
    static final int TOUCH_DRAG = 1;
    static final int TOUCH_UP = 2;

    private final IntArray events = new IntArray(32);
    private final IntArray touches = new IntArray(64);
    private boolean mouseButtons = true;
    private int pointerX;
    private int pointerY;

    /**
     * Whether pointer 0 presses drive the {@code MOUSE_*} bindings. True on desktop; false on Android, where libGDX
     * reports every finger as button 0.
     */
    public void setMouseButtons(boolean mouseButtons) {
        this.mouseButtons = mouseButtons;
    }

    @Override
    public boolean keyDown(int keycode) {
        record(keycode, true);
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        record(keycode, false);
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer == 0) {
            moved(screenX, screenY);
            if (mouseButtons) {
                record(InputNames.MOUSE_BASE + button, true);
            }
        }
        if (button == Buttons.LEFT) {
            touch(TOUCH_DOWN, pointer, screenX, screenY);
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pointer == 0) {
            moved(screenX, screenY);
            if (mouseButtons) {
                record(InputNames.MOUSE_BASE + button, false);
            }
        }
        if (button == Buttons.LEFT) {
            touch(TOUCH_UP, pointer, screenX, screenY);
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (pointer == 0) {
            moved(screenX, screenY);
        }
        touch(TOUCH_DRAG, pointer, screenX, screenY);
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        moved(screenX, screenY);
        return false;
    }

    int pointerX() {
        return pointerX;
    }

    int pointerY() {
        return pointerY;
    }

    IntArray events() {
        return events;
    }

    IntArray touches() {
        return touches;
    }

    private void moved(int x, int y) {
        pointerX = x;
        pointerY = y;
    }

    private void record(int code, boolean down) {
        if (code >= 0 && code < InputNames.TOUCH_BASE) {
            events.add((code << 1) | (down ? 1 : 0));
        }
    }

    private void touch(int type, int pointer, int x, int y) {
        if (pointer >= 0 && pointer < MAX_POINTERS) {
            touches.add(type, pointer, x, y);
        }
    }
}
