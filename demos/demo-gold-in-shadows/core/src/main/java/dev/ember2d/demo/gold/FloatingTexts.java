package dev.ember2d.demo.gold;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.Array;
import dev.ember2d.assets.Assets;
import dev.ember2d.render.LayerContent;

/**
 * Short feedback texts that rise and fade above a point (F-029), ported from the original's {@code FloatingText}: "+10"
 * for gold, "+{" for a medkit, "KEY!", "OPEN!" and "FIND THE &" at the door. Each lives 1.1 s, rises 12 px with an
 * ease-out, and fades over the last third. Drawn in the pixel font, one font pixel per world pixel, centred.
 */
final class FloatingTexts implements LayerContent {

    static final float PX = 1f / 16f;
    static final float LIFETIME = 1.1f;
    static final float RISE = 12f * PX;

    private final Assets assets;
    private final String layer;
    private final Array<Text> texts = new Array<>();
    private final GlyphLayout glyphs = new GlyphLayout();

    FloatingTexts(Assets assets, String layer) {
        this.assets = assets;
        this.layer = layer;
    }

    /** Shows {@code text} rising from (x, y), in world units. */
    void spawn(String text, float x, float y, Color color) {
        texts.add(new Text(text, x, y, color));
    }

    int size() {
        return texts.size;
    }

    void update(float delta) {
        for (int i = texts.size - 1; i >= 0; i--) {
            Text t = texts.get(i);
            t.age += delta;
            if (t.age >= LIFETIME) {
                texts.removeIndex(i);
            }
        }
    }

    /** Height above the start after {@code age} seconds. */
    static float rise(float age) {
        float t = Math.min(1f, age / LIFETIME);
        return RISE * (1f - (1f - t) * (1f - t));
    }

    /** Opacity after {@code age} seconds: solid, then fading over the last third. */
    static float alpha(float age) {
        float t = age / LIFETIME;
        return t < 0.66f ? 1f : Math.max(0f, 1f - (t - 0.66f) / 0.34f);
    }

    @Override
    public void drawLayer(String layerName, Batch batch) {
        if (!layer.equals(layerName) || texts.size == 0) {
            return;
        }
        BitmapFont font = assets.get("font.pixel", BitmapFont.class);
        float scaleX = font.getData().scaleX;
        float scaleY = font.getData().scaleY;
        font.getData().setScale(PX);
        try {
            for (Text t : texts) {
                font.setColor(t.color.r, t.color.g, t.color.b, alpha(t.age));
                glyphs.setText(font, t.text);
                font.draw(batch, t.text, Math.round((t.x - glyphs.width / 2f) * 16f) * PX,
                        t.y + rise(t.age) + glyphs.height);
            }
        } finally {
            font.getData().setScale(scaleX, scaleY);
            font.setColor(Color.WHITE);
        }
    }

    private static final class Text {
        final String text;
        final float x;
        final float y;
        final Color color;
        float age;

        Text(String text, float x, float y, Color color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
}
