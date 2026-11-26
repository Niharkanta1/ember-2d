package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.animation.Animator;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.Direction;
import dev.ember2d.collision.MoveResult;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.render.Sprite;

/** Fixed-step platformer movement for {@link PlayerControl}: walk, gravity, jump, drop through one-way platforms. */
public final class PlayerControlSystem extends EntitySystem {

    private static final int DROP_TICKS = 6;

    private final Collision collision;
    private final Input input;
    private final Vector2 move = new Vector2();

    public PlayerControlSystem(World world, Collision collision, Input input) {
        super(Phase.FIXED, world.family(Transform.class, PlayerControl.class, Actor.class));
        this.collision = collision;
        this.input = input;
    }

    /** Picks the clip from the movement (the animation state machine takes this over in I-135) and the facing. */
    private void animate(int entity, PlayerControl body, float moveX) {
        if (moveX != 0f && world().has(entity, Sprite.class)) {
            world().get(entity, Sprite.class).flipX = moveX < 0f;
        }
        if (!world().has(entity, Animator.class)) {
            return;
        }
        String clip;
        if (collision.isTouching(entity, Direction.DOWN)) {
            clip = moveX != 0f ? "run" : "idle";
        } else {
            clip = body.vy > 0f ? "jump" : "fall";
        }
        world().get(entity, Animator.class).playIfNotPlaying(clip);
    }

    @Override
    protected void process(int entity, float delta) {
        PlayerControl body = world().get(entity, PlayerControl.class);
        input.axis2("move", move);
        boolean grounded = collision.isTouching(entity, Direction.DOWN);
        if (input.justPressed("jump") && grounded) {
            if (move.y < -0.5f) {
                world().get(entity, Actor.class).dropThroughTicks = DROP_TICKS;
            } else {
                body.vy = body.jumpSpeed;
            }
        }
        body.vy = Math.max(body.vy - body.gravity * delta, -body.maxFall);
        MoveResult r = collision.moveAndSlide(entity, Math.signum(move.x) * body.speed * delta, body.vy * delta);
        if (r.hitY) {
            body.vy = 0f;
        }
        animate(entity, body, move.x);
    }
}
