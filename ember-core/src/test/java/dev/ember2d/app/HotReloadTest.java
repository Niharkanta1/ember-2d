package dev.ember2d.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.AssetReloaded;
import dev.ember2d.assets.AssetWatcher;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.internal.GameLoop;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.internal.DefaultInput;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The hot reload driver (F-008, I-150) with real assets in a temporary folder and a scripted watcher. */
class HotReloadTest {

    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "map", "path": "maps/test-map.tmx", "type": "map", "group": "global" },
              { "id": "data", "path": "data.json", "type": "json", "group": "global" },
              { "id": "input", "path": "input.json", "type": "json", "group": "global" } ] }
            """;
    private static final String BINDINGS = """
            { "version": 1, "contexts": { "gameplay": { "actions": { "jump": ["SPACE"] } } } }
            """;

    /** Hands over the paths given to {@link #change}. */
    static final class ScriptedWatcher implements AssetWatcher {
        final List<String> changes = new ArrayList<>();
        int drains;
        boolean closed;

        void change(String... paths) {
            changes.addAll(List.of(paths));
        }

        @Override
        public void drainChanges(Consumer<String> changedPath) {
            drains++;
            changes.forEach(changedPath);
            changes.clear();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Counts entries; optionally handles reloads itself. */
    static final class LevelScene extends Scene {
        final List<String> log = new ArrayList<>();
        boolean handle;
        World worldSeen;

        @Override
        protected void enter(EngineContext context) {
            log.add("enter");
            worldSeen = world();
            world().add(world().create(), Transform.class);
            context.events().on(AssetReloaded.class, e -> log.add("event " + e.id()));
            context.events().on(dev.ember2d.assets.AssetReloadFailed.class, e -> log.add("failed " + e.id()));
        }

        @Override
        protected boolean onAssetReloaded(AssetReloaded event) {
            log.add("hook " + event.id());
            return handle;
        }
    }

    @TempDir
    Path dir;
    private final ScriptedWatcher watcher = new ScriptedWatcher();
    private final LevelScene scene = new LevelScene();
    private GdxAssets assets;
    private EngineContext.Builder builder;
    private EngineContext context;
    private GameLoop loop;
    private HotReload hotReload;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        for (String f : List.of("data.json", "maps/test-map.tmx", "maps/test-tiles.png", "maps/test-sky.png",
                "maps/icons.tsx")) {
            Gdx.files.classpath("assets-test/" + f).copyTo(file(f));
        }
        file("input.json").writeString(BINDINGS, false, "UTF-8");
        file("manifest.json").writeString(MANIFEST, false, "UTF-8");
        assets = GdxAssets.load("manifest.json", dir.toString());
        assets.loadGroup("global").finish();
        EngineConfig config = new EngineConfig();
        config.devMode = true;
        builder = new EngineContext.Builder(config).assets(assets);
        context = builder.build();
        DefaultInput input = builder.inputState();
        input.setBindings(dev.ember2d.input.InputBindings.parse(assets.get("input",
                com.badlogic.gdx.utils.JsonValue.class), "input.json"));
        loop = new GameLoop(() -> 16_666_668L, builder.timeState(),
                new EnginePhaseRunner(context.timers(), context.tweens(), builder.eventBus(), input,
                        () -> hotReload.step(), () -> { }, () -> { }, context.scenes()::runPhase), 5);
        hotReload = new HotReload(watcher, assets, context.scenes(), builder.eventBus(), input, "input");
        context.scenes().push(scene);
        loop.frame();
    }

    @AfterEach
    void tearDown() {
        context.scenes().exitAll();
        assets.dispose();
        LogConfig.resetToDefaults();
        HeadlessGdx.clear();
    }

    private FileHandle file(String path) {
        return Gdx.files.absolute(dir.resolve(path).toString());
    }

    @Test
    void aChangedMapRestartsTheSceneWithAFreshWorld() {
        World before = scene.worldSeen;
        watcher.change("maps/test-map.tmx");

        loop.frame();   // reload, hook, restart requested
        loop.frame();   // restart applied

        assertThat(scene.log).containsExactly("enter", "hook map", "enter", "event map");
        assertThat(scene.worldSeen).isNotSameAs(before);
        assertThat(context.scenes().current()).isSameAs(scene);
    }

    @Test
    void aSceneThatHandlesTheReloadIsNotRestarted() {
        scene.handle = true;
        watcher.change("maps/test-tiles.png");   // a tileset image: the map reloads

        loop.frame();
        loop.frame();

        assertThat(scene.log).containsExactly("enter", "hook map", "event map");
    }

    @Test
    void otherAssetsAreReloadedWithoutARestart() {
        watcher.change("data.json", "readme.txt");

        loop.frame();
        loop.frame();

        assertThat(scene.log).containsExactly("enter", "hook data", "event data");
        assertThat(assets.generation()).isEqualTo(1);
    }

    @Test
    void inputBindingsAreAppliedAgainAndBrokenOnesKeepTheOldOnes() {
        file("input.json").writeString("""
                { "version": 1, "contexts": { "gameplay": { "actions": { "jump": ["SPACE"], "dash": ["X"] } } } }
                """, false, "UTF-8");
        watcher.change("input.json");
        loop.frame();
        assertThat(context.input().hasAction("dash")).isTrue();

        file("input.json").writeString("""
                { "version": 1, "contexts": { "gameplay": { "actions": { "jump": ["NOPE"] } } } }
                """, false, "UTF-8");
        watcher.change("input.json");
        loop.frame();
        assertThat(context.input().hasAction("dash")).as("old bindings kept").isTrue();
    }

    @Test
    void aManifestChangeOnlyWarns() {
        watcher.change("manifest.json");

        loop.frame();

        assertThat(assets.generation()).isZero();
        assertThat(scene.log).containsExactly("enter");
    }

    @Test
    void aRejectedFileIsPublished() {
        file("data.json").writeString("{ broken", false, "UTF-8");
        watcher.change("data.json");

        loop.frame();

        assertThat(scene.log).containsExactly("enter", "failed data");
        assertThat(assets.generation()).isZero();
    }
}
