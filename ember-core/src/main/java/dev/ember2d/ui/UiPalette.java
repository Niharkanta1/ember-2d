package dev.ember2d.ui;

import com.badlogic.gdx.graphics.Color;
import dev.ember2d.core.PublicApi;

/** The colours of the generated pixel skin (F-012); change them before {@link UiSkins#pixel(com.badlogic.gdx.graphics.g2d.BitmapFont, UiPalette)}. */
@PublicApi
public final class UiPalette {
    public final Color panel = new Color();
    public final Color panelBorder = new Color();
    public final Color button = new Color();
    public final Color buttonOver = new Color();
    public final Color buttonDown = new Color();
    public final Color text = new Color();
    public final Color dim = new Color();
    public final Color accent = new Color();
    public final Color disabled = new Color();

    /** Dark panels, light text, a warm accent for focus. */
    public static UiPalette standard() {
        UiPalette p = new UiPalette();
        p.panel.set(0.13f, 0.11f, 0.20f, 0.92f);
        p.panelBorder.set(0.93f, 0.89f, 0.80f, 1f);
        p.button.set(0.22f, 0.20f, 0.33f, 1f);
        p.buttonOver.set(0.30f, 0.27f, 0.44f, 1f);
        p.buttonDown.set(0.17f, 0.15f, 0.26f, 1f);
        p.text.set(0.97f, 0.95f, 0.88f, 1f);
        p.dim.set(0.66f, 0.63f, 0.72f, 1f);
        p.accent.set(1f, 0.78f, 0.25f, 1f);
        p.disabled.set(0.42f, 0.40f, 0.48f, 1f);
        return p;
    }
}
