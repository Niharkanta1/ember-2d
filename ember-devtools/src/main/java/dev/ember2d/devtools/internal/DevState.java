package dev.ember2d.devtools.internal;

import dev.ember2d.app.EngineContext;
import dev.ember2d.ecs.World;
import imgui.type.ImBoolean;
import java.util.ArrayDeque;
import java.util.Deque;

/** What the panels share (F-018, I-160): the engine, the selection, the logs and which panels are open. */
public final class DevState {

    /** Reload history lines kept for the Assets panel. */
    static final int RELOAD_HISTORY = 10;

    final EngineContext context;
    final ManualReloadWatcher reloads;   // null without hot reload
    final EventLog events = new EventLog(200);
    final FrameStats frames = new FrameStats(240);
    final Deque<String> reloadHistory = new ArrayDeque<>();
    String lastReloadError;
    int selected = World.NONE;

    final ImBoolean entitiesOpen = new ImBoolean(true);
    final ImBoolean inspectorOpen = new ImBoolean(true);
    final ImBoolean animationOpen = new ImBoolean(false);
    final ImBoolean timeOpen = new ImBoolean(true);
    final ImBoolean frameOpen = new ImBoolean(false);
    final ImBoolean debugOpen = new ImBoolean(false);
    final ImBoolean eventsOpen = new ImBoolean(false);
    final ImBoolean assetsOpen = new ImBoolean(false);

    DevState(EngineContext context, ManualReloadWatcher reloads) {
        this.context = context;
        this.reloads = reloads;
    }

    /** The current scene's world, or null. */
    World world() {
        return context.scenes().currentWorld();
    }

    /** The selected entity if it is still alive in the current world, else {@link World#NONE}. */
    int selection() {
        World w = world();
        if (w == null || selected == World.NONE || !w.isAlive(selected)) {
            return World.NONE;
        }
        return selected;
    }

    void reloaded(String line) {
        reloadHistory.addFirst(line);
        while (reloadHistory.size() > RELOAD_HISTORY) {
            reloadHistory.removeLast();
        }
    }
}
