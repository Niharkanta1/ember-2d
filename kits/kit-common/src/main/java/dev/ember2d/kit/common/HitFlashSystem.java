package dev.ember2d.kit.common;

import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.render.Sprite;

/**
 * Turns on the hit flash of {@link HitFlash} entities when they are {@link Damaged} and off after its time (F-026),
 * in {@code UPDATE} on scaled time.
 */
@PublicApi
public final class HitFlashSystem extends EntitySystem {

    public HitFlashSystem(World world, EventBus events) {
        super(Phase.UPDATE, world.family(HitFlash.class, Sprite.class));
        events.on(Damaged.class, e -> {
            HitFlash flash = world.get(e.target(), HitFlash.class);
            Sprite sprite = world.get(e.target(), Sprite.class);
            if (flash != null && sprite != null) {
                flash.left = flash.seconds;
                sprite.flashColor.set(flash.color);
                sprite.flash = 1f;
            }
        });
    }

    @Override
    protected void process(int entity, float delta) {
        HitFlash flash = world().get(entity, HitFlash.class);
        if (flash.left <= 0f) {
            return;
        }
        flash.left -= delta;
        if (flash.left <= 0f) {
            flash.left = 0f;
            world().get(entity, Sprite.class).flash = 0f;
        }
    }
}
