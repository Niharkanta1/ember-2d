package dev.ember2d.animation;

import dev.ember2d.core.PublicApi;

/**
 * Plays clips of one clip set on the entity's {@code Sprite} (F-007). {@code clipSet}, {@code clip} and {@code speed}
 * are data (prefabs, Tiled); the rest is playback state kept by the {@link AnimationSystem}. Setting {@code clip} to
 * another name starts that clip on the next update.
 */
@PublicApi
public final class Animator {

    /** Asset ID of the clip set. */
    public String clipSet;
    /** The clip to play. */
    public String clip;
    /** Playback speed; 1 = normal, 0 = frozen. */
    public float speed = 1f;

    String started;
    boolean restart;
    int frame;
    int direction = 1;
    float frameTime;
    float time;
    boolean finished;
    ClipSet resolved;
    String warned;

    /** Plays a clip from its first frame, even if it is already playing. */
    public void play(String name) {
        clip = name;
        restart = true;
    }

    /** Plays a clip unless it is the current one. */
    public void playIfNotPlaying(String name) {
        if (name != null && !name.equals(clip)) {
            clip = name;
        }
    }

    /** True when a {@code once} clip has reached its last frame. */
    public boolean isFinished() {
        return finished && clip != null && clip.equals(started);
    }

    /** The current frame index. */
    public int frame() {
        return frame;
    }

    /** Seconds since the current clip started (scaled by speed). */
    public float time() {
        return time;
    }
}
