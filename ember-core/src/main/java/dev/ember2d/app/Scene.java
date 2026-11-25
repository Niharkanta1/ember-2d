package dev.ember2d.app;

import dev.ember2d.core.PublicApi;
import dev.ember2d.core.Timers;
import dev.ember2d.core.Tweens;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;

/**
 * One screen of a game: a level, a menu, a cutscene (F-010). A scene owns a {@link World}, its own timers and tweens,
 * and the event subscriptions it makes with {@code context.events().on(...)}; all of them are released when the scene
 * exits. Scenes are started with {@link SceneManager} ({@code context.scenes()}) or {@code EngineConfig.firstScene}.
 *
 * <pre>{@code
 * final class LevelScene extends Scene {
 *     protected void enter(EngineContext ctx) {
 *         int player = world().create();
 *         world().add(player, Transform.class).teleport(2, 3);
 *         world().addSystem(new SpriteRenderSystem(world(), ctx.renderer(), ctx.assets(), ctx.time()));
 *     }
 * }
 * }</pre>
 */
@PublicApi
public abstract class Scene {

    private EngineContext context;
    private World world;
    private Timers timers;
    private Tweens tweens;
    DefaultEventBus.Scope eventScope;

    /** Builds the scene: entities, systems, subscriptions. Its asset group is already loaded. */
    protected abstract void enter(EngineContext context);

    /** Called before the scene's world, subscriptions, timers and tweens are released. */
    protected void exit() {
    }

    /** Per-frame hook in {@code UPDATE}, after the world's systems; {@code delta} is scaled (0 while paused). */
    protected void update(float delta) {
    }

    /** Asset group loaded before {@link #enter} and unloaded after {@link #exit} (unless another scene uses it). */
    protected String assetGroup() {
        return null;
    }

    /** The scene's world; cleared when the scene exits. */
    protected final World world() {
        requireActive();
        return world;
    }

    protected final EngineContext context() {
        requireActive();
        return context;
    }

    /** Timers that run while this scene is the current scene and are cancelled when it exits. */
    protected final Timers timers() {
        requireActive();
        return timers;
    }

    /** Tweens that run while this scene is the current scene and are cancelled when it exits. */
    protected final Tweens tweens() {
        requireActive();
        return tweens;
    }

    final boolean isActive() {
        return context != null;
    }

    final void attach(EngineContext context, World world, Timers timers, Tweens tweens) {
        this.context = context;
        this.world = world;
        this.timers = timers;
        this.tweens = tweens;
    }

    final void detach() {
        context = null;
        world = null;
        timers = null;
        tweens = null;
        eventScope = null;
    }

    final World worldInternal() {
        return world;
    }

    final Timers timersInternal() {
        return timers;
    }

    final Tweens tweensInternal() {
        return tweens;
    }

    private void requireActive() {
        if (context == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " is not active (use it from enter() onwards, "
                    + "until exit() returns)");
        }
    }
}
