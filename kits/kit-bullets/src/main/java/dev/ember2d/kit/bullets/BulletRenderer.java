package dev.ember2d.kit.bullets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import dev.ember2d.render.LayerContent;

/**
 * Draws every bullet of a {@link BulletWorld} on one render layer (F-024): add it with
 * {@code spriteRenderSystem.addLayerContent(renderer)}. Each type's region is looked up once (again after a hot
 * reload); bullets are centred on their position, sized by region pixels over pixels per unit times the type's scale,
 * and rotated to their velocity when the type says {@code rotate}. One pass, no allocation.
 */
@PublicApi
public final class BulletRenderer implements LayerContent {

    private final BulletWorld bullets;
    private final Assets assets;
    private final String layer;
    private final float pixelsPerUnit;
    private final TextureRegion[] regions;
    private int generation = Integer.MIN_VALUE;
    private Visibility visibility;

    /** Whether the player can see a point (F-030); bullets of {@code fogged} types are drawn only where it says so. */
    @FunctionalInterface
    public interface Visibility {
        boolean isVisible(float x, float y, float radius);
    }

    /** Sets the test for {@code fogged} bullet types; null draws them everywhere. */
    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public BulletRenderer(BulletWorld bullets, Assets assets, String layer, float pixelsPerUnit) {
        this.bullets = bullets;
        this.assets = assets;
        this.layer = layer;
        this.pixelsPerUnit = pixelsPerUnit;
        this.regions = new TextureRegion[bullets.types().size()];
    }

    @Override
    public void drawLayer(String renderLayer, Batch batch) {
        if (!layer.equals(renderLayer) || bullets.size() == 0) {
            return;
        }
        if (generation != assets.generation()) {
            generation = assets.generation();
            for (int i = 0; i < regions.length; i++) {
                regions[i] = assets.region(bullets.types().get(i).region);
            }
        }
        for (int i = 0; i < bullets.size(); i++) {
            BulletType type = bullets.types().get(bullets.type(i));
            if (type.fogged && visibility != null && !visibility.isVisible(bullets.x(i), bullets.y(i), type.radius)) {
                continue;
            }
            TextureRegion region = regions[bullets.type(i)];
            float w = region.getRegionWidth() / pixelsPerUnit;
            float h = region.getRegionHeight() / pixelsPerUnit;
            float rotation = (type.rotate ? MathUtils.atan2(bullets.vy(i), bullets.vx(i)) * MathUtils.radiansToDegrees
                    : 0f) + type.rotationOffset;
            batch.setColor(type.tint);
            batch.draw(region, bullets.x(i) - w / 2f, bullets.y(i) - h / 2f, w / 2f, h / 2f, w, h, type.scale,
                    type.scale, rotation);
        }
        batch.setColor(Color.WHITE);
    }
}
