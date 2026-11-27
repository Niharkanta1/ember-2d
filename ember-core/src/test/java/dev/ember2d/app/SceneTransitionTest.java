package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.assets.Assets;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.internal.GameLoop;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Scene transitions, the loading screen and preloading (F-010, I-240). */
class SceneTransitionTest {

    private final List<String> log = new ArrayList<>();
    /** Groups that load in the background until {@link #loaded} lists them. */
    private final Set<String> slow = new HashSet<>();
    private final Set<String> loaded = new HashSet<>();

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
                    return isDone() ? 1f : 0.5f;
                }

                @Override
                public boolean isDone() {
                    return !slow.contains(group) || loaded.contains(group);
                }

                @Override
                public void finish() {
                    log.add("finished " + group);
                    loaded.add(group);
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

    private final class Recorder extends Scene {
        final String name;
        final String group;
        int updates;

        Recorder(String name, String group) {
            this.name = name;
            this.group = group;
        }

        @Override
        protected void enter(EngineContext context) {
            log.add("enter " + name);
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

    private final EngineContext.Builder builder = new EngineContext.Builder(new EngineConfig()).assets(new FakeAssets());
    private final EngineContext context = builder.build();
    private final SceneManager scenes = context.scenes();
    private final GameLoop loop = new GameLoop(() -> 16_666_668L, builder.timeState(), scenes::runPhase, 5);

    private void frame() {
        loop.frame();
    }

    private int framesUntil(java.util.function.BooleanSupplier done) {
        for (int frames = 1; frames <= 600; frames++) {
            frame();
            if (done.getAsBoolean()) {
                return frames;
            }
        }
        throw new AssertionError("never happened");
    }

    private Recorder start(String name, String group) {
        Recorder scene = new Recorder(name, group);
        scenes.push(scene);
        frame();
        log.clear();
        return scene;
    }

    @Test
    void aFadeFreezesTheOutgoingSceneAndSwitchesAtFullCover() {
        Recorder a = start("a", null);
        Recorder b = new Recorder("b", null);
        int updatesBefore = a.updates;
        scenes.replace(b, Transition.fade(0.5f));
        frame();
        assertThat(scenes.inTransition()).isTrue();
        assertThat(scenes.blocksInput()).isTrue();
        assertThat(scenes.coverColor()).isEqualTo(Color.BLACK);

        float lastAlpha = scenes.coverAlpha();
        for (int i = 0; i < 7; i++) {
            frame();
            assertThat(scenes.coverAlpha()).isGreaterThan(lastAlpha);
            lastAlpha = scenes.coverAlpha();
        }
        assertThat(a.updates).as("frozen while covered").isEqualTo(updatesBefore);
        assertThat(log).isEmpty();

        int toSwitch = framesUntil(() -> scenes.current() == b);
        assertThat(8 + toSwitch).as("half of 0.5 s at 60 fps").isBetween(15, 16);
        assertThat(log).containsExactly("exit a", "enter b");
        assertThat(scenes.coverAlpha()).isEqualTo(1f);
        assertThat(scenes.blocksInput()).isFalse();

        frame();
        assertThat(scenes.coverAlpha()).isLessThan(1f);
        assertThat(b.updates).as("the new scene runs from the frame it enters").isEqualTo(2);
        int toEnd = framesUntil(() -> !scenes.inTransition());
        assertThat(1 + toEnd).isBetween(15, 16);
        assertThat(scenes.coverAlpha()).isZero();
        assertThat(scenes.coverColor()).isNull();
    }

    @Test
    void aLoadingGroupHoldsTheCoverAndShowsTheLoadingScreenAfterTheDelay() {
        start("a", null);
        slow.add("big");
        Recorder b = new Recorder("b", "big");
        scenes.replace(b, Transition.fade(0.2f));
        frame();
        assertThat(log).as("loading starts with the fade-out").containsExactly("load big");

        framesUntil(() -> log.contains("exit a"));
        assertThat(scenes.current()).isNull();
        assertThat(scenes.coverAlpha()).isEqualTo(1f);
        assertThat(scenes.loadingProgress()).as("not before the delay").isEqualTo(-1f);
        for (int i = 0; i < 6; i++) {
            frame();
        }
        assertThat(scenes.loadingProgress()).isEqualTo(0.5f);
        assertThat(scenes.current()).isNull();

        loaded.add("big");
        frame();
        assertThat(scenes.current()).isSameAs(b);
        assertThat(scenes.loadingProgress()).isEqualTo(-1f);
        assertThat(log).containsExactly("load big", "exit a", "finished big", "enter b");
    }

    @Test
    void aCutSwitchesInOneFrame() {
        start("a", null);
        Recorder b = new Recorder("b", "g");
        scenes.replace(b, Transition.cut());
        frame();
        assertThat(scenes.current()).isSameAs(b);
        assertThat(scenes.inTransition()).isFalse();
        assertThat(scenes.coverAlpha()).isZero();
        assertThat(log).containsExactly("load g", "exit a", "finished g", "enter b");
    }

    @Test
    void changesRequestedDuringATransitionWaitForIt() {
        start("a", null);
        Recorder b = new Recorder("b", null);
        Recorder c = new Recorder("c", null);
        scenes.replace(b, Transition.fade(0.2f));
        frame();
        scenes.push(c);
        framesUntil(() -> scenes.current() == b);
        frame();
        assertThat(scenes.current()).as("c waits for the fade-in").isSameAs(b);
        framesUntil(() -> !scenes.inTransition());
        assertThat(scenes.current()).isSameAs(c);
        assertThat(log).containsExactly("exit a", "enter b", "enter c");
    }

    @Test
    void pushAndPopWithTransitionsKeepTheSceneBelow() {
        Recorder a = start("a", null);
        Recorder b = new Recorder("b", null);
        scenes.push(b, Transition.fade(0.2f));
        framesUntil(() -> !scenes.inTransition());
        assertThat(scenes.depth()).isEqualTo(2);
        assertThat(log).containsExactly("enter b");

        int updates = a.updates;
        scenes.pop(Transition.fade(0.2f));
        framesUntil(() -> scenes.current() == a);
        assertThat(log).containsExactly("enter b", "exit b");
        framesUntil(() -> !scenes.inTransition());
        assertThat(a.updates).as("a runs again while uncovering").isGreaterThan(updates);
        assertThatThrownBy(() -> scenes.pop(Transition.cut())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPreloadedGroupIsUsedOnceAndSurvivesUntilItsSceneExits() {
        Recorder menu = start("menu", null);
        slow.add("level");
        AsyncLoad load = scenes.preload("level");
        assertThat(scenes.preload("level")).isSameAs(load);
        loaded.add("level");   // loaded in the background while the menu shows
        frame();

        Recorder level = new Recorder("level", "level");
        scenes.replace(level, Transition.fade(0.2f));
        framesUntil(() -> !scenes.inTransition());
        assertThat(log).containsExactly("load level", "exit menu", "finished level", "enter level");

        log.clear();
        scenes.replace(new Recorder("menu2", null));
        frame();
        assertThat(log).as("the scene owns the group now").containsExactly("exit level", "unload level",
                "enter menu2");
        assertThat(menu.updates).isPositive();
    }

    @Test
    void cancelPreloadUnloadsAGroupNoSceneUses() {
        start("a", "shared");
        scenes.preload("extra");
        scenes.preload("shared");
        scenes.cancelPreload("extra");
        scenes.cancelPreload("shared");
        scenes.cancelPreload("never");
        assertThat(log).containsExactly("load extra", "load shared", "unload extra");
    }

    @Test
    void aPreloadedGroupStaysWhenTheSceneUsingItExits() {
        start("a", "g");
        scenes.preload("g");
        scenes.replace(new Recorder("b", null));
        frame();
        assertThat(log).containsExactly("load g", "exit a", "enter b");
    }

    @Test
    void replacingWithASceneOfTheSameGroupKeepsTheGroupLoaded() {
        start("a", "g");
        scenes.replace(new Recorder("b", "g"));
        frame();
        scenes.replace(new Recorder("c", "g"), Transition.fade(0.1f));
        framesUntil(() -> !scenes.inTransition());
        assertThat(log).doesNotContain("unload g");
    }

    @Test
    void aFailingEnterEndsTheTransition() {
        start("a", null);
        scenes.replace(new Scene() {
            @Override
            protected void enter(EngineContext ctx) {
                throw new IllegalStateException("broken level");
            }
        }, Transition.fade(0.1f));
        assertThatThrownBy(() -> framesUntil(() -> false)).hasMessage("broken level");
        assertThat(scenes.inTransition()).isFalse();
        assertThat(scenes.coverAlpha()).isZero();
    }

    @Test
    void transitionsRejectBadDurations() {
        assertThatThrownBy(() -> Transition.fade(-1f)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A transition needs a finite duration >= 0 (was -1.0)");
        assertThatThrownBy(() -> Transition.fade(Float.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transition.fade(Float.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Transition.fade(1f, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Transition.cut().seconds()).isZero();
        Color white = new Color(Color.WHITE);
        Transition t = Transition.fade(1f, white);
        white.set(Color.RED);
        assertThat(t.color()).isEqualTo(Color.WHITE);
        t.color().set(Color.BLUE);
        assertThat(t.color()).as("a copy").isEqualTo(Color.WHITE);
    }

    @Test
    void pressesDoNotReachTheUiWhileTheCoverFadesOut() {
        UiInputRouter router = new UiInputRouter(scenes);
        start("a", null);
        assertThat(router.touchDown(1, 1, 0, 0)).isFalse();
        scenes.replace(new Recorder("b", null), Transition.fade(0.5f));
        frame();
        assertThat(router.touchDown(1, 1, 0, 0)).as("swallowed").isTrue();
        assertThat(router.keyDown(62)).isTrue();
        assertThat(router.keyTyped('x')).isTrue();
        assertThat(router.touchUp(1, 1, 0, 0)).as("releases pass").isFalse();
        assertThat(router.keyUp(62)).isFalse();
    }

    @Test
    void theDefaultLoadingScreenDrawsAProgressBar() {
        List<float[]> draws = new ArrayList<>();
        Color color = new Color(Color.WHITE);
        Batch batch = (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(), new Class<?>[] {Batch.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getColor" -> {
                            return color;
                        }
                        case "setColor" -> {
                            if (args.length == 1) {
                                color.set((Color) args[0]);
                            } else {
                                color.set((float) args[0], (float) args[1], (float) args[2], (float) args[3]);
                            }
                        }
                        case "draw" -> draws.add(new float[] {(float) args[1], (float) args[2], (float) args[3],
                                (float) args[4], color.r});
                        default -> { }
                    }
                    return null;
                });
        LoadingScreen.bar().draw(batch, new TextureRegion(), 1000f, 600f, 0.5f);
        assertThat(draws).hasSize(3);
        float[] fill = draws.get(2);
        assertThat(fill).containsExactly(300f, 295f, 200f, 10f, 1f);
        assertThat(draws.get(1)[2]).as("the whole bar").isEqualTo(400f);
        assertThat(color).as("the batch colour is restored").isEqualTo(Color.WHITE);
    }
}
