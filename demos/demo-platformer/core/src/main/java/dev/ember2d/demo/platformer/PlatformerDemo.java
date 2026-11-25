package dev.ember2d.demo.platformer;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.utils.ScreenUtils;
import dev.ember2d.core.Log;

/**
 * Platformer demo entry point. Phase 0: clears the screen only, to prove the build and launcher wiring.
 */
public class PlatformerDemo extends ApplicationAdapter {

    private static final Log LOG = Log.get("demo");

    @Override
    public void create() {
        LOG.info("Created");
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.08f, 0.06f, 0.10f, 1f);
    }

    @Override
    public void dispose() {
        LOG.info("Disposed");
    }
}
