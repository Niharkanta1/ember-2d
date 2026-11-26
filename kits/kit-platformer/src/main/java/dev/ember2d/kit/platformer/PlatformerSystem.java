package dev.ember2d.kit.platformer;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.animation.AnimationStateMachine;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.Direction;
import dev.ember2d.collision.MoveResult;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.render.Sprite;
import java.util.Map;

/**
 * Fixed-step platformer movement for every {@link PlatformerBody} with an {@code Actor} (F-015): accelerated running,
 * gravity with a faster fall, variable jump height, coyote time, jump buffering and drop-through on one-way platforms,
 * plus the optional air jumps, dash, wall slide, wall jump, ladders and platform momentum. Publishes {@link Jumped},
 * {@link Landed} and {@link Dashed}, flips the {@code Sprite} to the facing direction, and writes the state machine
 * parameters its definition declares ({@code grounded}, {@code speedX}, {@code velY}, {@code wallSliding},
 * {@code dashing}, {@code climbing}; triggers {@code airJump}, {@code wallJump}).
 */
@PublicApi
public final class PlatformerSystem extends EntitySystem {

    private static final int DROP_TICKS = 6;
    private static final int PARAMETER_REFRESH_TICKS = 60;

    private final Collision collision;
    private final Input input;
    private final EventBus events;
    private final Vector2 move = new Vector2();
    private final Rectangle box = new Rectangle();
    private final IntArray hits = new IntArray();

    public PlatformerSystem(World world, Collision collision, Input input, EventBus events) {
        super(Phase.FIXED, world.family(Transform.class, PlatformerBody.class, Actor.class));
        this.collision = collision;
        this.input = input;
        this.events = events;
    }

    @Override
    protected void process(int entity, float delta) {
        PlatformerBody body = world().get(entity, PlatformerBody.class);
        boolean grounded = collision.isTouching(entity, Direction.DOWN);
        body.ticksSinceGrounded = grounded ? 0 : Math.min(body.ticksSinceGrounded + 1, PlatformerBody.NEVER);
        if (grounded) {
            body.airJumpsLeft = body.airJumps;
            body.airDashesLeft = body.airDashes;
        }
        boolean pressed = !body.locked && justPressed(body.jumpAction);
        body.ticksSinceJumpPressed = pressed ? 0 : Math.min(body.ticksSinceJumpPressed + 1, PlatformerBody.NEVER);
        if (body.locked || !input.hasAction(body.moveAxis)) {
            move.setZero();
        } else {
            input.axis2(body.moveAxis, move);
        }
        int wall = 0;
        if (body.wallSlide || body.wallJump) {
            wall = collision.isTouching(entity, Direction.LEFT) ? -1
                    : collision.isTouching(entity, Direction.RIGHT) ? 1 : 0;
        }
        if (body.wallLockTicks > 0) {
            body.wallLockTicks--;
            if (Math.signum(move.x) == body.wallLockDirection) {
                move.x = 0f;   // just jumped off this wall: no climbing it
            }
        }
        if (body.ladders) {
            updateClimbing(entity, body, grounded);
        } else {
            body.climbing = false;
        }

        if (pressed && grounded && move.y < -0.5f && !body.climbing) {
            world().get(entity, Actor.class).dropThroughTicks = DROP_TICKS;   // down + jump: drop through
            body.ticksSinceJumpPressed = PlatformerBody.NEVER;
        }
        if (body.dashCooldown > 0) {
            body.dashCooldown--;
        }
        if (body.dash && body.dashTicksLeft == 0 && body.dashCooldown == 0 && !body.locked && !body.climbing
                && (grounded || body.airDashesLeft > 0) && justPressed(body.dashAction)) {
            body.dashDirection = move.x != 0f ? (int) Math.signum(move.x) : body.facing;
            body.dashTicksLeft = body.dashTicks;
            if (!grounded) {
                body.airDashesLeft--;
            }
            events.publish(new Dashed(entity, body.dashDirection));
        }

        boolean dashEnded = false;
        if (body.dashTicksLeft > 0) {
            body.dashTicksLeft--;
            body.vx = body.dashDirection * body.dashSpeed;
            body.vy = 0f;
            dashEnded = body.dashTicksLeft == 0;
        } else {
            jump(entity, body, grounded, wall);
            if (body.jumpCutAvailable && body.vy > 0f && !(input.hasAction(body.jumpAction)
                    && input.pressed(body.jumpAction))) {
                body.vy *= body.jumpCut;   // released early: a lower jump
                body.jumpCutAvailable = false;
            }
            if (body.vy <= 0f) {
                body.jumpCutAvailable = false;
            }
            if (body.climbing) {
                body.vx = MathUtils.clamp(move.x, -1f, 1f) * body.maxSpeed * 0.5f;
                body.vy = MathUtils.clamp(move.y, -1f, 1f) * body.climbSpeed;
            } else {
                float target = MathUtils.clamp(move.x, -1f, 1f) * body.maxSpeed;
                float rate = target == 0f ? (grounded ? body.groundDecel : body.airDecel)
                        : (grounded ? body.groundAccel : body.airAccel);
                body.vx = approach(body.vx, target, rate * delta);
                float g = body.vy < 0f ? body.gravity * body.fallMultiplier : body.gravity;
                body.vy = Math.max(body.vy - g * delta, -body.maxFall);
            }
        }
        body.wallSliding = body.wallSlide && !grounded && !body.climbing && body.vy < 0f && wall != 0
                && Math.signum(move.x) == wall;
        if (body.wallSliding) {
            body.vy = Math.max(body.vy, -body.wallSlideSpeed);
        }

        MoveResult r = collision.moveAndSlide(entity, body.vx * delta, body.vy * delta);
        if (r.hitY) {
            body.vy = 0f;
            if (body.climbing && move.y < 0f) {
                body.climbing = false;   // climbed down to the ground
            }
        }
        if (r.hitX) {
            body.vx = 0f;
        }
        if (dashEnded) {
            body.dashCooldown = body.dashCooldownTicks;   // after the last dash tick's move: back to running speed
            body.vx = Math.signum(body.vx) * Math.min(Math.abs(body.vx), body.maxSpeed);
        }
        boolean groundedAfter = collision.isTouching(entity, Direction.DOWN);
        if (groundedAfter && !body.wasGrounded) {
            events.publish(new Landed(entity));
        }
        body.wasGrounded = groundedAfter;
        body.grounded = groundedAfter;
        animate(entity, body);
    }

    private void jump(int entity, PlatformerBody body, boolean grounded, int wall) {
        if (body.ticksSinceJumpPressed > body.bufferTicks) {
            return;
        }
        JumpKind kind;
        if (body.climbing) {
            body.climbing = false;
            body.vy = body.jumpSpeed;
            kind = JumpKind.GROUND;
        } else if (body.ticksSinceGrounded <= body.coyoteTicks) {
            kind = body.ticksSinceGrounded == 0 ? JumpKind.GROUND : JumpKind.COYOTE;
            body.vy = body.jumpSpeed;
            if (body.inheritPlatformVelocity) {
                inheritPlatform(entity, body);
            }
        } else if (body.wallJump && wall != 0 && !grounded) {
            kind = JumpKind.WALL;
            body.vy = body.wallJumpY;
            body.vx = -wall * body.wallJumpX;
            body.wallLockTicks = body.wallJumpLockTicks;
            body.wallLockDirection = wall;
            trigger(entity, "wallJump");
        } else if (body.airJumpsLeft > 0) {
            kind = JumpKind.AIR;
            body.airJumpsLeft--;
            body.vy = body.airJumpSpeed;
            trigger(entity, "airJump");
        } else {
            return;
        }
        body.ticksSinceJumpPressed = PlatformerBody.NEVER;
        body.ticksSinceGrounded = PlatformerBody.NEVER;   // no second jump from the same coyote window
        body.jumpCutAvailable = true;
        events.publish(new Jumped(entity, kind));
    }

    /** Adds the velocity of the moving platform the body stood on. */
    private void inheritPlatform(int entity, PlatformerBody body) {
        int below = world().get(entity, Actor.class).hitEntityY;
        MovingPlatform platform = below == World.NONE ? null : world().get(below, MovingPlatform.class);
        if (platform != null) {
            body.vx += platform.velocityX();
            body.vy += Math.max(0f, platform.velocityY());
        }
    }

    private void updateClimbing(int entity, PlatformerBody body, boolean grounded) {
        boolean onLadder = false;
        hits.clear();
        collision.overlapBox(collision.bounds(entity, box), ~0, hits);
        for (int i = 0; i < hits.size; i++) {
            if (world().has(hits.get(i), Ladder.class)) {
                onLadder = true;
                break;
            }
        }
        if (!onLadder) {
            body.climbing = false;
        } else if (!body.climbing && !body.locked && Math.abs(move.y) > 0.5f && !(grounded && move.y < 0f)) {
            body.climbing = true;
            body.vx = 0f;
            body.vy = 0f;
            body.jumpCutAvailable = false;
        }
    }

    private boolean justPressed(String action) {
        return input.hasAction(action) && input.justPressed(action);
    }

    static float approach(float value, float target, float step) {
        return value < target ? Math.min(value + step, target) : Math.max(value - step, target);
    }

    /** Faces the moving direction and writes the parameters the entity's state machine declares. */
    private void animate(int entity, PlatformerBody body) {
        if (Math.abs(body.vx) > 0.05f) {
            body.facing = body.vx < 0f ? -1 : 1;
            Sprite sprite = world().get(entity, Sprite.class);
            if (sprite != null) {
                sprite.flipX = body.vx < 0f;
            }
        }
        AnimationStateMachine asm = world().get(entity, AnimationStateMachine.class);
        if (asm == null) {
            return;
        }
        Map<String, String> params = parameters(body, asm);
        setBool(asm, params, "grounded", body.grounded);
        setFloat(asm, params, "speedX", Math.abs(body.vx));
        setFloat(asm, params, "velY", body.vy);
        setBool(asm, params, "wallSliding", body.wallSliding);
        setBool(asm, params, "dashing", body.dashing());
        setBool(asm, params, "climbing", body.climbing);
    }

    private void trigger(int entity, String name) {
        AnimationStateMachine asm = world().get(entity, AnimationStateMachine.class);
        PlatformerBody body = world().get(entity, PlatformerBody.class);
        if (asm != null && "trigger".equals(parameters(body, asm).get(name))) {
            asm.trigger(name);
        }
    }

    /** The definition's parameters, looked up again now and then (and until the definition is loaded). */
    private static Map<String, String> parameters(PlatformerBody body, AnimationStateMachine asm) {
        if (body.asmParameters.isEmpty() || ++body.asmParameterTicks >= PARAMETER_REFRESH_TICKS) {
            body.asmParameters = asm.parameterTypes();
            body.asmParameterTicks = 0;
        }
        return body.asmParameters;
    }

    private static void setBool(AnimationStateMachine asm, Map<String, String> params, String name, boolean value) {
        if ("bool".equals(params.get(name))) {
            asm.setBool(name, value);
        }
    }

    private static void setFloat(AnimationStateMachine asm, Map<String, String> params, String name, float value) {
        if ("float".equals(params.get(name))) {
            asm.setFloat(name, value);
        }
    }
}
