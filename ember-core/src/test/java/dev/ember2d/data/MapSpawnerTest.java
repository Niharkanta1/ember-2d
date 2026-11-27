package dev.ember2d.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.LogConfig;
import dev.ember2d.data.TestComponents.Coin;
import dev.ember2d.data.TestComponents.Health;
import dev.ember2d.data.TestComponents.Walker;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.internal.DefaultEventBus;
import dev.ember2d.render.Sprite;
import dev.ember2d.tilemap.TileMap;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Placing prefabs from Tiled objects (F-009, F-010), through the real map loader. Maps: assets-test/maps/. */
class MapSpawnerTest {

    private static final FileHandleResolver RESOLVER =
            path -> Gdx.files.internal(path.startsWith("assets-test/") ? path : "assets-test/" + path);
    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "map.placement", "path": "maps/placement.tmx", "type": "map", "group": "level" },
              { "id": "map.errors", "path": "maps/placement-errors.tmx", "type": "map", "group": "errors" },
              { "id": "map.old.tmx", "path": "maps/old-class.tmx", "type": "map", "group": "old-tmx" },
              { "id": "map.old.tmj", "path": "maps/old-class.tmj", "type": "map", "group": "old-tmj" },
              { "id": "walker", "path": "prefabs/walker.json", "type": "prefab", "group": ["level", "errors"] },
              { "id": "hero", "path": "prefabs/hero.json", "type": "prefab", "group": ["level", "errors"] },
              { "id": "coin", "path": "prefabs/coin.json", "type": "prefab", "group": "level" },
              { "id": "double", "path": "prefabs/double.json", "type": "prefab", "group": "errors" } ] }
            """;

    private GdxAssets assets;
    private PrefabLibrary prefabs;
    private final World world = new World(false);
    private final DefaultEventBus events = new DefaultEventBus(false);
    private final List<String> spawnedOrder = new ArrayList<>();

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        assets = new GdxAssets(AssetManifest.parse(MANIFEST, "assets-test/manifest.json"), RESOLVER);
        assets.setTiledProject("maps/test.tiled-project");
        ComponentRegistry registry = new ComponentRegistry();
        new TestComponents.Setup().register(registry);
        prefabs = new PrefabLibrary(registry, assets, events);
        events.onGlobal(EntitySpawned.class, e -> spawnedOrder.add(world.name(e.entity()) + ":" + e.prefab()));
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
        LogConfig.resetToDefaults();
        HeadlessGdx.clear();
    }

    private TileMap map(String group, String id) {
        assets.loadGroup(group).finish();
        return TileMap.load(assets, id, 16f);
    }

    @Test
    void spawnsClassedObjectsWithTemplatesOverridesAndReferences() {
        assertPlacement(map("level", "map.placement"));
    }

    /**
     * I-245: with a project file from the type generator, libGDX writes every class member into the objects as defaults;
     * the entities must come out exactly the same.
     */
    @Test
    void aGeneratedProjectSpawnsTheSameEntities(@TempDir File dir) throws IOException {
        ComponentRegistry registry = new ComponentRegistry();
        new TestComponents.Setup().register(registry);
        Map<String, JsonValue> files = new LinkedHashMap<>();
        for (String id : List.of("walker", "hero", "coin")) {
            files.put(id, new JsonReader().parse(Gdx.files.internal("assets-test/prefabs/" + id + ".json")));
        }
        String existing = Gdx.files.internal("assets-test/maps/test.tiled-project").readString("UTF-8");
        File project = new File(dir, "generated.tiled-project");
        Files.write(project.toPath(), TiledTypes.update(registry, files, existing, new ArrayList<>())
                .getBytes(StandardCharsets.UTF_8));

        assets.dispose();
        FileHandleResolver resolver = path -> path.endsWith("generated.tiled-project")
                ? Gdx.files.absolute(project.getAbsolutePath()) : RESOLVER.resolve(path);
        assets = new GdxAssets(AssetManifest.parse(MANIFEST, "assets-test/manifest.json"), resolver);
        assets.setTiledProject("maps/generated.tiled-project");
        prefabs = new PrefabLibrary(registry, assets, events);
        assertPlacement(map("level", "map.placement"));
    }

    private void assertPlacement(TileMap map) {
        IntArray entities = new MapSpawner(prefabs).spawn(world, map);

        assertThat(entities.size).isEqualTo(4);
        assertThat(spawnedOrder).as("published after both passes, in map order")
                .containsExactly("walker_1:walker", "walker_2:walker", "hero:hero", "coin:coin");
        int walker1 = world.findByName("walker_1");
        int walker2 = world.findByName("walker_2");
        int hero = world.findByName("hero");
        int coin = world.findByName("coin");
        assertThat(world.findByName("patrol_path")).as("objects without a class are not entities")
                .isEqualTo(World.NONE);

        Walker w1 = world.get(walker1, Walker.class);
        assertThat(w1.speed).as("instance override beats the template's 2").isEqualTo(5f);
        assertThat(w1.target.entity(world)).as("object reference -> EntityRef").isEqualTo(hero);
        assertThat(world.get(walker1, Transform.class).position).as("bottom-centre of the tile object")
                .isEqualTo(new Vector2(2.5f, 1f));
        assertThat(world.get(walker1, Sprite.class).origin).as("prefab sprite kept").isEqualTo(new Vector2(8f, 0f));

        Walker w2 = world.get(walker2, Walker.class);
        assertThat(w2.speed).as("from the template").isEqualTo(2f);
        assertThat(w2.path).as("polyline in world units").containsExactly(1f, 3.5f, 3f, 3.5f);
        assertThat(w2.home).as("point position").isEqualTo(new Vector2(3f, 2f));
        assertThat(w2.zone).as("rectangle bounds").isEqualTo(new Rectangle(0f, 5f, 2f, 1f));

        assertThat(world.get(hero, Health.class).max).as("class property named after the component").isEqualTo(9);
        Transform heroT = world.get(hero, Transform.class);
        assertThat(heroT.position).isEqualTo(new Vector2(5.5f, 1f));
        assertThat(heroT.rotation).as("Tiled clockwise -> counter-clockwise").isEqualTo(-90f);
        assertThat(heroT.scale).as("prefab Transform values").isEqualTo(new Vector2(2f, 2f));
        assertThat(world.has(hero, Sprite.class)).as("not a tile object: no sprite added").isFalse();

        assertThat(world.get(coin, Coin.class).value).isEqualTo(5);
        Sprite coinSprite = world.get(coin, Sprite.class);
        assertThat(coinSprite.region.getRegionX()).as("tile image used when the prefab has no sprite").isEqualTo(16);
        assertThat(coinSprite.origin).isEqualTo(new Vector2(8f, 0f));
        assertThat(coinSprite.flipX).as("flipped tile object").isTrue();
        assertThat(world.get(coin, Transform.class).position).isEqualTo(new Vector2(8.5f, 0f));
    }

    @Test
    void reportsEveryProblemAndSpawnsNothing() {
        PrefabException e = catchThrowableOfType(PrefabException.class,
                () -> new MapSpawner(prefabs).spawn(world, map("errors", "map.errors")));

        String m = "Tiled map map.errors: ";
        assertThat(e).hasMessageStartingWith("Cannot place entities from Tiled map map.errors");
        assertThat(e.problems()).containsExactlyInAnyOrder(
                m + "object \"ghost\" (id 1): Unknown prefab \"nosuch\": not in the asset manifest",
                m + "object \"typo\" (id 2): property \"speeed\": no component of prefab \"walker\" has a field "
                        + "\"speeed\" (components [Walker, Sprite, Transform])",
                m + "object \"typo\" (id 2): property \"Walker.speed\": Walker.speed: expected a number for float, got "
                        + "string \"fast\"",
                m + "object \"typo\" (id 2): property \"target\": references object \"mark\" (id 5), which has no class "
                        + "and is not an entity",
                m + "object \"twins\" (id 3): property \"speed\": field \"speed\" is ambiguous; write one of "
                        + "[Walker.speed, Mover.speed]",
                m + "object \"nested\" (id 4): property \"Stats\": a class property must be named after a component of "
                        + "prefab \"hero\" [Health, Transform] (its members set that component's fields)");
        assertThat(world.entityCount()).isZero();
        assertThat(spawnedOrder).isEmpty();
    }

    @Test
    void tiled19ClassAttributesAreRejected() {
        assertThat(catchThrowable(() -> assets.loadGroup("old-tmx").finish()))
                .hasStackTraceContaining("object(s) \"old_walker\" (id 1) use the Tiled 1.9 \"class\" attribute")
                .hasStackTraceContaining("Tiled 1.10 or later");
        assertThat(catchThrowable(() -> assets.loadGroup("old-tmj").finish()))
                .hasStackTraceContaining("\"old_walker\" (id 1) use the Tiled 1.9 \"class\" attribute");
    }
}
