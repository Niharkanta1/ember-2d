package dev.ember2d.kit.novel;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;

/**
 * Presents what {@link NovelDirector} stages (F-023); {@link NovelView} is the standard one. Every method has a do-
 * nothing default, so a game (or a test) overrides only what it needs. Region and asset IDs are already resolved.
 */
@PublicApi
public interface StageListener {

    /** @param transition {@code fade} or {@code cut} */
    default void background(String region, String transition, float seconds) {
    }

    /** A character appears or changes expression or position. @param transition {@code fade}, {@code slide} or {@code cut} */
    default void show(String character, String region, float x, String transition, float seconds) {
    }

    default void hide(String character, String transition, float seconds) {
    }

    default void music(String id, float fadeSeconds) {
    }

    default void stopMusic(float fadeSeconds) {
    }

    default void sound(String id) {
    }

    default void voice(String id) {
    }

    /** A full-screen CG (null hides it). */
    default void cg(String region) {
    }

    default void shake(float strength) {
    }

    default void flash(Color color, float seconds) {
    }

    /** The whole stage at once, after a load: no transitions. */
    default void restore(StageState stage, NovelCharacters characters) {
    }
}
