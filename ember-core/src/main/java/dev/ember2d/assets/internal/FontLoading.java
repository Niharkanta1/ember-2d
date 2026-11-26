package dev.ember2d.assets.internal;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;

/** FreeType font loading through libGDX's loaders (F-004, I-140): entry options to generator parameters. */
final class FontLoading {

    private FontLoading() {
    }

    /** Registers the FreeType loaders: generators by type, fonts for keys ending in {@code .ttf}. */
    static void register(AssetManager manager, FileHandleResolver resolver) {
        manager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        manager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));
    }

    /** Loader parameters for a font file and its manifest options; nearest filtering for crisp pixel text. */
    static FreetypeFontLoader.FreeTypeFontLoaderParameter parameters(String path, AssetManifest.FontOptions o) {
        AssetManifest.FontOptions options = o == null ? AssetManifest.FontOptions.DEFAULTS : o;
        FreetypeFontLoader.FreeTypeFontLoaderParameter p = new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        p.fontFileName = path;
        FreeTypeFontGenerator.FreeTypeFontParameter f = p.fontParameters;
        f.size = options.size();
        f.mono = options.mono();
        f.color = colour(options.color());
        f.borderWidth = options.borderWidth();
        f.borderColor = colour(options.borderColor());
        f.shadowOffsetX = options.shadowOffsetX();
        f.shadowOffsetY = options.shadowOffsetY();
        f.shadowColor = colour(options.shadowColor());
        if (options.characters() != null) {
            f.characters = options.characters();
        }
        f.minFilter = Texture.TextureFilter.Nearest;
        f.magFilter = Texture.TextureFilter.Nearest;
        return p;
    }

    private static Color colour(String hex) {
        return Color.valueOf(hex.startsWith("#") ? hex.substring(1) : hex);
    }
}
