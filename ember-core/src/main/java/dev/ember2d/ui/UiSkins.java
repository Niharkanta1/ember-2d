package dev.ember2d.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import dev.ember2d.core.PublicApi;
import java.util.Objects;

/**
 * Builds a complete pixel-art Scene2D skin in code from a font (F-012, I-220): no skin files needed. Drawables are
 * small generated nine-patches (managed textures, so they survive an Android context loss), disposed with the skin.
 * The font is used, not owned: disposing the skin leaves it alone.
 *
 * <p>Drawables: {@code white}, {@code panel}, {@code button}, {@code button-over}, {@code button-down},
 * {@code button-focused}, {@code button-disabled}, {@code bar-bg}, {@code bar-fill}. Styles: {@code Label} (default,
 * {@code title}, {@code dim}), {@code TextButton} (default, {@code focused}), {@code ImageButton}, {@code Slider} and
 * {@code ProgressBar} ({@code default-horizontal}), {@code Window}, {@code ScrollPane}.
 */
@PublicApi
public final class UiSkins {

    private UiSkins() {
    }

    public static Skin pixel(BitmapFont font) {
        return pixel(font, UiPalette.standard());
    }

    public static Skin pixel(BitmapFont font, UiPalette palette) {
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(palette, "palette");
        Skin skin = new Skin();
        Drawable white = region(skin, "white", 1, palette.text, null);
        skin.add("white", white, Drawable.class);
        skin.add("panel", nine(skin, "panel", palette.panel, palette.panelBorder), Drawable.class);
        Drawable button = nine(skin, "button", palette.button, palette.dim);
        Drawable over = nine(skin, "button-over", palette.buttonOver, palette.text);
        Drawable down = nine(skin, "button-down", palette.buttonDown, palette.text);
        Drawable focused = nine(skin, "button-focused", palette.buttonOver, palette.accent);
        Drawable disabled = nine(skin, "button-disabled", palette.buttonDown, palette.disabled);
        skin.add("button", button, Drawable.class);
        skin.add("button-over", over, Drawable.class);
        skin.add("button-down", down, Drawable.class);
        skin.add("button-focused", focused, Drawable.class);
        skin.add("button-disabled", disabled, Drawable.class);
        Drawable barBg = nine(skin, "bar-bg", palette.buttonDown, palette.dim);
        Drawable barFill = region(skin, "bar-fill", 2, palette.accent, null);
        skin.add("bar-bg", barBg, Drawable.class);
        skin.add("bar-fill", barFill, Drawable.class);

        skin.add("default", new Label.LabelStyle(font, palette.text));
        skin.add("title", new Label.LabelStyle(font, palette.accent));
        skin.add("dim", new Label.LabelStyle(font, palette.dim));

        TextButton.TextButtonStyle text = new TextButton.TextButtonStyle(button, down, null, font);
        text.over = over;
        text.disabled = disabled;
        text.fontColor = new Color(palette.text);
        text.overFontColor = new Color(palette.accent);
        text.disabledFontColor = new Color(palette.disabled);
        skin.add("default", text);
        TextButton.TextButtonStyle focusedText = new TextButton.TextButtonStyle(text);
        focusedText.up = focused;
        focusedText.over = focused;
        focusedText.fontColor = new Color(palette.accent);
        skin.add("focused", focusedText);

        ImageButton.ImageButtonStyle image = new ImageButton.ImageButtonStyle(button, down, null, null, null, null);
        image.over = over;
        image.disabled = disabled;
        skin.add("default", image);

        Drawable knob = nine(skin, "knob", palette.text, palette.panelBorder);
        skin.add("knob", knob, Drawable.class);
        skin.add("default-horizontal", new Slider.SliderStyle(barBg, knob));
        ProgressBar.ProgressBarStyle bar = new ProgressBar.ProgressBarStyle(barBg, null);
        bar.knobBefore = barFill;
        skin.add("default-horizontal", bar);

        skin.add("default", new Window.WindowStyle(font, palette.text, skin.getDrawable("panel")));
        skin.add("default", new ScrollPane.ScrollPaneStyle(null, null, null, barBg, knob));
        return skin;
    }

    /**
     * The on-screen controls' default look (F-022, I-225): {@code touch-base} (ring), {@code touch-knob} (disc),
     * {@code touch-button}, {@code touch-button-down}, {@code touch-dpad}, {@code touch-dpad-down}. White shapes drawn
     * at the controls' opacity; 128 px textures with linear filtering, scaled to each control's size.
     */
    public static Skin touch() {
        Skin skin = new Skin();
        int n = 128;
        float c = (n - 1) / 2f;
        Pixmap base = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        Pixmap knob = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        Pixmap button = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        Pixmap down = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        Pixmap dpad = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        Pixmap dpadDown = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;   // 0 centre .. 1 edge
                if (d <= 1f) {
                    base.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, d > 0.9f ? 0.95f : 0.28f));
                    knob.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, d > 0.86f ? 1f : 0.8f));
                    button.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, d > 0.9f ? 1f : 0.55f));
                    down.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, d > 0.9f ? 1f : 0.85f));
                }
                boolean arm = Math.abs(x - c) < n * 0.17f || Math.abs(y - c) < n * 0.17f;
                if (arm && d <= 1f) {
                    boolean edge = Math.abs(x - c) > n * 0.15f && Math.abs(y - c) > n * 0.15f || d > 0.94f;
                    dpad.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, edge ? 1f : 0.55f));
                    dpadDown.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, edge ? 1f : 0.85f));
                }
            }
        }
        String[] names = {"touch-base", "touch-knob", "touch-button", "touch-button-down", "touch-dpad",
            "touch-dpad-down"};
        Pixmap[] maps = {base, knob, button, down, dpad, dpadDown};
        for (int i = 0; i < names.length; i++) {
            Texture texture = texture(skin, names[i], maps[i]);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            skin.add(names[i], new TextureRegionDrawable(new TextureRegion(texture)), Drawable.class);
        }
        return skin;
    }

    /** A 6×6 nine-patch: 1 px border, 2 px stretch margin. */
    private static Drawable nine(Skin skin, String name, Color fill, Color border) {
        Pixmap pixmap = new Pixmap(6, 6, Pixmap.Format.RGBA8888);
        pixmap.setColor(fill);
        pixmap.fill();
        pixmap.setColor(border);
        pixmap.drawRectangle(0, 0, 6, 6);
        NinePatch patch = new NinePatch(new TextureRegion(texture(skin, name, pixmap)), 2, 2, 2, 2);
        patch.setPadding(4, 4, 3, 3);
        return new NinePatchDrawable(patch);
    }

    /** A plain square of one colour. */
    private static Drawable region(Skin skin, String name, int size, Color color, Color unused) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        return new TextureRegionDrawable(new TextureRegion(texture(skin, name, pixmap)));
    }

    /** A managed texture over the pixmap; both are disposed with the skin. */
    private static Texture texture(Skin skin, String name, Pixmap pixmap) {
        Texture texture = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        skin.add(name + "-pixmap", pixmap, Pixmap.class);
        skin.add(name + "-texture", texture, Texture.class);
        return texture;
    }
}
