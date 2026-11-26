package dev.ember2d.animation;

import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.render.Sprite;
import java.util.Objects;

/**
 * Plays every {@link Animator} in {@code UPDATE} (F-007): advances it by the game delta times its speed (a paused game
 * pauses animation) and writes the current frame into the entity's {@link Sprite}. Frame events are queued as
 * {@link AnimationEvent} once each time a frame is entered, including frames passed in one long update and the first
 * frame of a clip. Register it after the systems that choose clips.
 */
@PublicApi
public final class AnimationSystem extends EntitySystem {

    private static final Log LOG = Log.get("animation");

    private final AnimationLibrary library;
    private final EventBus events;

    public AnimationSystem(World world, AnimationLibrary library, EventBus events) {
        super(Phase.UPDATE, world.family(Animator.class, Sprite.class));
        this.library = Objects.requireNonNull(library, "library");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    protected void process(int entity, float delta) {
        Animator a = world().get(entity, Animator.class);
        Clip clip = resolve(entity, a);
        if (clip == null) {
            return;
        }
        if (a.restart || !a.clip.equals(a.started)) {
            a.restart = false;
            a.started = a.clip;
            a.frame = 0;
            a.direction = 1;
            a.frameTime = 0f;
            a.time = 0f;
            a.finished = false;
            fire(entity, clip, 0);
        } else {
            advance(entity, a, clip, delta * a.speed);
        }
        world().get(entity, Sprite.class).region = clip.frame(a.frame);
    }

    private void advance(int entity, Animator a, Clip clip, float dt) {
        if (!(dt > 0f) || a.finished) {
            return;
        }
        a.time += dt;
        a.frameTime += dt;
        int n = clip.frameCount();
        while (a.frameTime >= clip.duration(a.frame)) {
            a.frameTime -= clip.duration(a.frame);
            switch (clip.mode()) {
                case LOOP -> a.frame = (a.frame + 1) % n;
                case ONCE -> {
                    if (a.frame == n - 1) {
                        a.finished = true;
                        a.frameTime = 0f;
                        return;
                    }
                    a.frame++;
                }
                case PINGPONG -> {
                    if (n == 1) {
                        a.frameTime = 0f;
                        return;
                    }
                    if (a.frame + a.direction < 0 || a.frame + a.direction >= n) {
                        a.direction = -a.direction;
                    }
                    a.frame += a.direction;
                }
            }
            fire(entity, clip, a.frame);
        }
    }

    private void fire(int entity, Clip clip, int frame) {
        String name = clip.eventAt(frame);
        if (name != null) {
            events.queue(new AnimationEvent(entity, clip.name(), name));
        }
    }

    private Clip resolve(int entity, Animator a) {
        if (a.clipSet == null || a.clip == null) {
            return null;
        }
        ClipSet set = library.clipSet(a.clipSet);
        if (set != a.resolved) {
            a.resolved = set;   // first use, another set, or a reloaded one
        }
        Clip clip = set.clip(a.clip);
        if (clip != null && a.frame >= clip.frameCount()) {
            a.restart = true;   // a reloaded clip got shorter
        }
        if (clip == null) {
            String key = a.clipSet + "/" + a.clip;
            if (!key.equals(a.warned)) {
                a.warned = key;
                LOG.warn("Entity " + entity + ": clip set \"" + a.clipSet + "\" has no clip \"" + a.clip + "\" "
                        + a.resolved.names());
            }
        }
        return clip;
    }
}
