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

    private enum Op { PUSH, POP, REPLACE }

    private record Change(Op op, Scene scene) {
    }

    private final TimeState time;
    private final DefaultEventBus events;
    private final boolean devMode;
    private final Array<Scene> stack = new Array<>(true, 4);
    private final Array<Change> pending = new Array<>(true, 4);
    private EngineContext context;
    private int plannedDepth;

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
            }
        }
    }

    /** Exits every scene, top first (shutdown). */
    void exitAll() {
        pending.clear();
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
            release(scene);
            throw e;
        }
        LOG.info("Entered " + scene.getClass().getSimpleName() + " (stack depth " + stack.size + ")");
    }

    private void exit(Scene scene) {
        try {
            scene.exit();
        } finally {
            stack.pop();
            release(scene);
            LOG.info("Exited " + scene.getClass().getSimpleName() + " (stack depth " + stack.size + ")");
        }
    }

    private void release(Scene scene) {
        scene.worldInternal().clear();
        events.closeScope(scene.eventScope);
        scene.timersInternal().cancelAll();
        scene.tweensInternal().cancelAll();
        String group = scene.assetGroup();
        if (group != null && !groupInUse(group)) {
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
