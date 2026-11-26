package dev.ember2d.audio.internal;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.LongArray;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;
import dev.ember2d.core.Log;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The engine's {@link Audio} over libGDX {@link Sound} and {@link Music} from the asset service (F-011, I-210). The
 * engine calls {@link #update(float)} every frame with the real (unscaled) delta, sets the listener from the camera,
 * calls {@link #suspend()} / {@link #resume()} with the app, {@link #release(String)} before an asset is unloaded and
 * {@link #stopAll()} at shutdown.
 */
public final class GdxAudio implements Audio {

    private static final Log LOG = Log.get("audio");

    /** One music track and its fade. */
    private static final class Track {
        final String id;
        final Music music;
        float fade;
        float fadeSpeed;   // fade units per second; negative while fading out

        Track(String id, Music music) {
            this.id = id;
            this.music = music;
        }
    }

    private final Assets assets;
    private final Random random;
    private final float[] volumes = {1f, 1f, 1f};
    private final Map<String, Integer> limits = new HashMap<>();
    private final Map<String, LongArray> instances = new HashMap<>();
    private final Map<String, Sound> used = new LinkedHashMap<>();
    private final Set<String> clampWarned = new HashSet<>();
    private Track current;
    private Track fadingOut;
    private boolean pausedByGame;
    private boolean suspended;
    private boolean hasListener;
    private float listenerX;
    private float listenerY;
    private float halfWidth;
    private float halfHeight;

    public GdxAudio(Assets assets) {
        this(assets, new Random());
    }

    /** For tests: a seeded random for {@link #playVaried}. */
    public GdxAudio(Assets assets, Random random) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.random = Objects.requireNonNull(random, "random");
    }

    // ---- sounds ----

    @Override
    public long play(String soundId) {
        return play(soundId, 1f, 1f, 0f);
    }

    @Override
    public long play(String soundId, float volume, float pitch, float pan) {
        Sound sound = assets.get(soundId, Sound.class);
        float v = clamp("volume", volume, 0f, 1f) * volumes[0] * volumes[2];
        float p = clamp("pitch", pitch, 0.5f, 2f);
        float n = clamp("pan", pan, -1f, 1f);
        if (v <= 0f || suspended) {
            return -1;
        }
        long id = sound.play(v, p, n);
        used.put(soundId, sound);
        if (id == -1) {
            return -1;
        }
        int limit = limits.getOrDefault(soundId, 0);
        if (limit > 0) {
            LongArray list = instances.computeIfAbsent(soundId, k -> new LongArray(true, limit + 1));
            list.add(id);
            while (list.size > limit) {
                sound.stop(list.removeIndex(0));   // the oldest instance makes room
            }
        }
        return id;
    }

    @Override
    public long playVaried(String soundId, float variation) {
        float v = MathUtils.clamp(variation, 0f, 1f);
        float pitch = 1f + (random.nextFloat() * 2f - 1f) * v;
        float volume = Math.min(1f, 1f + (random.nextFloat() * 2f - 1f) * v);
        return play(soundId, volume, pitch, 0f);
    }

    @Override
    public long playAt(String soundId, float worldX, float worldY) {
        if (!hasListener) {
            return play(soundId);
        }
        float dx = worldX - listenerX;
        float dy = worldY - listenerY;
        if (Math.abs(dx) > halfWidth * 3f || Math.abs(dy) > halfHeight * 3f) {
            return -1;   // more than one view away from the view
        }
        return play(soundId, 1f, 1f, MathUtils.clamp(dx / halfWidth, -1f, 1f));
    }

    @Override
    public void stop(String soundId) {
        assets.get(soundId, Sound.class).stop();
        LongArray list = instances.get(soundId);
        if (list != null) {
            list.clear();
        }
    }

    @Override
    public void setLimit(String soundId, int maxInstances) {
        if (maxInstances < 0) {
            throw new IllegalArgumentException("maxInstances must be >= 0 (was " + maxInstances + ")");
        }
        if (maxInstances == 0) {
            limits.remove(soundId);
            instances.remove(soundId);
        } else {
            limits.put(soundId, maxInstances);
        }
    }

    // ---- music ----

    @Override
    public void playMusic(String musicId, float fadeSeconds) {
        playMusic(musicId, fadeSeconds, true);
    }

    @Override
    public void playMusic(String musicId, float fadeSeconds, boolean loop) {
        if (current != null && current.id.equals(musicId)) {
            return;   // already playing
        }
        Music music = assets.get(musicId, Music.class);
        finishFadeOut();
        if (current != null) {
            fadeOut(current, fadeSeconds);
        }
        Track track = new Track(musicId, music);
        if (fadeSeconds > 0f) {
            track.fade = 0f;
            track.fadeSpeed = 1f / fadeSeconds;
        } else {
            track.fade = 1f;
        }
        music.setLooping(loop);
        current = track;
        applyMusicVolume();
        if (!pausedByGame && !suspended) {
            music.play();
        }
    }

    @Override
    public void stopMusic(float fadeSeconds) {
        if (current == null) {
            return;
        }
        finishFadeOut();
        fadeOut(current, fadeSeconds);
        current = null;
    }

    private void fadeOut(Track track, float fadeSeconds) {
        if (fadeSeconds <= 0f) {
            track.music.stop();
        } else {
            track.fadeSpeed = -1f / fadeSeconds;
            fadingOut = track;
        }
    }

    private void finishFadeOut() {
        if (fadingOut != null) {
            fadingOut.music.stop();
            fadingOut = null;
        }
    }

    @Override
    public void pauseMusic() {
        pausedByGame = true;
        pauseTracks();
    }

    @Override
    public void resumeMusic() {
        pausedByGame = false;
        if (!suspended) {
            playTracks();
        }
    }

    @Override
    public String currentMusic() {
        return current == null ? null : current.id;
    }

    // ---- volumes ----

    @Override
    public void setVolume(AudioCategory category, float volume) {
        volumes[category.ordinal()] = clamp(category.name().toLowerCase(Locale.ROOT) + " volume", volume, 0f,
                1f);
        applyMusicVolume();
    }

    @Override
    public float volume(AudioCategory category) {
        return volumes[category.ordinal()];
    }

    // ---- engine hooks ----

    /** Advances fades by the real (unscaled) frame delta. Frozen while suspended or paused by the game. */
    public void update(float realDelta) {
        if (suspended || pausedByGame) {
            return;
        }
        if (current != null && current.fadeSpeed != 0f) {
            current.fade = Math.min(1f, current.fade + current.fadeSpeed * realDelta);
            if (current.fade >= 1f) {
                current.fadeSpeed = 0f;
            }
        }
        if (fadingOut != null) {
            fadingOut.fade += fadingOut.fadeSpeed * realDelta;
            if (fadingOut.fade <= 0f) {
                fadingOut.music.stop();
                fadingOut = null;
            }
        }
        applyMusicVolume();
    }

    /** The camera's centre and half view size in world units, for {@link #playAt}. */
    public void setListener(float x, float y, float halfViewWidth, float halfViewHeight) {
        hasListener = halfViewWidth > 0f && halfViewHeight > 0f;
        listenerX = x;
        listenerY = y;
        halfWidth = halfViewWidth;
        halfHeight = halfViewHeight;
    }

    /** App suspended: pause every sound and the music. */
    public void suspend() {
        if (suspended) {
            return;
        }
        suspended = true;
        for (Sound sound : used.values()) {
            sound.pause();
        }
        pauseTracks();
    }

    /** App resumed: resume sounds, and the music unless the game paused it. */
    public void resume() {
        if (!suspended) {
            return;
        }
        suspended = false;
        for (Sound sound : used.values()) {
            sound.resume();
        }
        if (!pausedByGame) {
            playTracks();
        }
    }

    /** Before an asset is unloaded: stop it if it is a playing sound or music. */
    public void release(String assetId) {
        if (current != null && current.id.equals(assetId)) {
            current.music.stop();
            current = null;
        }
        if (fadingOut != null && fadingOut.id.equals(assetId)) {
            fadingOut.music.stop();
            fadingOut = null;
        }
        Sound sound = used.remove(assetId);
        if (sound != null) {
            sound.stop();
            instances.remove(assetId);
        }
    }

    /** Shutdown: stop everything. */
    public void stopAll() {
        for (Sound sound : used.values()) {
            sound.stop();
        }
        used.clear();
        instances.clear();
        finishFadeOut();
        if (current != null) {
            current.music.stop();
            current = null;
        }
    }

    private void pauseTracks() {
        if (current != null) {
            current.music.pause();
        }
        if (fadingOut != null) {
            fadingOut.music.pause();
        }
    }

    private void playTracks() {
        if (current != null) {
            current.music.play();
        }
        if (fadingOut != null) {
            fadingOut.music.play();
        }
    }

    private void applyMusicVolume() {
        float base = volumes[0] * volumes[1];
        if (current != null) {
            current.music.setVolume(base * current.fade);
        }
        if (fadingOut != null) {
            fadingOut.music.setVolume(base * Math.max(0f, fadingOut.fade));
        }
    }

    private float clamp(String what, float value, float min, float max) {
        if (value < min || value > max || Float.isNaN(value)) {
            if (clampWarned.add(what)) {
                LOG.debug("Audio " + what + " " + value + " is outside " + min + ".." + max + "; clamped (logged once)");
            }
            return Float.isNaN(value) ? min : MathUtils.clamp(value, min, max);
        }
        return value;
    }
}
