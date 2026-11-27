package dev.ember2d.app;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Timers;
import dev.ember2d.core.Tweens;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.ui.Ui;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The scene stack (F-010), available as {@code context.scenes()}. Only the top scene runs. {@link #push},
 * {@link #pop} and {@link #replace} take effect at the start of the next frame, never in the middle of one.
 *
 * <p>Entering a scene: load its asset group (blocking), create its world, open its event scope, call
 * {@code enter}. Exiting: call {@code exit}, clear its world, close its event scope, cancel its timers and tweens,
 * unload its asset group unless another scene in the stack, a preload or the replacing scene uses the same group.
 *
 * <p>With a {@link Transition} (I-240) the switch happens behind a cover that fades in and out; the incoming group
 * loads in the background meanwhile, with the loading screen shown if that takes a while. Changes requested during a
 * transition wait until it has finished. {@link #preload(String)} loads a group ahead of the scene that needs it.
 */
@PublicApi
public final class SceneManager {

    private static final Log LOG = Log.get("scene");

    private enum Op { PUSH, POP, REPLACE, RESTART }

    private record Change(Op op, Scene scene, Transition transition) {
    }

    private enum Stage { IDLE, COVER, LOAD, UNCOVER }

    /** The loading screen shows only after a load has taken this long (quick loads do not flash). */
    static final float LOADING_SCREEN_DELAY = 0.1f;

    private final TimeState time;
    private final DefaultEventBus events;
    private final boolean devMode;
    private final Array<Scene> stack = new Array<>(true, 4);
    private final Array<Change> pending = new Array<>(true, 4);
    private EngineContext context;
    private int plannedDepth;
    private Scene broken;
    private Supplier<Ui> uiFactory;   // dev builds: a scene whose restart failed, retried by the next restart()
    private final Map<String, AsyncLoad> preloaded = new LinkedHashMap<>();
    private Stage stage = Stage.IDLE;
    private Change active;
    private AsyncLoad incoming;
    private float elapsed;
    private float loadingFor;
    private String arrivingGroup;   // the group of the scene replacing the one that exits (kept loaded)

    SceneManager(TimeState time, DefaultEventBus events, boolean devMode) {
        this.time = time;
        this.events = events;
        this.devMode = devMode;
    }

    /** Starts a scene on top of the current one (which stops running until the new one is popped). */
    public void push(Scene scene) {
        push(scene, null);
    }

    /** {@link #push(Scene)} with a transition; the current scene stays below the new one. */
    public void push(Scene scene, Transition transition) {
        requireNew(scene);
        pending.add(new Change(Op.PUSH, scene, transition));
        plannedDepth++;
    }

    /**
     * Exits the current scene and resumes the one below.
     *
     * @throws IllegalStateException if that would leave no scene
     */
    public void pop() {
        pop(null);
    }

    /**
     * {@link #pop()} with a transition: the scene below resumes while the cover fades out.
     *
     * @throws IllegalStateException if that would leave no scene
     */
    public void pop(Transition transition) {
        if (plannedDepth <= 1) {
            throw new IllegalStateException("pop() would leave no scene; use replace() to switch scenes");
        }
        pending.add(new Change(Op.POP, null, transition));
        plannedDepth--;
    }

    /** Exits the current scene and starts another in its place (or starts it if there is none). */
    public void replace(Scene scene) {
        replace(scene, null);
    }

    /** {@link #replace(Scene)} with a transition, for example {@code replace(next, Transition.fade(0.5f))}. */
    public void replace(Scene scene, Transition transition) {
        requireNew(scene);
        pending.add(new Change(Op.REPLACE, scene, transition));
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
        pending.add(new Change(Op.RESTART, null, null));
    }

    /** Whether a transition is running (it ends when the cover has faded out again). */
    public boolean inTransition() {
        return stage != Stage.IDLE;
    }

    /**
     * Starts loading a group in the background, ahead of the scene that needs it. The group stays loaded until a scene
     * that uses it enters (that scene then owns it) or {@link #cancelPreload(String)}. Preloading a group again returns
     * the same load.
     *
     * @throws IllegalArgumentException for an unknown group
     */
    public AsyncLoad preload(String group) {
        Objects.requireNonNull(group, "group");
        AsyncLoad load = preloaded.get(group);
        if (load == null) {
            load = context.assets().loadGroup(group);
            preloaded.put(group, load);
        }
        return load;
    }

    /** Releases a preloaded group that no scene has taken; it is unloaded unless a scene uses it. */
    public void cancelPreload(String group) {
        if (preloaded.remove(group) != null && !groupInUse(group)) {
            context.assets().unloadGroup(group);
        }
    }

    /** Engine: whether presses should not reach the scene UI (the cover is fading out or waits for loading). */
    boolean blocksInput() {
        return stage == Stage.COVER || stage == Stage.LOAD;
    }

    /** Engine: how opaque the transition cover is, 0 to 1. */
    float coverAlpha() {
        float half = active == null ? 0f : active.transition().seconds() / 2f;
        return switch (stage) {
            case IDLE -> 0f;
            case COVER -> half <= 0f ? 1f : Math.min(1f, elapsed / half);
            case LOAD -> 1f;
            case UNCOVER -> half <= 0f ? 0f : 1f - Math.min(1f, elapsed / half);
        };
    }

    /** Engine: the cover colour (not a copy; do not change it), or null when no transition runs. */
    Color coverColor() {
        return active == null ? null : active.transition().colorInternal();
    }

    /** Engine: the progress to show on the loading screen, or -1 when it does not show. */
    float loadingProgress() {
        if (stage != Stage.LOAD || incoming == null || incoming.isDone() || loadingFor < LOADING_SCREEN_DELAY) {
            return -1f;
        }
        return incoming.progress();
    }

    /** Engine: how scenes create their UI (none without a renderer). */
    void setUiFactory(Supplier<Ui> factory) {
        this.uiFactory = factory;
    }

    Ui createUi() {
        if (uiFactory == null) {
            throw new IllegalStateException("Scene UI needs a renderer (none on headless backends or in test contexts)");
        }
        return uiFactory.get();
    }

    /** The current scene's UI, or null when it has none. */
    Ui currentUi() {
        Scene scene = current();
        return scene == null ? null : scene.ui;
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

    /** The current scene's world, or null when no scene runs (dev tools, F-018). */
    public World currentWorld() {
        Scene scene = current();
        return scene == null ? null : scene.worldInternal();
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
        if (stage == Stage.COVER || stage == Stage.LOAD) {
            if (phase == Phase.RENDER) {
                scene.worldInternal().runPhase(phase, time);   // the outgoing scene is drawn, frozen
            }
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
        if (stage != Stage.IDLE) {
            advance(time.unscaledDelta());
            if (stage != Stage.IDLE) {
                return;   // later changes wait for the transition
            }
        }
        int count = pending.size;
        for (int i = 0; i < count; i++) {
            Change change = pending.removeIndex(0);
            if (change.transition() != null) {
                startTransition(change);
                if (stage != Stage.IDLE) {
                    return;
                }
                continue;
            }
            switch (change.op()) {
                case PUSH -> enter(change.scene());
                case POP -> exit(stack.peek());
                case REPLACE -> {
                    exitReplaced(change.scene());
                    enter(change.scene());
                }
                case RESTART -> restartTop();
            }
        }
    }

    /** Exits the top scene for a replace; its group stays loaded when the replacing scene uses it too. */
    private void exitReplaced(Scene replacement) {
        if (stack.isEmpty()) {
            return;
        }
        arrivingGroup = replacement.assetGroup();
        try {
            exit(stack.peek());
        } finally {
            arrivingGroup = null;
        }
    }

    private void startTransition(Change change) {
        active = change;
        stage = Stage.COVER;
        elapsed = 0f;
        loadingFor = 0f;
        String group = change.scene() == null ? null : change.scene().assetGroup();
        if (group != null) {
            AsyncLoad load = preloaded.get(group);
            incoming = load != null ? load : context.assets().loadGroup(group);   // loads during the fade-out
        }
        advance(0f);   // a cut switches in this frame
    }

    /** Steps the running transition by {@code delta} seconds of real time. */
    private void advance(float delta) {
        float half = active.transition().seconds() / 2f;
        float step = delta;
        if (stage == Stage.COVER) {
            elapsed += step;
            if (elapsed < half) {
                return;
            }
            if (active.op() == Op.POP) {
                exit(stack.peek());
            } else if (active.op() == Op.REPLACE) {
                exitReplaced(active.scene());
            }   // PUSH: the current scene stays below
            stage = Stage.LOAD;
            step = 0f;
        }
        if (stage == Stage.LOAD) {
            if (incoming != null && !incoming.isDone()) {
                loadingFor += step;
                return;
            }
            if (active.op() != Op.POP) {
                try {
                    enter(active.scene());
                } catch (RuntimeException e) {
                    endTransition();
                    throw e;
                }
            }
            stage = Stage.UNCOVER;
            elapsed = 0f;
            step = 0f;
        }
        elapsed += step;
        if (elapsed >= half) {
            endTransition();
        }
    }

    private void endTransition() {
        stage = Stage.IDLE;
        active = null;
        incoming = null;
        elapsed = 0f;
        loadingFor = 0f;
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
        endTransition();
        while (!stack.isEmpty()) {
            exit(stack.peek());
        }
        plannedDepth = 0;
        for (String group : preloaded.keySet().toArray(new String[0])) {
            cancelPreload(group);
        }
    }

    private void enter(Scene scene) {
        String group = scene.assetGroup();
        if (group != null) {
            AsyncLoad load = preloaded.remove(group);   // a preloaded group now belongs to the scene
            if (load == null && incoming != null && incoming.group().equals(group)) {
                load = incoming;
            }
            (load != null ? load : context.assets().loadGroup(group)).finish();
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
        if (scene.ui != null) {
            scene.ui.dispose();
            scene.ui = null;
        }
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
        if (group.equals(arrivingGroup) || preloaded.containsKey(group)) {
            return true;
        }
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
