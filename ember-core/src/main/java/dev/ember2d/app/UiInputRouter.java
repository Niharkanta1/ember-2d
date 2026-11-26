package dev.ember2d.app;

import com.badlogic.gdx.InputProcessor;
import dev.ember2d.ui.Ui;

/** Passes input to the current scene's UI first (F-012); unhandled events go on to the game's input. */
final class UiInputRouter implements InputProcessor {

    private final SceneManager scenes;

    UiInputRouter(SceneManager scenes) {
        this.scenes = scenes;
    }

    private InputProcessor target() {
        Ui ui = scenes.currentUi();
        return ui == null ? null : ui.inputProcessor();
    }

    @Override
    public boolean keyDown(int keycode) {
        InputProcessor t = target();
        return t != null && t.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        InputProcessor t = target();
        return t != null && t.keyUp(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        InputProcessor t = target();
        return t != null && t.keyTyped(character);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        InputProcessor t = target();
        return t != null && t.touchDown(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        InputProcessor t = target();
        return t != null && t.touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        InputProcessor t = target();
        return t != null && t.touchCancelled(screenX, screenY, pointer, button);
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        InputProcessor t = target();
        return t != null && t.touchDragged(screenX, screenY, pointer);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        InputProcessor t = target();
        return t != null && t.mouseMoved(screenX, screenY);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        InputProcessor t = target();
        return t != null && t.scrolled(amountX, amountY);
    }
}
