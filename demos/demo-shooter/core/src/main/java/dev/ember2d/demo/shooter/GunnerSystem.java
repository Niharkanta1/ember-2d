package dev.ember2d.demo.shooter;

import com.badlogic.gdx.math.MathUtils;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.bullets.BulletSystem;

/** Enemies with a {@link Gunner} fire their pattern at the player every few seconds while the player is near. */
final class GunnerSystem extends EntitySystem {

    private static final float RANGE = 11f;

    private final BulletSystem bullets;
    private final int player;

    GunnerSystem(World world, BulletSystem bullets, int player) {
        super(Phase.UPDATE, world.family(Gunner.class, Transform.class));
        this.bullets = bullets;
        this.player = player;
    }

    @Override
    protected void process(int entity, float delta) {
        Gunner g = world().get(entity, Gunner.class);
        if (g.wait < 0f) {
            g.wait = MathUtils.random(0.5f, g.interval);
        }
        g.wait -= delta;
        if (g.wait > 0f) {
            return;
        }
        g.wait = g.interval;
        Transform t = world().get(entity, Transform.class);
        Transform p = world().get(player, Transform.class);
        if (p == null || t.position.dst2(p.position) > RANGE * RANGE) {
            return;
        }
        bullets.runAt(g.pattern, t.position.x, t.position.y + 0.5f, player, 2);
    }
}
