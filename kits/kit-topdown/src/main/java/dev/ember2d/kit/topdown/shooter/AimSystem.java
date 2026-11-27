package dev.ember2d.kit.topdown.shooter;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.kit.topdown.TopDownBody;

/**
 * Updates every {@link Aim} (F-016), in {@code UPDATE}: from its aim axis when held (the touch aim stick or keys), else
 * from the mouse pointer when {@code mouse} is set, else it keeps the last aim (at first the {@code TopDownBody}
 * facing). Runs while paused too, so the aim is right when play resumes.
 */
@PublicApi
public final class AimSystem extends EntitySystem {

    private final Input input;
    private final Vector2 v = new Vector2();

    public AimSystem(World world, Input input) {
        super(Phase.UPDATE, world.family(Aim.class, Transform.class));
        this.input = input;
    }

    @Override
    protected void process(int entity, float delta) {
        Aim aim = world().get(entity, Aim.class);
        Transform t = world().get(entity, Transform.class);
        input.axis2(aim.axis, v);
        if (v.len2() > 0.0001f) {
            set(aim, v.x, v.y);
            return;
        }
        if (aim.mouse) {
            input.pointerWorld(v);
            float dx = v.x - t.position.x;
            float dy = v.y - t.position.y;
            if (dx * dx + dy * dy > 0.0001f) {
                set(aim, dx, dy);
                return;
            }
        }
        if (!aim.aimed) {
            TopDownBody body = world().get(entity, TopDownBody.class);
            if (body != null) {
                set(aim, body.facingX, body.facingY);
            }
        }
    }

    private static void set(Aim aim, float x, float y) {
        float len = (float) Math.sqrt(x * x + y * y);
        aim.aimX = x / len;
        aim.aimY = y / len;
        aim.angle = (float) Math.toDegrees(Math.atan2(aim.aimY, aim.aimX));
        aim.aimed = true;
    }
}
