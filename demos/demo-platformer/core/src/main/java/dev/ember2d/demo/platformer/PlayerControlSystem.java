package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;

/** Fixed-step movement for {@link PlayerControl}: the {@code move} axis, or towards a tapped target. */
public final class PlayerControlSystem extends EntitySystem {

    private final Input input;
    private final Vector2 move = new Vector2();

    public PlayerControlSystem(World world, Input input) {
        super(Phase.FIXED, world.family(Transform.class, PlayerControl.class));
        this.input = input;
    }

    @Override
    protected void process(int entity, float delta) {
        Transform transform = world().get(entity, Transform.class);
        PlayerControl control = world().get(entity, PlayerControl.class);
        input.axis2("move", move);
        if (!move.isZero()) {
            control.hasTarget = false;   // keys win over a tapped target
        } else if (control.hasTarget) {
            control.target.y = MathUtils.clamp(control.target.y, control.minY, control.maxY);   // reachable
            move.set(control.target).sub(transform.position);
            float step = control.speed * delta;
            if (move.len() <= step) {
                transform.position.set(control.target);
                control.hasTarget = false;
                move.setZero();
            } else {
                move.nor();
            }
        }
        transform.position.mulAdd(move, control.speed * delta);
        transform.position.y = MathUtils.clamp(transform.position.y, control.minY, control.maxY);
    }
}
