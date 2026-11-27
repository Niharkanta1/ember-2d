package dev.ember2d.render.internal;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.core.Log;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteMaterials;

/**
 * The built-in material shader (F-026, I-520): palette swap, tint, 1-pixel outline and hit flash in one GLSL ES 1.00
 * program, created on first use. A sprite with an effect is drawn alone: the batch is flushed, the shader and its
 * values are set, and the default shader is restored afterwards.
 */
public final class MaterialShader implements SpriteMaterials, Disposable {

    private static final Log LOG = Log.get("render");
    static final int MAX_PALETTE = 16;
    /** Texture unit for the palette (the sprite's texture is on unit 0). */
    static final int PALETTE_UNIT = 1;

    static final String VERTEX = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a = v_color.a * (255.0 / 254.0);
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;

    static final String FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            uniform sampler2D u_palette;
            uniform float u_paletteCount;
            uniform float u_paletteWidth;
            uniform vec4 u_flash;
            uniform vec4 u_outline;
            uniform vec2 u_texel;
            uniform vec4 u_region;

            float alphaAt(vec2 uv) {
                if (uv.x < u_region.x || uv.y < u_region.y || uv.x > u_region.z || uv.y > u_region.w) {
                    return 0.0;
                }
                return texture2D(u_texture, uv).a;
            }

            void main() {
                vec4 c = texture2D(u_texture, v_texCoords);
                if (u_paletteCount > 0.0) {
                    for (int i = 0; i < 16; i++) {
                        if (float(i) >= u_paletteCount) {
                            break;
                        }
                        float u = (float(i) + 0.5) / u_paletteWidth;
                        vec3 from = texture2D(u_palette, vec2(u, 0.25)).rgb;
                        if (all(lessThan(abs(from - c.rgb), vec3(0.01)))) {
                            c.rgb = texture2D(u_palette, vec2(u, 0.75)).rgb;
                            break;
                        }
                    }
                }
                // The outline: fully transparent pixels next to an opaque one (shadows and soft edges stay as they are).
                if (u_outline.a > 0.0 && c.a < 0.01) {
                    float n = max(max(alphaAt(v_texCoords + vec2(u_texel.x, 0.0)),
                            alphaAt(v_texCoords - vec2(u_texel.x, 0.0))),
                            max(alphaAt(v_texCoords + vec2(0.0, u_texel.y)), alphaAt(v_texCoords - vec2(0.0, u_texel.y))));
                    if (n >= 0.5) {
                        gl_FragColor = vec4(u_outline.rgb, u_outline.a * v_color.a);
                        return;
                    }
                }
                c *= v_color;
                c.rgb = mix(c.rgb, u_flash.rgb, u_flash.a);
                gl_FragColor = c;
            }
            """;

    /** The values one sprite needs: flash (4), outline (4), texel (2), region (4), palette count and width (2). */
    static final int VALUES = 16;

    private ShaderProgram program;
    private boolean failed;
    private final float[] values = new float[VALUES];

    /**
     * Fills {@code out} with the shader values for a sprite (no GL needed): flash colour and amount, outline colour,
     * texel size and region bounds in texture coordinates, and the palette's colour count.
     */
    static void values(Sprite sprite, TextureRegion region, Texture palette, float[] out) {
        float flash = MathUtils.clamp(sprite.flash, 0f, 1f);
        out[0] = sprite.flashColor.r;
        out[1] = sprite.flashColor.g;
        out[2] = sprite.flashColor.b;
        out[3] = flash;
        out[4] = sprite.outline.r;
        out[5] = sprite.outline.g;
        out[6] = sprite.outline.b;
        out[7] = sprite.outline.a;
        Texture texture = region.getTexture();
        out[8] = texture == null ? 0f : 1f / texture.getWidth();
        out[9] = texture == null ? 0f : 1f / texture.getHeight();
        out[10] = Math.min(region.getU(), region.getU2());
        out[11] = Math.min(region.getV(), region.getV2());
        out[12] = Math.max(region.getU(), region.getU2());
        out[13] = Math.max(region.getV(), region.getV2());
        out[14] = palette == null ? 0f : Math.min(MAX_PALETTE, palette.getWidth());
        out[15] = palette == null ? 1f : palette.getWidth();
    }

    @Override
    public boolean begin(Batch batch, Sprite sprite, TextureRegion region, Texture palette) {
        ShaderProgram shader = program();
        if (shader == null) {
            return false;
        }
        if (palette != null && palette.getWidth() > MAX_PALETTE) {
            LOG.warn("A palette is " + palette.getWidth() + " pixels wide; only the first " + MAX_PALETTE
                    + " colours are used");
        }
        values(sprite, region, palette, values);
        batch.flush();
        batch.setShader(shader);
        if (palette != null) {
            palette.bind(PALETTE_UNIT);
            region.getTexture().bind(0);   // binding the palette changed the active unit
        }
        shader.setUniformi("u_palette", PALETTE_UNIT);
        shader.setUniformf("u_flash", values[0], values[1], values[2], values[3]);
        shader.setUniformf("u_outline", values[4], values[5], values[6], values[7]);
        shader.setUniformf("u_texel", values[8], values[9]);
        shader.setUniformf("u_region", values[10], values[11], values[12], values[13]);
        shader.setUniformf("u_paletteCount", values[14]);
        shader.setUniformf("u_paletteWidth", values[15]);
        return true;
    }

    @Override
    public void end(Batch batch) {
        batch.flush();
        batch.setShader(null);   // back to the batch's default shader
    }

    private ShaderProgram program() {
        if (program == null && !failed) {
            ShaderProgram.pedantic = false;
            ShaderProgram p = new ShaderProgram(VERTEX, FRAGMENT);
            if (!p.isCompiled()) {
                failed = true;
                LOG.error("The material shader did not compile; flash, outline and palette are off:\n" + p.getLog());
                p.dispose();
                return null;
            }
            program = p;
        }
        return program;
    }

    @Override
    public void dispose() {
        if (program != null) {
            program.dispose();
            program = null;
        }
    }
}
