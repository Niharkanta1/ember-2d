package dev.ember2d.render.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.render.Sprite;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The values the material shader gets per sprite (F-026), computed without GL. */
class MaterialShaderTest {

    static Texture sheet;
    static Texture palette;
    static Texture widePalette;

    @BeforeAll
    static void gdx() {
        HeadlessGdx.install();
        sheet = new Texture(new Pixmap(64, 32, Pixmap.Format.RGBA8888));
        palette = new Texture(new Pixmap(5, 2, Pixmap.Format.RGBA8888));
        widePalette = new Texture(new Pixmap(20, 2, Pixmap.Format.RGBA8888));
    }

    @AfterAll
    static void done() {
        HeadlessGdx.clear();
    }

    @Test
    void flashOutlineTexelRegionAndPaletteValues() {
        Sprite sprite = new Sprite();
        sprite.flash = 1.5f;
        sprite.flashColor.set(Color.RED);
        sprite.outline.set(0f, 1f, 0f, 0.5f);
        TextureRegion region = new TextureRegion(sheet, 16, 8, 16, 16);
        float[] v = new float[MaterialShader.VALUES];
        MaterialShader.values(sprite, region, palette, v);
        assertThat(v[3]).as("flash clamped to 1").isEqualTo(1f);
        assertThat(v[0]).isEqualTo(1f);
        assertThat(v[5]).isEqualTo(1f);
        assertThat(v[7]).isEqualTo(0.5f);
        assertThat(v[8]).isEqualTo(1f / 64f);
        assertThat(v[9]).isEqualTo(1f / 32f);
        assertThat(v[10]).isCloseTo(0.25f, within(1e-6f));
        assertThat(v[11]).isCloseTo(0.25f, within(1e-6f));
        assertThat(v[12]).isCloseTo(0.5f, within(1e-6f));
        assertThat(v[13]).isCloseTo(0.75f, within(1e-6f));
        assertThat(v[14]).as("palette colours").isEqualTo(5f);
        assertThat(v[15]).isEqualTo(5f);
    }

    @Test
    void aFlippedRegionStillGivesOrderedBoundsAndPalettesAreCapped() {
        TextureRegion region = new TextureRegion(sheet, 0, 0, 16, 16);
        region.flip(true, true);
        float[] v = new float[MaterialShader.VALUES];
        MaterialShader.values(new Sprite(), region, widePalette, v);
        assertThat(v[10]).isLessThan(v[12]);
        assertThat(v[11]).isLessThan(v[13]);
        assertThat(v[14]).as("at most 16 colours").isEqualTo(16f);
        assertThat(v[15]).isEqualTo(20f);
        MaterialShader.values(new Sprite(), region, null, v);
        assertThat(v[14]).isZero();
        assertThat(new Sprite().hasMaterial()).isFalse();
    }
}
