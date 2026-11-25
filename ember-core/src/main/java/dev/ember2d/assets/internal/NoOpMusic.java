package dev.ember2d.assets.internal;

import com.badlogic.gdx.audio.Music;

/** Silent fallback for a missing optional music track. */
final class NoOpMusic implements Music {

    private boolean looping;
    private float volume = 1f;

    @Override
    public void play() {
    }

    @Override
    public void pause() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isPlaying() {
        return false;
    }

    @Override
    public void setLooping(boolean isLooping) {
        looping = isLooping;
    }

    @Override
    public boolean isLooping() {
        return looping;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setPan(float pan, float volume) {
        this.volume = volume;
    }

    @Override
    public void setPosition(float position) {
    }

    @Override
    public float getPosition() {
        return 0f;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
    }
}
