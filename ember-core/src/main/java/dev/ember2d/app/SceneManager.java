package dev.ember2d.app;

import com.badlogic.gdx.utils.Array;
import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Timers;
import dev.ember2d.core.Tweens;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import java.util.Objects;

/**
 * The scene stack (F-010), available as {@code context.scenes()}. Only the top scene runs. {@link #push},
 * {@link #pop} and {@link #replace} take effect at the start of the next frame, never in the middle of one.
 *
 * <p>Entering a scene: load its asset group (blocking), create its world, open its event scope, call
 * {@code enter}. Exiting: call {@code exit}, clear its world, close its event scope, cancel its timers and tweens,
 * unload its asset group unless another scene in the stack uses the same group.
 */
@PublicApi
public final class SceneManager {

    private static final Log LOG = Log.get("scene");

    private enum Op { PUSH, POP, REPLACE, RESTART }

    private record Change(Op op, Scene scene) {
    }

    private final TimeState time;
    private final DefaultEventBus events;
    private final boolean devMode;
    private final Array<Scene> stack = new Array<>(true, 4);
    private final Array<Change> pending = new Array<>(true, 4);
    private EngineContext context;
    private int plannedDepth;
    private Scene broken;   // dev builds: a scene whose restart failed, retried by the next restart()

    SceneManager(TimeState time, DefaultEventBus events, boolean devMode) {
        this.time = time;
        this.events = events;
        this.devMode = devMode;
    }

    /** Starts a scene on top of the current one (which stops running until the new one is popped). */
    public void push(Scene scene) {
        requireNew(scene);
        pending.add(new Change(Op.PUSH, scene));
        plannedDepth++;
    }

    /**
     * Exits the current scene and resumes the one below.
     *
     * @throws IllegalStateException if that would leave no scene
     */
    public void pop() {
        if (plannedDepth <= 1) {
            throw new IllegalStateException("pop() would leave no scene; use replace() to switch scenes");
        }
        pending.add(new Change(Op.POP, null));
        plannedDepth--;
    }

    /** Exits the current scene and starts another in its place (or starts it if there is none). */
    public void replace(Scene scene) {
        requireNew(scene);
        pending.add(new Change(Op.REPLACE, scene));
        if (plannedDepth == 0) {
            plannedDepth = 1;
        }
    }

    /**
     * Exits the current scene and enters the same scene object again with a fresh world, timers, tweens and event
     * scope; its asset group stays loaded. Used by hot reload after a map changes (F-008, I-150). In dev builds a
     * failed restart is logged and leaves the scene out of the stack until the next {@code restart()} retries it.
     *
     * @throws IllegalStateException if there is no scene to restart
     */
    public void restart() {
        if (plannedDepth == 0 && broken == null) {
            throw new IllegalStateException("restart() needs a current scene");
        }
        pending.add(new Change(Op.RESTART, null));
    }

    /** Whether {@link #restart()} has a scene to restart. */
    boolean canRestart() {
        return plannedDepth > 0 || broken != null;
    }

    /** Dev builds: whether a scene's restart failed and waits for the next {@link #restart()}. */
    boolean isBroken() {
        return broken != null;
    }

    /** The running scene, or null. */
    public Scene current() {
        return stack.isEmpty() ? null : stack.peek();
    }

    /** Number of scenes in the stack. */
    public int depth() {
        return stack.size;
    }

    void attach(EngineContext context) {
        this.context = context;
    }

    /** Engine hook: the scene part of each phase. Pending changes apply at the start of {@code INPUT}. */
    void runPhase(Phase phase) {
        if (phase == Phase.INPUT) {
            applyPending();
        }
        Scene scene = current();
        if (scene == null) {
            return;
        }
        if (phase == Phase.UPDATE) {
            scene.timersInternal().update();
            scene.tweensInternal().update();
        }
        scene.worldInternal().runPhase(phase, time);
        if (phase == Phase.UPDATE) {
            scene.update(time.delta());
        }
    }

    /** Applies the changes requested so far; changes requested meanwhile wait for the next frame. */
    void applyPending() {
        int count = pending.size;
        for (int i = 0; i < count; i++) {
            Change change = pending.removeIndex(0);
            switch (change.op()) {
                case PUSH -> enter(change.scene());
                case POP -> exit(stack.peek());
                case REPLACE -> {
                    if (!stack.isEmpty()) {
                        exit(stack.peek());
                    }
                    enter(change.scene());
                }
                case RESTART -> restartTop();
            }
        }
    }

    private void restartTop() {
        Scene scene;
        if (broken != null) {
            scene = broken;
            broken = null;
        } else if (!stack.isEmpty()) {
            scene = stack.peek();
            exit(scene, true);
        } else {
            return;
        }
        try {
            enter(scene);
        } catch (RuntimeException e) {
            if (!devMode) {
                throw e;
            }
            broken = scene;
            LOG.error("Restarting " + scene.getClass().getSimpleName() + " failed; fix the file and save it to try "
                    + "again", e);
        }
    }

    /** Exits every scene, top first (shutdown). */
    void exitAll() {
        pending.clear();
        broken = null;
        while (!stack.isEmpty()) {
            exit(stack.peek());
        }
        plannedDepth = 0;
    }

    private void enter(Scene scene) {
        String group = scene.assetGroup();
        if (group != null) {
            context.assets().loadGroup(group).finish();
        }
        scene.attach(context, new World(devMode), new Timers(time), new Tweens(time));
        scene.eventScope = events.openScope();
        stack.add(scene);
        try {
            scene.enter(context);
        } catch (RuntimeException e) {
            stack.pop();
            release(scene, false);
            throw e;
        }
        LOG.info("Entered " + scene.getClass().getSimpleName() + " (stack depth " + stack.size + ")");
    }

    private void exit(Scene scene) {
        exit(scene, false);
    }

    private void exit(Scene scene, boolean keepGroup) {
        try {
            scene.exit();
        } finally {
            stack.pop();
            release(scene, keepGroup);
            LOG.info("Exited " + scene.getClass().getSimpleName() + " (stack depth " + stack.size + ")");
        }
    }

    private void release(Scene scene, boolean keepGroup) {
        scene.worldInternal().clear();
        events.closeScope(scene.eventScope);
        scene.timersInternal().cancelAll();
        scene.tweensInternal().cancelAll();
        String group = scene.assetGroup();
        if (group != null && !keepGroup && !groupInUse(group)) {
            context.assets().unloadGroup(group);
        }
        scene.detach();
    }

    private boolean groupInUse(String group) {
        for (Scene other : stack) {
            if (group.equals(other.assetGroup())) {
                return true;
            }
        }
        return false;
    }

    private void requireNew(Scene scene) {
        Objects.requireNonNull(scene, "scene");
        if (scene.isActive() || stack.contains(scene, true)) {
            throw new IllegalArgumentException(scene.getClass().getSimpleName() + " is already in the scene stack");
        }
        for (Change change : pending) {
            if (change.scene() == scene) {
                throw new IllegalArgumentException(scene.getClass().getSimpleName() + " is already waiting to start");
            }
        }
    }
}
