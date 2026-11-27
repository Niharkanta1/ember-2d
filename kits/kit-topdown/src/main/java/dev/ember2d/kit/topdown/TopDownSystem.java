package dev.ember2d.kit.topdown;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.MoveResult;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.common.Damaged;
import dev.ember2d.render.Sprite;

/**
 * Top-down movement for every {@link TopDownBody} with an {@code Actor} (F-016), in {@code FIXED}. Reads the
 * {@code move} 2D axis (clamped to length 1, so diagonals are not faster), accelerates towards it, slides along walls
 * with {@code moveAndSlide}, and applies knockback from kit-common {@code Damaged} events. In grid mode it moves one
 * tile per step and publishes {@link StepFinished}. Create it in a scene's {@code enter}: it subscribes to events.
 */
@PublicApi
public final class TopDownSystem extends EntitySystem {

    /** The input axis this system reads. */
    public static final String MOVE_AXIS = "move";

    private final Collision collision;
    private final Input input;
    private final EventBus events;
    private final Vector2 move = new Vector2();

    public TopDownSystem(World world, Collision collision, Input input, EventBus events) {
        super(Phase.FIXED, world.family(TopDownBody.class, Actor.class, Transform.class));
        this.collision = collision;
        this.input = input;
        this.events = events;
        events.on(Damaged.class, e -> {
            TopDownBody body = world().isAlive(e.target()) ? world().get(e.target(), TopDownBody.class) : null;
            if (body != null) {
                knockback(e.target(), e.knockbackX() * body.knockbackScale, e.knockbackY() * body.knockbackScale);
            }
        });
    }

    /** Adds knockback velocity to a body (it fades by {@link TopDownBody#knockbackDecay}). */
    public void knockback(int entity, float x, float y) {
        TopDownBody body = world().get(entity, TopDownBody.class);
        if (body != null) {
            body.knockbackX += x;
            body.knockbackY += y;
        }
    }

    @Override
    protected void process(int entity, float delta) {
        TopDownBody body = world().get(entity, TopDownBody.class);
        if (body.locked) {
            move.setZero();
        } else if (!body.useInput) {
            move.set(body.moveX, body.moveY);
            if (move.len2() > 1f) {
                move.nor();
            }
        } else {
            input.axis2(MOVE_AXIS, move);
            if (move.len2() > 1f) {
                move.nor();   // diagonals are not faster
            }
        }
        if (body.gridStep) {
            grid(entity, body, delta);
        } else {
            free(entity, body, delta);
        }
        fadeKnockback(entity, body, delta);
        Sprite sprite = world().get(entity, Sprite.class);
        if (body.flipSprite && sprite != null && body.facingX != 0f) {
            sprite.flipX = body.facingX < 0f;
        }
    }

    private void free(int entity, TopDownBody body, float delta) {
        if (!move.isZero()) {
            body.facingX = move.x;
            body.facingY = move.y;
            float len = (float) Math.sqrt(body.facingX * body.facingX + body.facingY * body.facingY);
            body.facingX /= len;
            body.facingY /= len;
            body.vx = approach(body.vx, move.x * body.maxSpeed, body.acceleration * delta);
            body.vy = approach(body.vy, move.y * body.maxSpeed, body.acceleration * delta);
        } else {
            // Friction along the velocity, so a diagonal stop stays diagonal.
            float speed = (float) Math.sqrt(body.vx * body.vx + body.vy * body.vy);
            if (speed > 0f) {
                float slower = Math.max(0f, speed - body.friction * delta) / speed;
                body.vx *= slower;
                body.vy *= slower;
            }
        }
        if (body.vx == 0f && body.vy == 0f) {
            body.moving = false;
            return;
        }
        MoveResult r = collision.moveAndSlide(entity, body.vx * delta, body.vy * delta);
        if (r.hitX) {
            body.vx = 0f;
        }
        if (r.hitY) {
            body.vy = 0f;
        }
        body.moving = true;
    }

    private void grid(int entity, TopDownBody body, float delta) {
        if (body.stepLeft <= 0f) {
            if (move.isZero()) {
                body.moving = false;
                return;
            }
            boolean horizontal = Math.abs(move.x) >= Math.abs(move.y);
            body.stepX = horizontal ? Math.signum(move.x) : 0f;
            body.stepY = horizontal ? 0f : Math.signum(move.y);
            body.facingX = body.stepX;
            body.facingY = body.stepY;
            body.stepLeft = body.tileSize;
        }
        float distance = Math.min(body.stepLeft, body.stepsPerSecond * body.tileSize * delta);
        MoveResult r = collision.moveAndCollide(entity, body.stepX * distance, body.stepY * distance);
        body.stepLeft -= distance;
        body.moving = true;
        if (r.hitX || r.hitY || body.stepLeft <= 1e-5f) {
            body.stepLeft = 0f;
            Transform t = world().get(entity, Transform.class);
            events.publish(new StepFinished(entity, (int) Math.floor(t.position.x / body.tileSize + 1e-4f),
                    (int) Math.floor(t.position.y / body.tileSize + 1e-4f)));
        }
    }

    private void fadeKnockback(int entity, TopDownBody body, float delta) {
        if (body.knockbackX == 0f && body.knockbackY == 0f) {
            return;
        }
        MoveResult r = collision.moveAndSlide(entity, body.knockbackX * delta, body.knockbackY * delta);
        float speed = (float) Math.sqrt(body.knockbackX * body.knockbackX + body.knockbackY * body.knockbackY);
        float slower = Math.max(0f, speed - body.knockbackDecay * delta) / speed;
        body.knockbackX = r.hitX ? 0f : body.knockbackX * slower;
        body.knockbackY = r.hitY ? 0f : body.knockbackY * slower;
    }

    private static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
