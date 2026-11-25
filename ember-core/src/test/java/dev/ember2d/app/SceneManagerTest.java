package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.Phase;
import dev.ember2d.core.TimerHandle;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.Subscription;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SceneManagerTest {

    record Ping() {
    }

    /** Records group loads and unloads. */
    private final class FakeAssets implements Assets {
        @Override
        public <T> T get(String id, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TextureRegion region(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AsyncLoad loadGroup(String group) {
            log.add("load " + group);
            return new AsyncLoad() {
                @Override
                public String group() {
                    return group;
                }

                @Override
                public float progress() {
                    return 1f;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public void finish() {
                    log.add("finished " + group);
                }
            };
        }

        @Override
        public void unloadGroup(String group) {
            log.add("unload " + group);
        }

        @Override
        public boolean isLoaded(String id) {
            return false;
        }

        @Override
        public boolean contains(String id) {
            return false;
        }
    }

    /** A scene that records its lifecycle and keeps handles for leak checks. */
    private class Recorder extends Scene {
        final String name;
        final String group;
        World worldSeen;
        Subscription subscription;
        TimerHandle timer;
        int updates;

        Recorder(String name, String group) {
            this.name = name;
            this.group = group;
        }

        @Override
        protected void enter(EngineContext context) {
            log.add("enter " + name);
            worldSeen = world();
            world().add(world().create(), Transform.class);
            subscription = context.events().on(Ping.class, p -> log.add(name + " got ping"));
            timer = timers().every(1f, () -> { });
        }

        @Override
        protected void exit() {
            log.add("exit " + name);
        }

        @Override
        protected void update(float delta) {
            updates++;
        }

        @Override
        protected String assetGroup() {
            return group;
        }
    }

    private final List<String> log = new ArrayList<>();
    private final EngineContext.Builder builder = new EngineContext.Builder(new EngineConfig()).assets(new FakeAssets());
    private final EngineContext context = builder.build();
    private final SceneManager scenes = context.scenes();
    private final GameLoop loop = new GameLoop(() -> 16_666_668L, builder.timeState(), scenes::runPhase, 5);

    private void frame() {
        loop.frame();
    }

    @Test
    void changesWaitForTheNextFrame() {
        Recorder a = new Recorder("a", null);
        scenes.push(a);

        assertThat(log).isEmpty();
        assertThat(scenes.current()).isNull();
        frame();
        assertThat(log).containsExactly("enter a");
        assertThat(scenes.current()).isSameAs(a);
    }

    @Test
    void pushPopAndReplaceRunEnterAndExitInOrder() {
        Recorder a = new Recorder("a", null);
        Recorder b = new Recorder("b", null);
        Recorder c = new Recorder("c", null);
        scenes.push(a);
        frame();
        scenes.push(b);
        frame();
        assertThat(scenes.depth()).isEqualTo(2);

        scenes.replace(c);
        frame();
        scenes.pop();
        frame();

        assertThat(log).containsExactly("enter a", "enter b", "exit b", "enter c", "exit c");
        assertThat(scenes.current()).isSameAs(a);
        assertThat(scenes.depth()).isEqualTo(1);
    }

    @Test
    void exitLeavesNothingBehind() {
        Recorder a = new Recorder("a", null);
        scenes.push(a);
        frame();
        World world = a.worldSeen;
        assertThat(world.entityCount()).isEqualTo(1);

        scenes.replace(new Recorder("b", null));
        frame();
        context.events().publish(new Ping());

        assertThat(world.entityCount()).isZero();
        assertThat(a.subscription.isActive()).isFalse();
        assertThat(a.timer.isActive()).isFalse();
        assertThat(log).containsExactly("enter a", "exit a", "enter b", "b got ping");
        assertThatThrownBy(a::world).isInstanceOf(IllegalStateException.class).hasMessageContaining("not active");
    }

    @Test
    void onlyTheTopSceneRunsAndReceivesScopedEvents() {
        Recorder a = new Recorder("a", null);
        Recorder b = new Recorder("b", null);
        int[] systemRuns = {0};
        scenes.push(a);
        frame();
        a.worldSeen.addSystem(new EntitySystem(Phase.UPDATE, a.worldSeen.family(Transform.class)) {
            @Override
            protected void process(int entity, float delta) {
                systemRuns[0]++;
            }
        });
        frame();
        scenes.push(b);
        frame();
        frame();

        assertThat(systemRuns[0]).as("a's systems stop while b is on top").isEqualTo(1);
        assertThat(a.updates).as("a ran in the two frames before b was pushed").isEqualTo(2);
        assertThat(b.updates).isEqualTo(2);
        context.events().publish(new Ping());
        assertThat(log).contains("a got ping", "b got ping");   // a is paused, not exited: its subscription stays
    }

    @Test
    void assetGroupIsLoadedBeforeEnterAndUnloadedAfterExitUnlessShared() {
        scenes.push(new Recorder("menu", "ui"));
        frame();
        scenes.push(new Recorder("options", "ui"));
        frame();
        scenes.pop();
        frame();
        scenes.replace(new Recorder("level", "level1"));
        frame();

        assertThat(log).containsExactly(
                "load ui", "finished ui", "enter menu",
                "load ui", "finished ui", "enter options",
                "exit options",                               // "ui" still used by menu: not unloaded
                "exit menu", "unload ui",
                "load level1", "finished level1", "enter level");
    }

    @Test
    void sceneCannotBeUsedTwiceAndTheLastSceneCannotBePopped() {
        Recorder a = new Recorder("a", null);
        scenes.push(a);
        assertThatThrownBy(() -> scenes.push(a)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already waiting");
        frame();
        assertThatThrownBy(() -> scenes.push(a)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in the scene stack");
        assertThatThrownBy(scenes::pop).isInstanceOf(IllegalStateException.class).hasMessageContaining("no scene");
    }

    @Test
    void sceneRequestedDuringEnterStartsNextFrame() {
        Recorder second = new Recorder("second", null);
        scenes.push(new Scene() {
            @Override
            protected void enter(EngineContext ctx) {
                log.add("enter first");
                ctx.scenes().replace(second);
            }
        });

        frame();
        assertThat(log).containsExactly("enter first");
        frame();
        assertThat(log).containsExactly("enter first", "enter second");
    }

    @Test
    void failedEnterLeavesNoSceneBehind() {
        scenes.push(new Scene() {
            @Override
            protected void enter(EngineContext ctx) {
                throw new IllegalStateException("broken level");
            }
        });

        assertThatThrownBy(this::frame).hasMessage("broken level");
        assertThat(scenes.depth()).isZero();
    }

    @Test
    void firstSceneStartsAtBootAndScenesExitAtShutdown() {
        EngineConfig config = new EngineConfig();
        Recorder first = new Recorder("first", null);
        config.firstScene = () -> first;
        EmberGame game = new EmberGame(config,
                c -> new GdxAssets(AssetManifest.empty("test"), new InternalFileHandleResolver()));

        game.create();
        assertThat(log).containsExactly("enter first");
        assertThat(game.context().scenes().current()).isSameAs(first);

        game.dispose();
        assertThat(log).containsExactly("enter first", "exit first");
        assertThat(game.context().scenes().depth()).isZero();
    }
}
