package dev.ember2d.input.internal;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.IntArray;

/**
 * The libGDX input processor of the engine (F-005). Records key and mouse-button changes in arrival order as
 * {@code (code << 1) | down} in a reused array, and the latest pointer position. {@link DefaultInput} applies the
 * events once per frame. Returns false from every callback so other processors still see the events.
 *
 * <p>Tests feed events through the same methods ({@code keyDown(Keys.SPACE)}).
 */
public final class GdxInputSource extends InputAdapter {

    private final IntArray events = new IntArray(32);
    private int pointerX;
    private int pointerY;

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
        moved(screenX, screenY);
        if (pointer == 0) {
            record(InputNames.MOUSE_BASE + button, true);
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        moved(screenX, screenY);
        if (pointer == 0) {
            record(InputNames.MOUSE_BASE + button, false);
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

    private void moved(int x, int y) {
        pointerX = x;
        pointerY = y;
    }

    private void record(int code, boolean down) {
        if (code >= 0 && code < InputNames.COUNT) {
            events.add((code << 1) | (down ? 1 : 0));
        }
    }
}
