package dev.ember2d.demo.gold;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.camera.CameraRig;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.topdown.TopDownBody;

/**
 * The original game's camera (F-029), ported from its {@code CameraController}: in {@code LATE}, the rig moves towards
 * the player plus {@link GoldCamera#lookAhead} seconds of its velocity at {@link GoldCamera#smoothing} per second, and
 * a shake fades out over its length in a random direction each frame. Shakes come from {@link #shake}.
 */
final class GoldCameraSystem extends EntitySystem {

    private final CameraRig rig;
    private final Family heroes;
    private final Vector2 base = new Vector2();
    private boolean placed;

    GoldCameraSystem(World world, CameraRig rig) {
        super(Phase.LATE, world.family(GoldCamera.class));
        this.rig = rig;
        this.heroes = world.family(Hero.class, Transform.class);
    }

    /**
     * Shakes the camera by up to {@code strength} world units for {@code seconds}; a weaker shake does not cut a
     * stronger one short (the original's rule).
     */
    static void shake(World world, float strength, float seconds) {
        for (int e : world.family(GoldCamera.class).entities().toArray()) {
            GoldCamera c = world.get(e, GoldCamera.class);
            float current = c.shakeLeft > 0f ? c.shakeStrength * (c.shakeLeft / c.shakeSeconds) : 0f;
            if (seconds > 0f && strength >= current) {
                c.shakeStrength = strength;
                c.shakeSeconds = seconds;
                c.shakeLeft = seconds;
            }
        }
    }

    @Override
    protected void process(int entity, float delta) {
        if (heroes.size() == 0) {
            return;
        }
        int hero = heroes.entities().first();
        Transform t = world().get(hero, Transform.class);
        TopDownBody body = world().get(hero, TopDownBody.class);
        GoldCamera c = world().get(entity, GoldCamera.class);
        float goalX = t.position.x;
        float goalY = t.position.y;
        if (body != null) {
            goalX += (body.vx + body.knockbackX) * c.lookAhead;
            goalY += (body.vy + body.knockbackY) * c.lookAhead;
        }
        if (!placed) {   // campaign levels start with the camera on the player
            base.set(t.position.x, t.position.y);
            placed = true;
        } else {
            float k = Math.min(1f, c.smoothing * delta);
            base.add((goalX - base.x) * k, (goalY - base.y) * k);
        }
        float shakeX = 0f;
        float shakeY = 0f;
        if (c.shakeLeft > 0f) {
            c.shakeLeft = Math.max(0f, c.shakeLeft - delta);
            float amplitude = c.shakeStrength * (c.shakeLeft / c.shakeSeconds);
            float angle = MathUtils.random(MathUtils.PI2);
            shakeX = MathUtils.cos(angle) * amplitude;
            shakeY = MathUtils.sin(angle) * amplitude;
        }
        rig.position.set(base.x + shakeX, base.y + shakeY);
    }
}
