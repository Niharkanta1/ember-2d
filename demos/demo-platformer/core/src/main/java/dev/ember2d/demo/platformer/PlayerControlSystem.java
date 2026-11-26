package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.MathUtils;
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
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.render.Sprite;

/**
 * Fixed-step platformer movement for {@link PlayerControl} (F-015, I-170): accelerated running, gravity with a faster
 * fall, variable jump height, coyote time, jump buffering and drop-through on one-way platforms. Publishes
 * {@link Jumped} and {@link Landed}; feeds the animation state machine ({@code anim/frog.asm.json} picks the clip).
 */
public final class PlayerControlSystem extends EntitySystem {

    private static final int DROP_TICKS = 6;

    private final Collision collision;
    private final Input input;
    private final EventBus events;
    private final Vector2 move = new Vector2();

    public PlayerControlSystem(World world, Collision collision, Input input, EventBus events) {
        super(Phase.FIXED, world.family(Transform.class, PlayerControl.class, Actor.class));
        this.collision = collision;
        this.input = input;
        this.events = events;
    }

    @Override
    protected void process(int entity, float delta) {
        PlayerControl body = world().get(entity, PlayerControl.class);
        boolean grounded = collision.isTouching(entity, Direction.DOWN);
        body.ticksSinceGrounded = grounded ? 0 : Math.min(body.ticksSinceGrounded + 1, PlayerControl.NEVER);
        boolean pressed = !body.locked && input.justPressed("jump");
        body.ticksSinceJumpPressed = pressed ? 0 : Math.min(body.ticksSinceJumpPressed + 1, PlayerControl.NEVER);
        if (body.locked) {
            move.setZero();
        } else {
            input.axis2("move", move);
        }

        if (pressed && grounded && move.y < -0.5f) {
            world().get(entity, Actor.class).dropThroughTicks = DROP_TICKS;   // down + jump: drop through
            body.ticksSinceJumpPressed = PlayerControl.NEVER;
        }
        if (body.ticksSinceJumpPressed <= body.bufferTicks && body.ticksSinceGrounded <= body.coyoteTicks) {
            body.vy = body.jumpSpeed;
            body.ticksSinceJumpPressed = PlayerControl.NEVER;
            body.ticksSinceGrounded = PlayerControl.NEVER;   // no second jump from the same coyote window
            body.jumpCutAvailable = true;
            events.publish(new Jumped(entity));
        }
        if (body.jumpCutAvailable && body.vy > 0f && !input.pressed("jump")) {
            body.vy *= body.jumpCut;   // released early: a lower jump
            body.jumpCutAvailable = false;
        }
        if (body.vy <= 0f) {
            body.jumpCutAvailable = false;
        }

        float target = MathUtils.clamp(move.x, -1f, 1f) * body.maxSpeed;
        float rate = target == 0f ? (grounded ? body.groundDecel : body.airDecel)
                : (grounded ? body.groundAccel : body.airAccel);
        body.vx = approach(body.vx, target, rate * delta);
        float g = body.vy < 0f ? body.gravity * body.fallMultiplier : body.gravity;
        body.vy = Math.max(body.vy - g * delta, -body.maxFall);

        MoveResult r = collision.moveAndSlide(entity, body.vx * delta, body.vy * delta);
        if (r.hitY) {
            body.vy = 0f;
        }
        if (r.hitX) {
            body.vx = 0f;
        }
        boolean groundedAfter = collision.isTouching(entity, Direction.DOWN);
        if (groundedAfter && !body.wasGrounded) {
            events.publish(new Landed(entity));
        }
        body.wasGrounded = groundedAfter;
        animate(entity, body, groundedAfter);
    }

    static float approach(float value, float target, float step) {
        return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
    }

    /** Faces the running direction and feeds the animation state machine. */
    private void animate(int entity, PlayerControl body, boolean grounded) {
        if (Math.abs(body.vx) > 0.05f && world().has(entity, Sprite.class)) {
            world().get(entity, Sprite.class).flipX = body.vx < 0f;
        }
        AnimationStateMachine asm = world().get(entity, AnimationStateMachine.class);
        if (asm != null) {
            asm.setBool("grounded", grounded);
            asm.setFloat("speedX", Math.abs(body.vx));
            asm.setFloat("velY", body.vy);
        }
    }
}
