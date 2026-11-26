package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.animation.AnimationStateMachine;
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

    /** Faces the walking direction and feeds the animation state machine (anim/hero.asm.json picks the clip). */
    private void animate(int entity, PlayerControl body, float moveX) {
        if (moveX != 0f && world().has(entity, Sprite.class)) {
            world().get(entity, Sprite.class).flipX = moveX < 0f;
        }
        boolean grounded = collision.isTouching(entity, Direction.DOWN);
        if (world().has(entity, AnimationStateMachine.class)) {
            AnimationStateMachine asm = world().get(entity, AnimationStateMachine.class);
            asm.setBool("grounded", grounded);
            asm.setFloat("speedX", Math.abs(Math.signum(moveX) * body.speed));
            asm.setFloat("velY", body.vy);
            if (grounded && !body.wasGrounded) {
                asm.trigger("land");   // a short squash on touchdown
            }
        }
        body.wasGrounded = grounded;
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
