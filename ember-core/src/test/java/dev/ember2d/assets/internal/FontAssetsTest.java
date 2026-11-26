package dev.ember2d.assets.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.AssetLoadException;
import dev.ember2d.core.LogConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** FreeType font entries (F-004, I-140), rendered from a real .ttf on the headless backend. */
class FontAssetsTest {

    private static final FileHandleResolver RESOLVER =
            path -> Gdx.files.internal(path.startsWith("assets-test/") ? path : "assets-test/" + path);
    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "font.small", "path": "fonts/PressStart2P-Regular.ttf", "type": "freetype", "group": "fonts",
                "font": { "size": 8, "mono": true } },
              { "id": "font.big", "path": "fonts/PressStart2P-Regular.ttf", "type": "freetype", "group": "fonts",
                "font": { "size": 16, "borderWidth": 1, "borderColor": "#000000", "color": "#ffcc00",
                          "characters": "ABC abc!", "markup": true } },
              { "id": "font.gone", "path": "fonts/missing.ttf", "type": "freetype", "group": "optional",
                "optional": true } ] }
            """;

    private GdxAssets assets;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        assets = new GdxAssets(AssetManifest.parse(MANIFEST, "assets-test/manifest.json"), RESOLVER);
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
        LogConfig.resetToDefaults();
        HeadlessGdx.clear();
    }

    @Test
    void oneTtfRenderedAtTwoSizesWithItsOptions() {
        assets.loadGroup("fonts").finish();

        BitmapFont small = assets.get("font.small", BitmapFont.class);
        BitmapFont big = assets.get("font.big", BitmapFont.class);
        assertThat(small).isNotSameAs(big);
        assertThat(small.getLineHeight()).isLessThan(big.getLineHeight());
        assertThat(big.getLineHeight()).isGreaterThanOrEqualTo(16f);
        assertThat(small.getData().markupEnabled).isFalse();
        assertThat(big.getData().markupEnabled).as("markup option").isTrue();
        assertThat(big.getData().getGlyph('A')).isNotNull();
        assertThat(big.getData().getGlyph('Z')).as("only the listed characters").isNull();
        assertThat(small.getData().getGlyph('Z')).as("default character set").isNotNull();
        assertThat(assets.isLoaded("font.small")).isTrue();
    }

    @Test
    void aMissingOptionalFontFallsBackToTheBuiltInFont() {
        assets.loadGroup("optional").finish();

        assertThat(assets.get("font.gone", BitmapFont.class)).isNotNull();
    }

    @Test
    void invalidFontOptionsAreReported() {
        AssetLoadException e = catchThrowableOfType(AssetLoadException.class, () -> AssetManifest.parse("""
                { "version": 1, "assets": [
                  { "id": "a", "path": "f.ttf", "type": "freetype", "group": "g",
                    "font": { "size": 0, "mono": "yes", "color": "red", "borderWidth": -1, "glow": 2,
                              "characters": "" } },
                  { "id": "b", "path": "f.fnt", "type": "font", "group": "g", "font": { "size": 8, "markup": true } },
                  { "id": "c", "path": "f.png", "type": "freetype", "group": "g" },
                  { "id": "d", "path": "t.png", "type": "texture", "group": "g", "font": { } } ] }
                """, "m.json"));

        assertThat(e.problems()).containsExactlyInAnyOrder(
                "assets[0] (\"a\"): font.glow: unknown field (expected size, mono, color, borderWidth, borderColor, "
                        + "shadowOffsetX, shadowOffsetY, shadowColor, characters, markup)",
                "assets[0] (\"a\"): font.size: must be a whole number > 0",
                "assets[0] (\"a\"): font.mono: must be true or false",
                "assets[0] (\"a\"): font.color: must be a colour \"#RRGGBB\" or \"#RRGGBBAA\"",
                "assets[0] (\"a\"): font.borderWidth: must be a number >= 0",
                "assets[0] (\"a\"): font.characters: must be \"default\" or the characters to include",
                "assets[1] (\"b\"): font.size: only \"markup\" applies to a bitmap font",
                "assets[2] (\"c\"): a freetype font must be a .ttf or .otf file (was f.png)",
                "assets[3] (\"d\"): \"font\" options are only for font and freetype entries");
    }
}
