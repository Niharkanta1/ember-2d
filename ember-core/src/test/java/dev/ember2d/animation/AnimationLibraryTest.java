package dev.ember2d.animation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.assets.internal.AssetManifest;
import dev.ember2d.assets.internal.GdxAssets;
import dev.ember2d.core.LogConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Clip import from real files (F-007, I-130), headless. Files: assets-test/anim/. */
class AnimationLibraryTest {

    private static final FileHandleResolver RESOLVER =
            path -> Gdx.files.internal(path.startsWith("assets-test/") ? path : "assets-test/" + path);
    private static final String MANIFEST = """
            { "version": 1, "assets": [
              { "id": "tex.strip", "path": "anim/strip.png", "type": "texture", "group": "anim" },
              { "id": "tex.grid", "path": "anim/grid.png", "type": "texture", "group": "anim" },
              { "id": "tex.hero", "path": "anim/hero.png", "type": "texture", "group": "anim" },
              { "id": "atlas.test", "path": "anim/test.atlas", "type": "atlas", "group": "anim" },
              { "id": "ase.array", "path": "anim/hero-array.json", "type": "json", "group": "anim" },
              { "id": "ase.hash", "path": "anim/hero-hash.json", "type": "json", "group": "anim" },
              { "id": "anim.good", "path": "anim/good.clips.json", "type": "clips", "group": "anim" },
              { "id": "anim.bad", "path": "anim/bad.clips.json", "type": "clips", "group": "anim" } ] }
            """;

    private GdxAssets assets;
    private AnimationLibrary library;

    @BeforeEach
    void setUp() {
        HeadlessGdx.install();
        assets = new GdxAssets(AssetManifest.parse(MANIFEST, "assets-test/manifest.json"), RESOLVER);
        assets.loadGroup("anim").finish();
        library = new AnimationLibrary(assets);
    }

    @AfterEach
    void tearDown() {
        assets.dispose();
        LogConfig.resetToDefaults();
        HeadlessGdx.clear();
    }

    private static int[] regionX(Clip clip) {
        int[] xs = new int[clip.frameCount()];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = clip.frame(i).getRegionX();
        }
        return xs;
    }

    @Test
    void spriteSheetsStripsAndGrids() {
        ClipSet set = library.clipSet("anim.good");
        assertThat(set.names()).containsExactly("strip", "grid2", "atlasRun", "trim", "aseRun", "aseBack",
                "aseBounce", "aseHit", "aseOverride");

        Clip strip = set.clip("strip");
        assertThat(regionX(strip)).containsExactly(0, 32, 64, 96);
        assertThat(strip.duration(3)).isEqualTo(0.1f);
        assertThat(strip.mode()).isEqualTo(PlayMode.LOOP);
        assertThat(strip.eventAt(0)).isEqualTo("start");
        assertThat(strip.eventAt(1)).isNull();
        assertThat(strip.eventAt(2)).isEqualTo("mid");
        assertThat(strip.totalDuration()).isCloseTo(0.4f, within(1e-6f));

        Clip grid = set.clip("grid2");
        assertThat(grid.frameCount()).isEqualTo(3);
        assertThat(grid.frame(0).getRegionX()).isEqualTo(32);
        assertThat(grid.frame(1).getRegionY()).as("second row").isEqualTo(32);
        assertThat(grid.frame(2).getRegionX()).isEqualTo(32);
        assertThat(grid.mode()).isEqualTo(PlayMode.PINGPONG);
        assertThat(grid.duration(0)).isEqualTo(0.05f);
        assertThat(set.clip("nope")).isNull();
        assertThat(library.clipSet("anim.good")).as("cached").isSameAs(set);
    }

    @Test
    void atlasSequencesInIndexOrderWithTrimOffsets() {
        ClipSet set = library.clipSet("anim.good");
        assertThat(regionX(set.clip("atlasRun"))).containsExactly(0, 16, 32);
        assertThat(set.clip("atlasRun").mode()).isEqualTo(PlayMode.ONCE);

        TextureRegion trim = set.clip("trim").frame(0);
        assertThat(trim).isInstanceOf(TextureAtlas.AtlasRegion.class);
        TextureAtlas.AtlasRegion r = (TextureAtlas.AtlasRegion) trim;
        assertThat(r.offsetX).isEqualTo(2f);
        assertThat(r.offsetY).isEqualTo(1f);
        assertThat(r.originalWidth).isEqualTo(16);
        assertThat(r.packedWidth).isEqualTo(8);
    }

    @Test
    void asepriteTagsDirectionsDurationsTrimAndEvents() {
        ClipSet set = library.clipSet("anim.good");

        Clip run = set.clip("aseRun");
        assertThat(regionX(run)).containsExactly(0, 16, 32);
        assertThat(run.duration(0)).isEqualTo(0.1f);
        assertThat(run.duration(1)).isEqualTo(0.15f);
        assertThat(run.duration(2)).isEqualTo(0.2f);
        assertThat(run.mode()).isEqualTo(PlayMode.LOOP);
        assertThat(run.eventAt(1)).as("from the tag's user data").isEqualTo("step");

        Clip back = set.clip("aseBack");
        assertThat(regionX(back)).as("reverse, from the json-hash export").containsExactly(32, 16, 0);
        assertThat(back.duration(0)).isEqualTo(0.2f);
        assertThat(set.clip("aseBounce").mode()).isEqualTo(PlayMode.PINGPONG);

        Clip hit = set.clip("aseHit");
        assertThat(hit.mode()).as("repeat 1").isEqualTo(PlayMode.ONCE);
        TextureAtlas.AtlasRegion trimmed = (TextureAtlas.AtlasRegion) hit.frame(0);
        assertThat(trimmed.getRegionX()).isEqualTo(48);
        assertThat(trimmed.getRegionWidth()).isEqualTo(10);
        assertThat(trimmed.offsetX).isEqualTo(3f);
        assertThat(trimmed.offsetY).as("from the bottom: 16 - (2 + 12)").isEqualTo(2f);
        assertThat(trimmed.originalHeight).isEqualTo(16);
        assertThat(hit.duration(0)).isEqualTo(0.3f);

        Clip over = set.clip("aseOverride");
        assertThat(over.mode()).isEqualTo(PlayMode.ONCE);
        assertThat(over.duration(1)).isEqualTo(0.5f);
        assertThat(over.eventAt(0)).isEqualTo("go");
        assertThat(over.eventAt(1)).isEqualTo("step");
    }

    @Test
    void invalidClipSetsNameFileClipAndField() {
        AnimationException e = catchThrowableOfType(AnimationException.class, () -> library.clipSet("anim.bad"));

        String f = "anim/bad.clips.json: ";
        assertThat(e).hasMessageStartingWith("Invalid clip set \"anim.bad\"");
        assertThat(e.problems()).containsExactlyInAnyOrder(
                f + "speed: unknown field (expected version, defaults, clips)",
                f + "version: required, must be 1",
                f + "clips.none: needs exactly one frame source: atlas, aseprite or sheet",
                f + "clips.two: needs exactly one frame source: atlas, aseprite or sheet",
                f + "clips.range.frames: [2, 9] is out of range (the sheet has frames 0-3)",
                f + "clips.missing.regions: no regions named \"hero/jump\" in atlas \"atlas.test\"",
                f + "clips.tag.tag: no frame tag \"dash\" (tags: [run, back, bounce, hit, bad])",
                f + "clips.data.tag: user data \"events: x=y\": expected \"events: 2=step, 6=step\"",
                f + "clips.mode.frameDuration: must be a number > 0",
                f + "clips.mode.mode: must be \"loop\", \"once\" or \"pingpong\" (was \"twice\")",
                f + "clips.event.events.7: frame 7 is out of range (0-3)",
                f + "clips.event.events.a: the key must be a frame index",
                f + "clips.asset.colour: unknown field (expected atlas, regions, aseprite, tag, texture, sheet, "
                        + "frameWidth, frameHeight, frames, frameDuration, mode, events)",
                f + "clips.asset.sheet: unknown asset \"tex.nope\"");
        assertThatThrownBy(() -> library.clipSet("anim.none")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the asset manifest");
    }

    @Test
    void reloadReplacesAClipSetAndABadReloadKeepsTheOld() {
        ClipSet before = library.clipSet("anim.good");
        library.reload("anim.good", new com.badlogic.gdx.utils.JsonReader().parse("""
                { "version": 1, "clips": { "only": { "sheet": "tex.strip", "frameWidth": 32, "frameHeight": 32 } } }
                """));
        ClipSet after = library.clipSet("anim.good");
        assertThat(after).isNotSameAs(before);
        assertThat(after.names()).containsExactly("only");

        assertThatThrownBy(() -> library.reload("anim.good", new com.badlogic.gdx.utils.JsonReader().parse(
                "{ \"version\": 1, \"clips\": { \"x\": { } } }"))).isInstanceOf(AnimationException.class);
        assertThat(library.clipSet("anim.good")).isSameAs(after);
    }
}
