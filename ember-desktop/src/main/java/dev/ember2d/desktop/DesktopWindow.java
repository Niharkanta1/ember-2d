package dev.ember2d.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
import dev.ember2d.app.AppState;
import dev.ember2d.app.EmberGame;
import dev.ember2d.core.PublicApi;
import java.util.Objects;

/**
 * Desktop window handling for an {@link EmberGame} (F-005). Install with
 * {@code configuration.setWindowListener(DesktopWindow.focusListener(game))}.
 */
@PublicApi
public final class DesktopWindow {

    private DesktopWindow() {
    }

    /**
     * A window listener that releases every held input when the window loses focus, so a key held while switching
     * windows does not stay pressed (its key-up goes to the other window). The game is not paused.
     */
    public static Lwjgl3WindowListener focusListener(EmberGame game) {
        Objects.requireNonNull(game, "game");
        return new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                if (game.state() == AppState.RUNNING || game.state() == AppState.SUSPENDED) {
                    game.context().input().releaseAll();
                }
            }
        };
    }
}
