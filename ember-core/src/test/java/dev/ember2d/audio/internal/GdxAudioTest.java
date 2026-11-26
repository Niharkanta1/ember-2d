package dev.ember2d.audio.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.audio.AudioCategory;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The audio service (F-011, I-210) with recording sounds and music. */
class GdxAudioTest {

    /** A recording {@link Sound}: every call as "name args". */
    static final class FakeSound {
        final List<String> calls = new ArrayList<>();
        final List<float[]> plays = new ArrayList<>();
        long next = 1;
        final Sound sound = (Sound) Proxy.newProxyInstance(Sound.class.getClassLoader(), new Class<?>[] {Sound.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    calls.add(name + (args == null ? "" : " " + java.util.Arrays.toString(args)));
                    if (name.equals("play")) {
                        plays.add(new float[] {(Float) args[0], (Float) args[1], (Float) args[2]});
                        return next++;
                    }
                    return method.getReturnType() == long.class ? 0L : null;
                });
    }

    /** A {@link Music} with state: playing, paused, volume, looping. */
    static final class FakeMusic {
        boolean playing;
        boolean looping;
        float volume = 1f;
        int plays;
        int stops;
        final Music music = (Music) Proxy.newProxyInstance(Music.class.getClassLoader(), new Class<?>[] {Music.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "play" -> {
                        playing = true;
                        plays++;
                        yield null;
                    }
                    case "pause" -> {
                        playing = false;
                        yield null;
                    }
                    case "stop" -> {
                        playing = false;
                        stops++;
                        yield null;
                    }
                    case "isPlaying" -> playing;
                    case "setLooping" -> {
                        looping = (Boolean) args[0];
                        yield null;
                    }
                    case "isLooping" -> looping;
                    case "setVolume" -> {
                        volume = (Float) args[0];
                        yield null;
                    }
                    case "getVolume" -> volume;
                    default -> method.getReturnType() == float.class ? 0f
                            : method.getReturnType() == boolean.class ? false : null;
                });
    }

    final Map<String, Object> files = new HashMap<>();
    final FakeSound jump = new FakeSound();
    final FakeSound coin = new FakeSound();
    final FakeMusic level = new FakeMusic();
    final FakeMusic boss = new FakeMusic();
    final GdxAudio audio;

    GdxAudioTest() {
        files.put("sfx.jump", jump.sound);
        files.put("sfx.coin", coin.sound);
        files.put("music.level", level.music);
        files.put("music.boss", boss.music);
        audio = new GdxAudio(new Assets() {
            @Override
            public <T> T get(String id, Class<T> type) {
                Object asset = files.get(id);
                if (asset == null) {
                    throw new IllegalArgumentException("Unknown asset id " + id);
                }
                if (!type.isInstance(asset)) {
                    throw new IllegalArgumentException("Asset " + id + " is not a " + type.getSimpleName());
                }
                return type.cast(asset);
            }

            @Override
            public TextureRegion region(String id) {
                return null;
            }

            @Override
            public AsyncLoad loadGroup(String group) {
                return null;
            }

            @Override
            public void unloadGroup(String group) {
            }

            @Override
            public boolean isLoaded(String id) {
                return true;
            }

            @Override
            public boolean contains(String id) {
                return files.containsKey(id);
            }
        }, new Random(42));
    }

    @Test
    void soundsPlayWithCategoryVolumesAndClampedValues() {
        audio.setVolume(AudioCategory.MASTER, 0.5f);
        audio.setVolume(AudioCategory.SFX, 0.5f);
        audio.play("sfx.jump");
        audio.play("sfx.jump", 0.8f, 3f, -2f);

        assertThat(jump.plays.get(0)).containsExactly(0.25f, 1f, 0f);
        assertThat(jump.plays.get(1)[0]).isCloseTo(0.2f, within(1e-6f));
        assertThat(jump.plays.get(1)[1]).as("pitch clamped").isEqualTo(2f);
        assertThat(jump.plays.get(1)[2]).as("pan clamped").isEqualTo(-1f);

        audio.setVolume(AudioCategory.SFX, 0f);
        assertThat(audio.play("sfx.jump")).as("silent: not played").isEqualTo(-1);
        assertThat(jump.plays).hasSize(2);
        assertThat(audio.volume(AudioCategory.MASTER)).isEqualTo(0.5f);
    }

    @Test
    void variedSoundsStayInTheirRange() {
        for (int i = 0; i < 200; i++) {
            audio.playVaried("sfx.coin", 0.1f);
        }
        assertThat(coin.plays).allSatisfy(p -> {
            assertThat(p[0]).isBetween(0.9f, 1f);
            assertThat(p[1]).isBetween(0.9f, 1.1f);
        });
        assertThat(coin.plays.stream().map(p -> p[1]).distinct().count()).isGreaterThan(100);
    }

    @Test
    void positionedSoundsArePannedFromTheListenerAndFarOnesAreSkipped() {
        assertThat(audio.playAt("sfx.coin", 50f, 0f)).as("no listener yet: centred").isNotEqualTo(-1);
        audio.setListener(10f, 5f, 10f, 5.625f);
        audio.playAt("sfx.coin", 15f, 5f);
        audio.playAt("sfx.coin", 0f, 5f);
        audio.playAt("sfx.coin", 35f, 5f);
        long far = audio.playAt("sfx.coin", 45f, 5f);

        assertThat(coin.plays).extracting(p -> p[2]).containsExactly(0f, 0.5f, -1f, 1f);
        assertThat(far).as("beyond one view width outside").isEqualTo(-1);
    }

    @Test
    void theLimitStopsTheOldestInstance() {
        audio.setLimit("sfx.coin", 2);
        audio.play("sfx.coin");
        audio.play("sfx.coin");
        audio.play("sfx.coin");
        audio.play("sfx.coin");

        assertThat(coin.calls).filteredOn(c -> c.startsWith("stop")).containsExactly("stop [1]", "stop [2]");
        audio.stop("sfx.coin");
        assertThat(coin.calls).last().isEqualTo("stop");
        assertThatThrownBy(() -> audio.setLimit("sfx.coin", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void musicCrossfadesOverTheGivenTime() {
        audio.playMusic("music.level", 0f);
        assertThat(level.playing).isTrue();
        assertThat(level.looping).isTrue();
        assertThat(level.volume).isEqualTo(1f);

        audio.playMusic("music.boss", 1f);
        assertThat(audio.currentMusic()).isEqualTo("music.boss");
        assertThat(boss.volume).isZero();
        audio.update(0.25f);
        assertThat(level.volume).isCloseTo(0.75f, within(1e-5f));
        assertThat(boss.volume).isCloseTo(0.25f, within(1e-5f));
        audio.update(0.75f);
        assertThat(boss.volume).isEqualTo(1f);
        assertThat(level.playing).as("faded out and stopped").isFalse();
        assertThat(level.stops).isEqualTo(1);

        audio.playMusic("music.boss", 1f);
        assertThat(boss.plays).as("same track: nothing happens").isEqualTo(1);
    }

    @Test
    void aNewTrackDuringAFadeEndsTheOutgoingOneAtOnce() {
        audio.playMusic("music.level", 0f);
        audio.playMusic("music.boss", 2f);
        audio.update(0.5f);
        audio.playMusic("music.level", 2f, false);

        assertThat(level.stops).as("the fading level track stopped at once").isEqualTo(1);
        assertThat(audio.currentMusic()).isEqualTo("music.level");
        assertThat(level.looping).isFalse();
        assertThat(level.playing).isTrue();
    }

    @Test
    void stopMusicFadesOutAndCategoryVolumeAppliesAtOnce() {
        audio.playMusic("music.level", 0f);
        audio.setVolume(AudioCategory.MUSIC, 0.5f);
        assertThat(level.volume).isEqualTo(0.5f);
        audio.stopMusic(1f);
        assertThat(audio.currentMusic()).isNull();
        audio.update(0.5f);
        assertThat(level.volume).isCloseTo(0.25f, within(1e-5f));
        audio.update(0.6f);
        assertThat(level.playing).isFalse();
    }

    @Test
    void gamePauseAndAppSuspendPauseMusicAndFadesWait() {
        audio.playMusic("music.level", 1f);
        audio.pauseMusic();
        assertThat(level.playing).isFalse();
        audio.update(5f);
        assertThat(level.volume).as("fade waits while paused").isZero();
        audio.resumeMusic();
        assertThat(level.playing).isTrue();

        audio.play("sfx.jump");
        audio.suspend();
        assertThat(level.playing).isFalse();
        assertThat(jump.calls).contains("pause");
        assertThat(audio.play("sfx.jump")).as("nothing plays while suspended").isEqualTo(-1);
        audio.resume();
        assertThat(level.playing).isTrue();
        assertThat(jump.calls).contains("resume");

        audio.pauseMusic();
        audio.suspend();
        audio.resume();
        assertThat(level.playing).as("the game's pause outlasts a suspend").isFalse();
    }

    @Test
    void releasingAnAssetStopsItFirst() {
        audio.playMusic("music.level", 0f);
        audio.play("sfx.jump");
        audio.release("music.level");
        audio.release("sfx.jump");

        assertThat(level.playing).isFalse();
        assertThat(audio.currentMusic()).isNull();
        assertThat(jump.calls).last().isEqualTo("stop");
        audio.update(1f);   // nothing left to touch
        audio.stopAll();
    }

    @Test
    void wrongTypesAndUnknownIdsFail() {
        assertThatThrownBy(() -> audio.play("music.level")).hasMessageContaining("is not a Sound");
        assertThatThrownBy(() -> audio.playMusic("sfx.jump", 0f)).hasMessageContaining("is not a Music");
        assertThatThrownBy(() -> audio.play("sfx.nope")).hasMessageContaining("Unknown asset id");
    }
}
