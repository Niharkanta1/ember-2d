package dev.ember2d.demo.platformer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.ScreenUtils;

/**
 * Platformer demo entry point. Phase 0: clears the screen only, to prove the build and launcher wiring.
 */
public class PlatformerDemo extends ApplicationAdapter {

    @Override
    public void render() {
        ScreenUtils.clear(0.08f, 0.06f, 0.10f, 1f);
    }
}
