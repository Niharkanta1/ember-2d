package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;

/** Fixed-step movement from the {@code move} axis for {@link PlayerControl}. */
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
        transform.position.mulAdd(move, control.speed * delta);
        transform.position.y = MathUtils.clamp(transform.position.y, control.minY, control.maxY);
    }
}
