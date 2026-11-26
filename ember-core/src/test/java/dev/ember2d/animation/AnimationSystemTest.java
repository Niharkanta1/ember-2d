package dev.ember2d.animation;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Phase;
import dev.ember2d.core.Time;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.render.Sprite;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Playback and frame events (F-007, I-130) with in-memory clips; no window, no textures. */
class AnimationSystemTest {

    final World world = new World(false);
    final DefaultEventBus bus = new DefaultEventBus(false);
    final AnimationLibrary library = new AnimationLibrary(noAssets());
    final List<String> events = new ArrayList<>();
    float delta;
    boolean paused;

    final Time time = (Time) Proxy.newProxyInstance(Time.class.getClassLoader(), new Class<?>[] {Time.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "delta", "unscaledDelta" -> delta;
                case "paused" -> paused;
                case "fixedDelta" -> 1f / 60f;
                case "timeScale" -> 1f;
                default -> method.getReturnType() == double.class ? 0.0 : method.getReturnType() == long.class
                        ? 0L : method.getReturnType() == float.class ? 0f : null;
            });

    AnimationSystemTest() {
        Map<String, Clip> clips = new LinkedHashMap<>();
        clips.put("walk", clip("walk", PlayMode.LOOP, new float[] {0.1f, 0.2f, 0.1f}, "a", null, "c"));
        clips.put("hit", clip("hit", PlayMode.ONCE, new float[] {0.1f, 0.1f, 0.1f}, null, null, "end"));
        clips.put("bob", clip("bob", PlayMode.PINGPONG, new float[] {0.1f, 0.1f, 0.1f}, null, null, null));
        clips.put("still", clip("still", PlayMode.PINGPONG, new float[] {0.1f}, null));
        library.put(new ClipSet("set", clips));
        world.addSystem(new AnimationSystem(world, library, bus));
        bus.onGlobal(AnimationEvent.class, e -> events.add(e.clip() + ":" + e.name()));
    }

    /** Frame index of every test region, by identity. */
    static final Map<TextureRegion, Integer> INDEX = new java.util.IdentityHashMap<>();

    static Clip clip(String name, PlayMode mode, float[] durations, String... eventNames) {
        TextureRegion[] frames = new TextureRegion[durations.length];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new TextureRegion();
            INDEX.put(frames[i], i);
        }
        String[] events = new String[durations.length];
        if (eventNames != null) {
            System.arraycopy(eventNames, 0, events, 0, Math.min(eventNames.length, events.length));
        }
        return new Clip(name, frames, durations, events, mode);
    }

    int entity(String clip) {
        int e = world.create();
        world.add(e, new Sprite());
        Animator a = world.add(e, new Animator());
        a.clipSet = "set";
        a.clip = clip;
        return e;
    }

    /** Runs one UPDATE of {@code seconds} and returns the frame shown. */
    int step(int e, float seconds) {
        delta = seconds;
        world.runPhase(Phase.UPDATE, time);
        bus.flush();
        return INDEX.get(world.get(e, Sprite.class).region);
    }

    @Test
    void loopWithVariableDurations() {
        int e = entity("walk");
        assertThat(step(e, 0f)).as("starts on frame 0").isZero();
        assertThat(events).containsExactly("walk:a");
        assertThat(step(e, 0.05f)).isZero();
        assertThat(step(e, 0.06f)).isEqualTo(1);   // 0.11
        assertThat(step(e, 0.15f)).isEqualTo(1);   // 0.26 < 0.3
        assertThat(step(e, 0.05f)).isEqualTo(2);   // 0.31
        assertThat(step(e, 0.1f)).as("wraps").isZero();
        assertThat(events).containsExactly("walk:a", "walk:c", "walk:a");
    }

    @Test
    void aLongDeltaFiresEveryFramePassedInOrder() {
        int e = entity("walk");
        step(e, 0f);
        events.clear();
        assertThat(step(e, 0.85f)).as("0.85 = 0.4 + 0.4 + 0.05").isZero();
        assertThat(events).containsExactly("walk:c", "walk:a", "walk:c", "walk:a");
    }

    @Test
    void onceStopsOnTheLastFrameAndFinishes() {
        int e = entity("hit");
        step(e, 0f);
        Animator a = world.get(e, Animator.class);
        assertThat(a.isFinished()).isFalse();
        assertThat(step(e, 0.25f)).isEqualTo(2);
        assertThat(events).containsExactly("hit:end");
        assertThat(step(e, 0.1f)).isEqualTo(2);
        assertThat(a.isFinished()).isTrue();
        assertThat(step(e, 5f)).isEqualTo(2);
        assertThat(events).as("the last frame fired once").containsExactly("hit:end");

        a.play("hit");
        assertThat(step(e, 0f)).as("play restarts").isZero();
        assertThat(a.isFinished()).isFalse();
    }

    @Test
    void pingpongDoesNotRepeatTheEndFrames() {
        int e = entity("bob");
        step(e, 0f);
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            seen.add(step(e, 0.1f));
        }
        assertThat(seen).containsExactly(1, 2, 1, 0, 1, 2);
        int s = entity("still");
        step(s, 0f);
        assertThat(step(s, 1f)).as("one frame").isZero();
    }

    @Test
    void speedPauseAndSwitchingClips() {
        int e = entity("walk");
        step(e, 0f);
        Animator a = world.get(e, Animator.class);
        a.speed = 2f;
        assertThat(step(e, 0.05f)).as("0.1 at double speed").isEqualTo(1);
        a.speed = 0f;
        assertThat(step(e, 1f)).as("frozen").isEqualTo(1);
        a.speed = 1f;
        paused = true;
        assertThat(step(e, 1f)).as("paused game: UPDATE does not run").isEqualTo(1);
        paused = false;

        a.playIfNotPlaying("walk");
        assertThat(step(e, 0f)).as("already playing: no restart").isEqualTo(1);
        a.clip = "bob";   // from data
        assertThat(step(e, 0f)).isZero();
        assertThat(a.time()).isZero();
        a.clip = "nope";
        assertThat(step(e, 0.1f)).as("unknown clip: keeps the last region").isZero();
    }

    private static Assets noAssets() {
        return new Assets() {
            @Override
            public <T> T get(String id, Class<T> type) {
                return null;
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
                return false;
            }

            @Override
            public boolean contains(String id) {
                return false;
            }
        };
    }
}
