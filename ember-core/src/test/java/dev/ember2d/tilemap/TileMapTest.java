package dev.ember2d.tilemap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTile;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.AssetLoadException;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.collision.TileGrid;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.Phase;
import dev.ember2d.core.Time;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.RenderMode;
import dev.ember2d.render.RenderTestKit;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.render.internal.GdxRenderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tiled maps through the asset service (F-009, I-110), headless. Sample maps: {@code assets-test/maps/}. */
class TileMapTest {

    private static final FileHandleResolver RESOLVER =
            path -> Gdx.files.internal(path.startsWith("assets-test/") ? path : "assets-test/" + path);
    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "map.tmx", "path": "maps/test-map.tmx", "type": "map", "group": "maps" },
              { "id": "map.tmj", "path": "maps/test-map.tmj", "type": "map", "group": "maps" },
              { "id": "map.iso", "path": "maps/iso.tmx", "type": "map", "group": "iso" },
              { "id": "map.infinite", "path": "maps/infinite.tmx", "type": "map", "group": "infinite" },
              { "id": "map.class", "path": "maps/class-prop.tmx", "type": "map", "group": "class" } ] }
            """;

    private GdxAssets assets;
    private final List<String> warnings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink((level, category, message, error) -> {
            if (level == LogLevel.WARN) {
                warnings.add(message);
            }
        });
        assets = new GdxAssets(AssetManifest.parse(MANIFEST, "assets-test/manifest.json"), RESOLVER);
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
        LogConfig.resetToDefaults();
        HeadlessGdx.clear();
    }

    private TileMap load(String id, float pixelsPerUnit) {
        assets.loadGroup("maps").finish();
        return TileMap.load(assets, id, pixelsPerUnit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"map.tmx", "map.tmj"})
    void loadsTmxAndTmjAlike(String id) {
        TileMap map = load(id, 16f);

        assertThat(map.width()).isEqualTo(10);
        assertThat(map.height()).isEqualTo(6);
        assertThat(map.tileWidth()).isEqualTo(1f);
        assertThat(map.worldWidth()).isEqualTo(10f);
        assertThat(map.worldHeight()).isEqualTo(6f);
        assertThat(map.properties().get("music")).isEqualTo("cave");
        assertThat(map.layerNames()).containsExactly("sky", "ground", "collision", "leaves", "hidden", "entities");
        assertThat(map.tile("ground", 0, 0)).isNotNull();
        assertThat(map.tile("ground", 3, 5)).isNull();
        assertThat(map.tile("ground", 10, 0)).as("outside").isNull();
        assertThat(map.source()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"map.tmx", "map.tmj"})
    void collisionGridFromPropertiesAndTheCollisionLayer(String id) {
        TileGrid grid = load(id, 16f).collision();

        assertThat(grid.width()).isEqualTo(10);
        assertThat(grid.height()).isEqualTo(6);
        assertThat(grid.cellWidth()).isEqualTo(1f);
        for (int x = 0; x < 10; x++) {
            assertThat(grid.solid(x, 0)).as("floor %d", x).isTrue();
        }
        assertThat(grid.oneWay(2, 2)).isTrue();
        assertThat(grid.oneWay(4, 2)).isTrue();
        assertThat(grid.solid(3, 2)).isFalse();
        assertThat(grid.solid(9, 3)).as("one-way tile on the collision layer is solid").isTrue();
        assertThat(grid.oneWay(9, 3)).isFalse();
        assertThat(grid.solid(8, 5)).as("hidden layers still collide").isTrue();
        assertThat(grid.solid(0, 5)).as("\"solid\" of the wrong type is ignored").isFalse();
        assertThat(grid.solidCount()).isEqualTo(12);
        assertThat(grid.oneWayCount()).isEqualTo(3);
        assertThat(warnings).anyMatch(w -> w.contains("tile 4 has property \"solid\" of type String"));
    }

    @Test
    void geometryFollowsPixelsPerUnitWithYUp() {
        TileMap map = load("map.tmx", 8f);

        assertThat(map.tileWidth()).isEqualTo(2f);
        assertThat(map.tileX(3.9f)).isEqualTo(1);
        assertThat(map.tileX(4f)).isEqualTo(2);
        assertThat(map.tileY(-0.1f)).isEqualTo(-1);
        assertThat(map.tileCenter(2, 1, new Vector2())).isEqualTo(new Vector2(5f, 3f));
        assertThat(map.bounds(new Rectangle())).isEqualTo(new Rectangle(0f, 0f, 20f, 12f));
        assertThat(map.collision().cellX(3.9f)).isEqualTo(1);
        assertThat(map.collision().solid(0, 0)).as("row 0 is the bottom row").isTrue();
    }

    @Test
    void unknownOrNonTileLayersAreRejected() {
        TileMap map = load("map.tmx", 16f);

        assertThatThrownBy(() -> map.tile("water", 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no tile layer \"water\"").hasMessageContaining("ground");
        assertThatThrownBy(() -> map.tile("entities", 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void animatedTilesFollowGameTime() {
        TileMap map = load("map.tmx", 16f);
        TiledMapTile tile = map.tile("ground", 7, 1);

        assertThat(tile).isInstanceOf(GameTimeAnimatedTile.class);
        assertThat(map.tile("leaves", 5, 4)).as("same tile everywhere").isSameAs(tile);
        map.clock().set(0.0);
        assertThat(tile.getTextureRegion().getRegionX()).isEqualTo(32);
        map.clock().set(0.15);
        assertThat(tile.getTextureRegion().getRegionX()).isEqualTo(48);
        map.clock().set(0.25);
        assertThat(tile.getTextureRegion().getRegionX()).isEqualTo(32);
        assertThat(TileMap.load(assets, "map.tmx", 16f).clock()).as("wrapping twice keeps one clock")
                .isSameAs(map.clock());
    }

    @Test
    void rejectsUnsupportedMaps() {
        assertThat(catchThrowable(() -> assets.loadGroup("iso").finish()))
                .hasStackTraceContaining("orientation \"isometric\" is not supported");
        assertThat(catchThrowable(() -> assets.loadGroup("infinite").finish()))
                .hasStackTraceContaining("infinite maps are not supported");
        assertThatThrownBy(() -> TileMap.of(new TiledMap(), "empty", 16f)).isInstanceOf(TileMapException.class)
                .hasMessage("Tiled map empty: not a Tiled map (no orientation)");
    }

    @Test
    void classPropertiesNeedTheTiledProject() {
        assertThat(catchThrowable(() -> assets.loadGroup("class").finish()))
                .hasStackTraceContaining("set EngineConfig.tiledProject");
    }

    @Test
    void classPropertiesLoadWithTheTiledProject() {
        assets.setTiledProject("maps/test.tiled-project");
        assets.loadGroup("class").finish();

        TiledMap map = assets.get("map.class", TiledMap.class);
        MapProperties stats = map.getLayers().get("entities").getObjects().get("door").getProperties()
                .get("stats", MapProperties.class);
        assertThat(stats.get("cost")).isEqualTo(3);
        assertThat(stats.get("locked")).as("default from the project file").isEqualTo(true);
    }

    @Test
    void missingTiledProjectFileFailsEarly() {
        assertThatThrownBy(() -> assets.setTiledProject("maps/nope.tiled-project"))
                .isInstanceOf(AssetLoadException.class).hasMessageContaining("EngineConfig.tiledProject");
    }

    @Test
    void manifestMapsMustBeTmxOrTmj() {
        assertThat(catchThrowable(() -> AssetManifest.parse("""
                { "version": 1, "assets": [ { "id": "m", "path": "maps/test-tiles.png", "type": "map", "group": "g" } ] }
                """, "m.json"))).hasMessageContaining("a map must be a .tmx or .tmj file");
    }

    @Test
    void mapLayersAreDrawnInsideTheRenderLayers() {
        TileMap map = load("map.tmx", 16f);
        Texture tiles = map.tile("ground", 0, 0).getTextureRegion().getTexture();
        RenderTestKit kit = new RenderTestKit();
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);
        renderer.setLayers(new RenderLayer("background", SortMode.NONE), new RenderLayer("world", SortMode.Z),
                new RenderLayer("foreground", SortMode.NONE));
        renderer.worldCamera().viewportWidth = 20f;   // the whole 10x6 map is in view
        renderer.worldCamera().viewportHeight = 12f;
        renderer.worldCamera().position.set(5f, 3f, 0f);
        renderer.worldCamera().update();
        World world = new World(false);
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, renderer, null, kit.time);
        world.addSystem(sprites);
        double[] gameTime = {0.15};
        new TileMapRenderer(map, renderer, fakeTime(gameTime)).attachTo(sprites);
        int player = world.create();
        world.add(player, Transform.class).teleport(4f, 1f);
        world.add(player, new Sprite()).region = new TextureRegion(tiles, 0, 0, 16, 16);
        int hidden = world.create();   // drawn last in "world" with alpha 0: must not fade the foreground tiles
        world.add(hidden, Transform.class).teleport(6f, 1f);
        Sprite ghost = world.add(hidden, new Sprite());
        ghost.region = new TextureRegion(tiles, 0, 0, 16, 16);
        ghost.tint.set(1f, 1f, 1f, 0f);
        ghost.z = 5f;

        kit.calls.clear();
        world.runPhase(Phase.RENDER, kit.time);

        List<String> order = new ArrayList<>();
        for (RenderTestKit.Call call : kit.calls("draw")) {
            String what = call.args().length == 4 ? (call.args()[0] == tiles ? "tiles" : "image") : "sprite";
            if (order.isEmpty() || !order.get(order.size() - 1).startsWith(what)) {
                order.add(what + " x1");
            } else {
                String last = order.remove(order.size() - 1);
                order.add(what + " x" + (Integer.parseInt(last.substring(last.indexOf('x') + 1)) + 1));
            }
        }
        assertThat(order).as("sky (background), ground (world), sprite (world), leaves (foreground); "
                + "collision and hidden layers not drawn").containsExactly("image x1", "tiles x15", "sprite x2",
                "tiles x1");
        List<RenderTestKit.Call> draws = kit.calls("draw");
        float[] lastVertices = (float[]) draws.get(draws.size() - 1).args()[1];
        assertThat(lastVertices[2]).as("foreground tiles drawn opaque after a transparent sprite")
                .isEqualTo(com.badlogic.gdx.graphics.Color.WHITE.toFloatBits());
        assertThat(kit.calls("setProjectionMatrix")).as("the tile renderer leaves the batch projection alone")
                .isEmpty();
        assertThat(map.clock().millis).as("animated tiles on game time").isEqualTo(150L);
    }

    @Test
    void unknownRenderLayerFallsBackToWorldWithAWarning() {
        TileMap map = load("map.tmx", 16f);
        RenderTestKit kit = new RenderTestKit();
        GdxRenderer renderer = kit.renderer(RenderMode.PIXEL_PERFECT);   // only "world"
        renderer.worldCamera().viewportWidth = 20f;   // the whole 10x6 map is in view
        renderer.worldCamera().viewportHeight = 12f;
        renderer.worldCamera().position.set(5f, 3f, 0f);
        renderer.worldCamera().update();
        TileMapRenderer tiles = new TileMapRenderer(map, renderer, fakeTime(new double[] {0}));

        tiles.drawLayer("world", kit.batch);

        assertThat(kit.calls("draw")).hasSize(1 + 15 + 1);
        assertThat(warnings).contains(
                "Tiled map map.tmx: unknown render layer \"background\"; drawing on \"world\"",
                "Tiled map map.tmx: unknown render layer \"foreground\"; drawing on \"world\"");
    }

    /** Game time that only advances when the test says so. */
    private static Time fakeTime(double[] elapsed) {
        TimeState real = new TimeState(1f / 60f);
        return (Time) java.lang.reflect.Proxy.newProxyInstance(Time.class.getClassLoader(), new Class<?>[] {Time.class},
                (proxy, method, args) -> method.getName().equals("elapsed") ? elapsed[0] : method.invoke(real, args));
    }
}
