package dev.ember2d.assets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.headless.HeadlessFiles;
import com.badlogic.gdx.backends.headless.mock.audio.MockAudio;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.assets.AssetLoadException;
import dev.ember2d.assets.AsyncLoad;
import dev.ember2d.core.LogConfig;
import dev.ember2d.core.LogLevel;
import dev.ember2d.core.LogSink;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Loads real files (test resources under {@code assets-test/}) without a window: libGDX headless files and mock
 * audio, plus do-nothing stand-ins for OpenGL and the application built with {@link Proxy} (test code only).
 */
class GdxAssetsTest {

    /** Test assets live under assets-test/; dependencies (atlas and font pages) arrive already prefixed. */
    private static final FileHandleResolver RESOLVER =
            path -> Gdx.files.internal(path.startsWith("assets-test/") ? path : "assets-test/" + path);

    private GdxAssets assets;
    private final List<String> warnings = new ArrayList<>();
    private final LogSink sink = (level, category, message, error) -> {
        if (level == LogLevel.WARN) {
            warnings.add(message);
        }
    };

    @BeforeAll
    static void headlessLibgdx() {
        GdxNativesLoader.load();
        Gdx.files = new HeadlessFiles();
        Gdx.audio = new MockAudio();
        Gdx.graphics = new MockGraphics();
        GL20 gl = stub(GL20.class);
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.app = stub(Application.class);
    }

    @AfterAll
    static void clearLibgdx() {
        Gdx.files = null;
        Gdx.audio = null;
        Gdx.graphics = null;
        Gdx.gl = null;
        Gdx.gl20 = null;
        Gdx.app = null;
    }

    /** An interface implementation whose methods do nothing and return defaults. */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (r == boolean.class) {
                return false;
            } else if (r == int.class) {
                return 0;
            } else if (r == long.class) {
                return 0L;
            } else if (r == float.class) {
                return 0f;
            } else if (r == double.class) {
                return 0d;
            } else if (r == String.class) {
                return "";
            } else if (r == Application.ApplicationType.class) {
                return Application.ApplicationType.HeadlessDesktop;
            }
            return null;
        });
    }

    @BeforeEach
    void setUp() {
        LogConfig.resetToDefaults();
        LogConfig.removeSink(LogConfig.consoleSink());
        LogConfig.addSink(sink);
        String json = RESOLVER.resolve("manifest.json").readString("UTF-8");
        assets = new GdxAssets(AssetManifest.parse(json, "assets-test/manifest.json"), RESOLVER);
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
        LogConfig.resetToDefaults();
    }

    @Test
    void loadsTexturesAtlasesAndFontsById() {
        AsyncLoad load = assets.loadGroup("global");
        load.finish();

        assertThat(load.isDone()).isTrue();
        assertThat(load.progress()).isEqualTo(1f);
        assertThat(assets.get("tex.square", Texture.class).getWidth()).isEqualTo(4);
        assertThat(assets.get("atlas.sheet", TextureAtlas.class).getRegions()).hasSize(2);
        assertThat(assets.get("font.test", BitmapFont.class).getData().getGlyph('A')).isNotNull();
        assertThat(assets.isLoaded("tex.square")).isTrue();
        assertThat(assets.contains("tex.square")).isTrue();
        assertThat(assets.contains("tex.nothing")).isFalse();
    }

    @Test
    void loadsJsonSoundAndMusic() {
        assets.loadGroup("level1").finish();

        assertThat(assets.get("data.player", JsonValue.class).getFloat("speed")).isEqualTo(4.5f);
        assertThat(assets.get("sfx.blip", Sound.class)).isNotNull();
        assertThat(assets.get("music.theme", Music.class)).isNotNull();
    }

    @Test
    void progressIsReportedWhileLoading() {
        AsyncLoad load = assets.loadGroup("level1");

        assertThat(load.isDone()).isFalse();
        assertThat(load.progress()).isLessThan(1f);
        while (!load.isDone()) {
            assets.update();
        }
        assertThat(load.progress()).isEqualTo(1f);
        assertThat(load.group()).isEqualTo("level1");
        assertThat(assets.loadGroup("level1")).as("loading again returns the same load").isSameAs(load);
    }

    @Test
    void regionsFromAtlasAndTextureAreCached() {
        assets.loadGroup("global").finish();

        TextureRegion red = assets.region("atlas.sheet/red");
        TextureRegion whole = assets.region("tex.square");

        assertThat(red.getRegionWidth()).isEqualTo(2);
        assertThat(red.getRegionX()).isZero();
        assertThat(assets.region("atlas.sheet/blue").getRegionX()).isEqualTo(2);
        assertThat(whole.getRegionWidth()).isEqualTo(4);
        assertThat(assets.region("atlas.sheet/red")).isSameAs(red);
    }

    @Test
    void missingRegionIsMagentaWithOneWarning() {
        assets.loadGroup("global").finish();

        TextureRegion missing = assets.region("atlas.sheet/green");
        assets.region("atlas.sheet/green");

        assertThat(missing.getRegionWidth()).isEqualTo(16);
        assertThat(warnings).singleElement().asString().contains("\"green\" not found in atlas \"atlas.sheet\"");
    }

    @Test
    void sharedAssetStaysLoadedUntilAllItsGroupsAreUnloaded() {
        assets.loadGroup("global").finish();
        assets.loadGroup("level1").finish();

        assets.unloadGroup("level1");
        assertThat(assets.isLoaded("atlas.sheet")).as("still needed by global").isTrue();
        assertThat(assets.isLoaded("data.player")).isFalse();

        assets.unloadGroup("global");
        assertThat(assets.isLoaded("atlas.sheet")).isFalse();
        assets.unloadGroup("global");   // no-op
    }

    @Test
    void missingOptionalAssetsUseFallbacksWithAWarningEach() {
        assets.loadGroup("extras").finish();

        Sound sound = assets.get("sfx.missing", Sound.class);
        assertThat(sound.play()).isEqualTo(-1L);
        assertThat(assets.get("music.missing", Music.class).isPlaying()).isFalse();
        assertThat(assets.get("tex.missing", Texture.class).getWidth()).isEqualTo(16);
        assertThat(assets.region("atlas.missing/anything").getRegionWidth()).isEqualTo(16);
        assertThat(assets.get("data.missing", JsonValue.class).isObject()).isTrue();
        assertThat(assets.get("font.missing", BitmapFont.class)).isNotNull();
        assertThat(assets.isLoaded("sfx.missing")).isTrue();
        assertThat(warnings).hasSize(6).allMatch(w -> w.startsWith("Optional asset"));
    }

    @Test
    void missingRequiredAssetsFailTheGroupListingEveryFile() {
        AssetLoadException e = catchThrowableOfType(AssetLoadException.class, () -> assets.loadGroup("broken"));

        assertThat(e.getMessage()).contains("Cannot load asset group \"broken\"");
        assertThat(e.problems()).containsExactly(
                "\"tex.required\": gone.png does not exist",
                "\"sfx.required\": gone.wav does not exist");
    }

    @Test
    void clearErrorsForUnknownIdsWrongTypesUnloadedAssetsAndUnknownGroups() {
        assertThatThrownBy(() -> assets.get("tex.sqare", Texture.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown asset id \"tex.sqare\"")
                .hasMessageContaining("closest: [tex.square");
        assertThatThrownBy(() -> assets.get("tex.square", Sound.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("is a Texture, not a Sound");
        assertThatThrownBy(() -> assets.get("data.player", JsonValue.class))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("load its group first: \"level1\"");
        assertThatThrownBy(() -> assets.loadGroup("level9"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("known groups: [global, level1, extras, broken]");
    }
}
