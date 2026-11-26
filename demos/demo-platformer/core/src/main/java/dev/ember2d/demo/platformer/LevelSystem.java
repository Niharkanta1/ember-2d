package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.animation.AnimationStateMachine;
import dev.ember2d.animation.Animator;
import dev.ember2d.collision.Actor;
import dev.ember2d.collision.Collider;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.TriggerEntered;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.platformer.Patroller;
import dev.ember2d.kit.platformer.PlatformerBody;
import dev.ember2d.event.EventBus;
import dev.ember2d.render.Sprite;
import java.util.Locale;

/**
 * The rules of the demo level (F-015, I-170), after collision in the fixed step: enemies hurt on contact and are
 * defeated by landing on them, hazards hurt, a hurt player respawns at the last checkpoint after a short hit animation
 * and then blinks while invulnerable, falling out of the map counts as hurt, and the goal ends the level: the player
 * stops and {@link LevelCompleted} is published (the scene shows the results, I-220).
 */
public final class LevelSystem extends EntitySystem {

    /** Seconds the hit animation plays before the respawn. */
    static final float HURT_SECONDS = 0.42f;
    /** Seconds without damage after a respawn. */
    static final float INVULNERABLE_SECONDS = 1f;
    static final float BLINK_SECONDS = 0.1f;
    /** Below this height (world units) the player has fallen out of the map. */
    static final float FALL_LIMIT = -2f;
    private final Collision collision;
    private final EventBus events;
    private final Family enemies;
    private final Family checkpoints;
    private final Vector2 respawn = new Vector2();
    private final Rectangle box = new Rectangle();
    private final Rectangle other = new Rectangle();
    private final IntArray hits = new IntArray();
    private boolean respawnSet;
    private float hurtTimer;
    private float invulnerable;
    private float levelTime;
    private int deaths;
    private boolean complete;

    public LevelSystem(World world, Collision collision, EventBus events) {
        super(Phase.FIXED, world.family(Transform.class, PlatformerBody.class));
        this.collision = collision;
        this.events = events;
        this.enemies = world.family(Enemy.class);
        this.checkpoints = world.family(Checkpoint.class, Animator.class);
        events.on(TriggerEntered.class, this::triggerEntered);
    }

    /** Deaths so far. */
    public int deaths() {
        return deaths;
    }

    /** Game seconds since the level started (stops at the goal). */
    public float levelTime() {
        return levelTime;
    }

    /** Whether the goal was reached. */
    public boolean complete() {
        return complete;
    }

    /** Where the player respawns. */
    public Vector2 respawnPoint() {
        return respawn;
    }

    @Override
    protected void process(int player, float delta) {
        PlatformerBody body = world().get(player, PlatformerBody.class);
        Transform transform = world().get(player, Transform.class);
        if (!respawnSet) {
            respawn.set(transform.position);
            respawnSet = true;
        }
        updateEnemies(delta);
        updateCheckpoints();
        if (complete) {
            body.locked = true;
            body.vx = 0f;
            return;
        }
        levelTime += delta;
        if (hurtTimer > 0f) {
            hurtTimer -= delta;
            if (hurtTimer <= 0f) {
                transform.teleport(respawn.x, respawn.y);
                body.vx = 0f;
                body.vy = 0f;
                body.locked = false;
                invulnerable = INVULNERABLE_SECONDS;
            }
            return;
        }
        if (invulnerable > 0f) {
            invulnerable = Math.max(0f, invulnerable - delta);
            Sprite sprite = world().get(player, Sprite.class);
            if (sprite != null) {
                sprite.tint.a = invulnerable > 0f && ((int) (invulnerable / BLINK_SECONDS)) % 2 == 0 ? 0.35f : 1f;
            }
        }
        if (transform.position.y < FALL_LIMIT) {
            hurt(player, true);
            return;
        }
        touch(player, body, transform);
    }

    /** Enemies and hazards overlapping the player, every step: standing in a hazard hurts once blinking ends. */
    private void touch(int player, PlatformerBody body, Transform transform) {
        hits.clear();
        collision.overlapBox(collision.bounds(player, box), ~0, hits);
        for (int i = 0; i < hits.size; i++) {
            int e = hits.get(i);
            if (invulnerable <= 0f && world().has(e, Hazard.class)) {
                hurt(player, false);
                return;
            }
            Enemy enemy = world().get(e, Enemy.class);
            if (enemy == null || enemy.defeated) {
                continue;
            }
            collision.bounds(e, other);
            if (body.vy < 0f && transform.position.y >= other.y + other.height * 0.5f) {
                defeat(e, enemy);
                body.vy = enemy.stompBounce;
            } else if (invulnerable <= 0f) {
                hurt(player, false);
                return;
            }
        }
    }

    private void defeat(int e, Enemy enemy) {
        events.publish(new EnemyStomped(e));
        enemy.defeated = true;
        enemy.defeatedTime = 0f;
        world().remove(e, Patroller.class);
        world().remove(e, Actor.class);
        world().remove(e, Collider.class);
        Animator animator = world().get(e, Animator.class);
        if (animator != null) {
            animator.play("hit");
        }
    }

    private void updateEnemies(float delta) {
        IntArray list = enemies.entities();
        for (int i = list.size - 1; i >= 0; i--) {
            int e = list.get(i);
            Enemy enemy = world().get(e, Enemy.class);
            if (enemy.defeated) {
                enemy.defeatedTime += delta;
                if (enemy.defeatedTime >= enemy.defeatSeconds) {
                    world().destroy(e);
                }
            }
        }
    }

    private void updateCheckpoints() {
        IntArray list = checkpoints.entities();
        for (int i = 0; i < list.size; i++) {
            int e = list.get(i);
            Animator animator = world().get(e, Animator.class);
            if (world().get(e, Checkpoint.class).active && "checkpoint_out".equals(animator.clip)
                    && animator.isFinished()) {
                animator.play("checkpoint_idle");   // the flag is out: wave
            }
        }
    }

    private void hurt(int player, boolean fell) {
        if (hurtTimer > 0f || complete() || (!fell && invulnerable > 0f)) {
            return;
        }
        PlatformerBody body = world().get(player, PlatformerBody.class);
        deaths++;
        events.publish(new PlayerHurt(player));
        hurtTimer = HURT_SECONDS;
        body.locked = true;
        body.vx = 0f;
        body.vy = fell ? 0f : 6f;   // a small hop
        AnimationStateMachine asm = world().get(player, AnimationStateMachine.class);
        if (asm != null) {
            asm.trigger("hit");
        }
    }

    private void triggerEntered(TriggerEntered e) {
        int player = e.actor();
        if (!world().has(player, PlatformerBody.class)) {
            return;   // enemies walk through triggers too
        }
        Checkpoint checkpoint = world().get(e.trigger(), Checkpoint.class);
        if (checkpoint != null && !checkpoint.active && !complete()) {
            checkpoint.active = true;
            events.publish(new CheckpointReached(e.trigger()));
            Vector2 at = world().get(e.trigger(), Transform.class).position;
            respawn.set(at);
            respawnSet = true;
            Animator animator = world().get(e.trigger(), Animator.class);
            if (animator != null) {
                animator.play("checkpoint_out");
            }
        }
        Goal goal = world().get(e.trigger(), Goal.class);
        if (goal != null && !complete() && hurtTimer <= 0f) {
            reachGoal(player, e.trigger());
        }
    }

    private void reachGoal(int player, int entity) {
        PlatformerBody body = world().get(player, PlatformerBody.class);
        body.locked = true;
        body.vx = 0f;
        complete = true;
        Animator animator = world().get(entity, Animator.class);
        if (animator != null) {
            animator.play("goal_pressed");
        }
        events.publish(new LevelCompleted(levelTime, deaths));
    }

    /** Minutes and seconds, "1:05". */
    static String clock(float seconds) {
        int s = (int) seconds;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }
}
