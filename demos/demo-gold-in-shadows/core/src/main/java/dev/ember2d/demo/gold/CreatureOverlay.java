package dev.ember2d.demo.gold;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.Disposable;
import dev.ember2d.assets.Assets;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.common.Health;
import dev.ember2d.render.LayerContent;
import dev.ember2d.render.Sprite;

/**
 * Draws what the original shows above creatures (F-029): "!" when one spots the player, "?" while it investigates a
 * sound, and a small health bar for 2.5 s after a hit. Only for creatures that are visible (not hidden in the fog).
 * Text is the original pixel font, one font pixel per world pixel.
 */
final class CreatureOverlay implements LayerContent, Disposable {

    private static final float PX = 1f / 16f;
    private static final float BAR_WIDTH = 12f * PX;
    private static final float BAR_HEIGHT = 2f * PX;
    private static final float HEAD = 14f * PX;

    private final World world;
    private final Assets assets;
    private final String layer;
    private final Family creatures;
    private Texture white;

    CreatureOverlay(World world, Assets assets, String layer) {
        this.world = world;
        this.assets = assets;
        this.layer = layer;
        this.creatures = world.family(Creature.class, Transform.class);
    }

    @Override
    public void drawLayer(String layerName, Batch batch) {
        if (!layer.equals(layerName) || creatures.size() == 0) {
            return;
        }
        if (white == null) {
            Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            white = new Texture(new PixmapTextureData(pixmap, null, false, false, true));
        }
        BitmapFont font = assets.get("font.pixel", BitmapFont.class);
        float scaleX = font.getData().scaleX;
        float scaleY = font.getData().scaleY;
        font.getData().setScale(PX);
        try {
            for (int i = 0; i < creatures.size(); i++) {
                int e = creatures.entities().get(i);
                Sprite sprite = world.get(e, Sprite.class);
                if (sprite != null && !sprite.visible) {
                    continue;
                }
                Creature c = world.get(e, Creature.class);
                Transform t = world.get(e, Transform.class);
                float x = t.position.x;
                float y = t.position.y + HEAD;
                if (c.healthBarLeft > 0f) {
                    Health h = world.get(e, Health.class);
                    if (h != null && h.max > 0) {
                        float fill = Math.max(0f, Math.min(1f, (float) h.current / h.max));
                        batch.setColor(Hud.DARK);
                        batch.draw(white, x - BAR_WIDTH / 2f - PX, y - PX, BAR_WIDTH + 2f * PX, BAR_HEIGHT + 2f * PX);
                        batch.setColor(Hud.MAROON);
                        batch.draw(white, x - BAR_WIDTH / 2f, y, BAR_WIDTH * fill, BAR_HEIGHT);
                        y += BAR_HEIGHT + 2f * PX;
                    }
                }
                String mark = c.alertLeft > 0f ? "!" : c.investigating && !c.hasTarget ? "?" : null;
                if (mark != null) {
                    font.setColor(c.alertLeft > 0f ? Hud.CREAM : Hud.TAN);
                    font.draw(batch, mark, x - 2f * PX, y + 8f * PX);
                }
            }
        } finally {
            font.getData().setScale(scaleX, scaleY);
            font.setColor(Color.WHITE);
            batch.setColor(Color.WHITE);
        }
    }

    @Override
    public void dispose() {
        if (white != null) {
            white.dispose();
            white = null;
        }
    }
}
