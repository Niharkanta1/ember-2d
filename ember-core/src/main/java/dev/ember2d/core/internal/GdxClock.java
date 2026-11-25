package dev.ember2d.core.internal;

import com.badlogic.gdx.Gdx;

/** Frame durations as measured by the libGDX backend ({@code Gdx.graphics.getDeltaTime()}). */
public final class GdxClock implements Clock {

    @Override
    public long frameDeltaNanos() {
        return Gdx.graphics == null ? 0L : Math.round(Gdx.graphics.getDeltaTime() * 1_000_000_000d);
    }
}
