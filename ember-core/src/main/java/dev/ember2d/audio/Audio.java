package dev.ember2d.audio;

import dev.ember2d.core.PublicApi;

/**
 * Sound effects and music by asset ID (F-011), available as {@code context.audio()}. Sounds are manifest entries of
 * type {@code sound}, music of type {@code music}. Volumes combine master × category × the call's volume. Music fades
 * and crossfades run on real time, so they finish while the game is paused; pausing the game does not touch audio.
 * Everything pauses while the app is suspended. Render thread only.
 */
@PublicApi
public interface Audio {

    /** Plays a sound at full volume, normal pitch, centred. Returns the instance ID, or -1 if nothing played. */
    long play(String soundId);

    /**
     * Plays a sound.
     *
     * @param volume 0..1, times the master and sfx volumes
     * @param pitch  0.5..2 (1 = normal)
     * @param pan    -1 (left) .. 1 (right)
     * @return the instance ID, or -1 if nothing played
     */
    long play(String soundId, float volume, float pitch, float pan);

    /** Plays a sound with a random pitch and volume within ±{@code variation} (0..1), for repeated sounds. */
    long playVaried(String soundId, float variation);

    /**
     * Plays a sound panned by its world position relative to the camera; not played when it is more than one view
     * width (or height) outside the view.
     */
    long playAt(String soundId, float worldX, float worldY);

    /** Stops every playing instance of a sound. */
    void stop(String soundId);

    /** At most this many instances of the sound at once; a new one stops the oldest. 0 = no limit. */
    void setLimit(String soundId, int maxInstances);

    /** Plays looping music, crossfading from the current track over {@code fadeSeconds} (0 = switch at once). */
    void playMusic(String musicId, float fadeSeconds);

    void playMusic(String musicId, float fadeSeconds, boolean loop);

    /** Fades the current music out over {@code fadeSeconds} (0 = at once) and stops it. */
    void stopMusic(float fadeSeconds);

    /** Pauses the music (the game's choice, for example in a pause menu); fades wait too. */
    void pauseMusic();

    void resumeMusic();

    /** The music asset ID playing or fading in, or null. */
    String currentMusic();

    /** Sets a category volume, 0..1; playing music follows at once. */
    void setVolume(AudioCategory category, float volume);

    float volume(AudioCategory category);
}
