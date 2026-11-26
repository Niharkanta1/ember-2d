package dev.ember2d.tilemap;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;

/**
 * An animated tile driven by game time (F-009). Replaces libGDX's {@link AnimatedTiledMapTile}, whose single static
 * real-time clock keeps running while the game is paused. Same frames, durations, id and properties.
 */
final class GameTimeAnimatedTile implements TiledMapTile {

    /** The game time shared by all animated tiles of one map, in milliseconds. */
    static final class Clock {
        long millis;

        void set(double seconds) {
            millis = (long) (seconds * 1000.0);
        }
    }

    private final Clock clock;
    private final StaticTiledMapTile[] frames;
    private final int[] durations;
    private final long loop;
    private final MapProperties properties;
    private final MapObjects objects;
    private int id;
    private BlendMode blendMode;
    private float offsetX;
    private float offsetY;

    GameTimeAnimatedTile(AnimatedTiledMapTile source, Clock clock) {
        this.clock = clock;
        this.frames = source.getFrameTiles();
        this.durations = source.getAnimationIntervals().clone();
        long total = 0;
        for (int d : durations) {
            total += Math.max(1, d);
        }
        this.loop = Math.max(1, total);
        this.properties = source.getProperties();
        this.objects = source.getObjects();
        this.id = source.getId();
        this.blendMode = source.getBlendMode();
        this.offsetX = source.getOffsetX();
        this.offsetY = source.getOffsetY();
    }

    Clock clock() {
        return clock;
    }

    /** The frame index at the clock's current time. */
    int frameIndex() {
        long t = Math.floorMod(clock.millis, loop);
        for (int i = 0; i < durations.length; i++) {
            long d = Math.max(1, durations[i]);
            if (t < d) {
                return i;
            }
            t -= d;
        }
        return durations.length - 1;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public void setId(int id) {
        this.id = id;
    }

    @Override
    public BlendMode getBlendMode() {
        return blendMode;
    }

    @Override
    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode;
    }

    @Override
    public TextureRegion getTextureRegion() {
        return frames[frameIndex()].getTextureRegion();
    }

    @Override
    public void setTextureRegion(TextureRegion textureRegion) {
        throw new UnsupportedOperationException("Cannot set the texture region of an animated tile");
    }

    @Override
    public float getOffsetX() {
        return offsetX;
    }

    @Override
    public void setOffsetX(float offsetX) {
        this.offsetX = offsetX;
    }

    @Override
    public float getOffsetY() {
        return offsetY;
    }

    @Override
    public void setOffsetY(float offsetY) {
        this.offsetY = offsetY;
    }

    @Override
    public MapProperties getProperties() {
        return properties;
    }

    @Override
    public MapObjects getObjects() {
        return objects;
    }
}
