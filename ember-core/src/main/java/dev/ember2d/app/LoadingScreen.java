package dev.ember2d.app;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.core.PublicApi;

/**
 * Drawn while a scene transition waits for the incoming scene's asset group (F-010, I-240), over the transition's
 * cover colour, after the load has taken 0.1 s. Set with {@code EngineConfig.loadingScreen}.
 */
@PublicApi
@FunctionalInterface
public interface LoadingScreen {

    /**
     * Draws one frame of the loading screen.
     *
     * @param batch begun, with a projection in window pixels (origin bottom left)
     * @param pixel a white 1x1 region owned by the engine, for bars and boxes
     * @param progress the group's progress, 0 to 1
     */
    void draw(Batch batch, TextureRegion pixel, float width, float height, float progress);

    /** The default: a grey bar filled in white, in the middle of the screen. */
    static LoadingScreen bar() {
        return (batch, pixel, width, height, progress) -> {
            float barWidth = Math.round(width * 0.4f);
            float barHeight = Math.max(4f, Math.round(height / 60f));
            float x = Math.round((width - barWidth) / 2f);
            float y = Math.round((height - barHeight) / 2f);
            float border = Math.max(1f, Math.round(barHeight / 4f));
            Color old = batch.getColor().cpy();
            batch.setColor(0.35f, 0.35f, 0.35f, 1f);
            batch.draw(pixel, x - border, y - border, barWidth + 2 * border, barHeight + 2 * border);
            batch.setColor(0.1f, 0.1f, 0.1f, 1f);
            batch.draw(pixel, x, y, barWidth, barHeight);
            batch.setColor(Color.WHITE);
            batch.draw(pixel, x, y, Math.round(barWidth * Math.max(0f, Math.min(1f, progress))), barHeight);
            batch.setColor(old);
        };
    }
}
