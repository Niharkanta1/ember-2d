package dev.ember2d.assets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.AssetLoadException;
import dev.ember2d.assets.AssetReloaded;
import dev.ember2d.core.LogConfig;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Hot reload in the asset service (F-008, I-150), on copies of the test assets in a temporary folder. */
class HotReloadAssetsTest {

    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "data", "path": "data.json", "type": "json", "group": ["a", "b"] },
              { "id": "tex", "path": "square.png", "type": "texture", "group": "a" },
              { "id": "sheet", "path": "sheet.atlas", "type": "atlas", "group": "a" },
              { "id": "map", "path": "maps/test-map.tmx", "type": "map", "group": "a" },
              { "id": "late", "path": "later.json", "type": "json", "group": "later" } ] }
            """;

    @TempDir
    Path dir;
    private GdxAssets assets;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        for (String file : List.of("data.json", "square.png", "sheet.atlas", "sheet.png", "maps/test-map.tmx",
                "maps/test-tiles.png", "maps/test-sky.png", "maps/icons.tsx")) {
            Gdx.files.classpath("assets-test/" + file).copyTo(file(file));
        }
        write("later.json", "{}");
        write("manifest.json", MANIFEST);
        assets = GdxAssets.load("manifest.json", dir.toString());
        assets.loadGroup("a").finish();
        assets.loadGroup("b").finish();
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
        LogConfig.resetToDefaults();
        HeadlessGdx.clear();
    }

    private com.badlogic.gdx.files.FileHandle file(String path) {
        return Gdx.files.absolute(dir.resolve(path).toString());
    }

    private void write(String path, String text) {
        file(path).writeString(text, false, "UTF-8");
    }

    private List<String> ids(String changedPath) {
        return assets.entriesForChange(changedPath).stream().map(AssetManifest.Entry::id).toList();
    }

    @Test
    void readsFromTheFolderAndMapsChangedFilesToLoadedEntries() {
        assertThat(assets.get("data", JsonValue.class)).isNotNull();
        assertThat(ids("data.json")).containsExactly("data");
        assertThat(ids("square.png")).containsExactly("tex");
        assertThat(ids("sheet.png")).as("atlas page").containsExactly("sheet");
        assertThat(ids("maps/test-tiles.png")).as("tileset image").containsExactly("map");
        assertThat(ids("maps/icons.tsx")).as("every loaded map").containsExactly("map");
        assertThat(ids("later.json")).as("group not loaded").isEmpty();
        assertThat(ids("readme.txt")).isEmpty();
    }

    @Test
    void reloadReplacesEachTypeAndCountsGenerations() {
        JsonValue oldData = assets.get("data", JsonValue.class);
        Texture oldTex = assets.get("tex", Texture.class);
        TextureAtlas oldSheet = assets.get("sheet", TextureAtlas.class);
        TiledMap oldMap = assets.get("map", TiledMap.class);
        TextureRegion oldRegion = assets.region("sheet/red");
        assertThat(assets.generation()).isZero();

        write("data.json", "{ \"hello\": \"again\" }");
        List<AssetReloaded> events = assets.reload(List.of(entry("data"), entry("tex"), entry("sheet"), entry("map")));

        assertThat(events).containsExactly(new AssetReloaded("data", "json", "data.json"),
                new AssetReloaded("tex", "texture", "square.png"), new AssetReloaded("sheet", "atlas", "sheet.atlas"),
                new AssetReloaded("map", "map", "maps/test-map.tmx"));
        assertThat(assets.generation()).isEqualTo(1);
        assertThat(assets.get("data", JsonValue.class)).isNotSameAs(oldData);
        assertThat(assets.get("data", JsonValue.class).getString("hello")).isEqualTo("again");
        assertThat(assets.get("tex", Texture.class)).isNotSameAs(oldTex);
        assertThat(assets.get("sheet", TextureAtlas.class)).isNotSameAs(oldSheet);
        assertThat(assets.get("map", TiledMap.class)).isNotSameAs(oldMap);
        assertThat(assets.region("sheet/red")).as("region cache cleared").isNotSameAs(oldRegion);
    }

    @Test
    void aReloadedAssetKeepsItsGroups() {
        assets.reload(List.of(entry("data")));

        assets.unloadGroup("a");
        assertThat(assets.isLoaded("data")).as("still in group b").isTrue();
        assets.unloadGroup("b");
        assertThat(assets.isLoaded("data")).isFalse();
    }

    @Test
    void aBrokenFileKeepsTheOldVersion() {
        JsonValue oldData = assets.get("data", JsonValue.class);
        TiledMap oldMap = assets.get("map", TiledMap.class);
        write("data.json", "{ broken");
        write("maps/test-map.tmx", "<map version=");

        List<AssetReloaded> events = assets.reload(List.of(entry("data"), entry("map")));

        assertThat(events).isEmpty();
        assertThat(assets.generation()).isZero();
        assertThat(assets.get("data", JsonValue.class)).isSameAs(oldData);
        assertThat(assets.get("map", TiledMap.class)).isSameAs(oldMap);
        assertThat(assets.isLoaded("map")).isTrue();
    }

    @Test
    void aMissingFolderFailsWithThePath() {
        AssetLoadException e = catchThrowableOfType(AssetLoadException.class,
                () -> GdxAssets.load("manifest.json", dir.resolve("nope").toString()));
        assertThat(e.problems()).singleElement().asString().contains("nope").contains("EngineConfig.assetsDir");
    }

    private AssetManifest.Entry entry(String id) {
        return assets.manifest().find(id);
    }
}
