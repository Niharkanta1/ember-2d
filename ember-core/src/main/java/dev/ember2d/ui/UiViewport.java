package dev.ember2d.ui;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.viewport.Viewport;

/** The UI's viewport: a world size in UI units shown in a screen rectangle set by the engine (F-012). */
final class UiViewport extends Viewport {

    UiViewport() {
        setCamera(new OrthographicCamera());
    }

    /** Returns true when something changed. */
    boolean set(float width, float height, int screenX, int screenY, int screenWidth, int screenHeight) {
        if (getWorldWidth() == width && getWorldHeight() == height && getScreenX() == screenX
                && getScreenY() == screenY && getScreenWidth() == screenWidth && getScreenHeight() == screenHeight) {
            return false;
        }
        setWorldSize(width, height);
        setScreenBounds(screenX, screenY, screenWidth, screenHeight);
        getCamera().viewportWidth = width;    // so pointer mapping is right before the first draw
        getCamera().viewportHeight = height;
        getCamera().position.set(width / 2f, height / 2f, 0f);
        getCamera().update();
        return true;
    }
}
