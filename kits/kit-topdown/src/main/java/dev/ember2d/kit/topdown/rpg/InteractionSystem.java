package dev.ember2d.kit.topdown.rpg;

import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.input.Input;
import dev.ember2d.kit.topdown.TopDownBody;

/**
 * Focuses and uses {@link Interactable}s (F-017), in {@code UPDATE}. For each {@link Interactor} the target is the
 * enabled interactable within its range and inside the cone around the interactor's facing (the {@code TopDownBody}
 * facing; every direction without a body), the nearest first (score = distance x (1 + angle / 180)); closer than 0.3
 * counts in every direction. Publishes {@link InteractionFocus} when the target changes and {@link Interacted} when the
 * interact action is pressed. A locked body has no target.
 */
@PublicApi
public final class InteractionSystem extends EntitySystem {

    /** Closer than this, the facing does not matter. */
    static final float TOUCHING = 0.3f;

    private final Input input;
    private final EventBus events;
    private final Family interactables;

    /** @param input reads each interactor's {@code inputAction}; may be null when only {@link #interact} is used */
    public InteractionSystem(World world, Input input, EventBus events) {
        super(Phase.UPDATE, world.family(Interactor.class, Transform.class));
        this.input = input;
        this.events = events;
        this.interactables = world.family(Interactable.class, Transform.class);
    }

    @Override
    protected void process(int entity, float delta) {
        Interactor interactor = world().get(entity, Interactor.class);
        TopDownBody body = world().get(entity, TopDownBody.class);
        int target = body != null && body.locked ? World.NONE : choose(entity, interactor, body);
        if (target != interactor.target) {
            interactor.target = target;
            String prompt = target == World.NONE ? "" : world().get(target, Interactable.class).prompt;
            events.publish(new InteractionFocus(entity, target, prompt));
        }
        if (input != null && target != World.NONE && !interactor.inputAction.isEmpty()
                && input.justPressed(interactor.inputAction)) {
            interact(entity);
        }
    }

    /**
     * Uses the interactor's focused interactable now (a touch button, a cutscene).
     *
     * @return false when nothing is focused
     */
    public boolean interact(int interactor) {
        Interactor i = world().get(interactor, Interactor.class);
        if (i == null || i.target == World.NONE || !world().isAlive(i.target)) {
            return false;
        }
        Interactable target = world().get(i.target, Interactable.class);
        if (target == null || !target.enabled) {
            return false;
        }
        events.publish(new Interacted(interactor, i.target, target.action));
        return true;
    }

    private int choose(int entity, Interactor interactor, TopDownBody body) {
        Transform t = world().get(entity, Transform.class);
        float fx = body == null ? 0f : body.facingX;
        float fy = body == null ? 0f : body.facingY;
        float facingLength = (float) Math.sqrt(fx * fx + fy * fy);
        int best = World.NONE;
        float bestScore = Float.MAX_VALUE;
        IntArray all = interactables.entities();
        for (int k = 0; k < all.size; k++) {
            int candidate = all.get(k);
            if (candidate == entity) {
                continue;
            }
            Interactable it = world().get(candidate, Interactable.class);
            if (!it.enabled) {
                continue;
            }
            Transform ct = world().get(candidate, Transform.class);
            float dx = ct.position.x + it.offsetX - t.position.x;
            float dy = ct.position.y + it.offsetY - t.position.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance > it.range) {
                continue;
            }
            float angle = 0f;
            if (distance >= TOUCHING && facingLength > 0f) {
                float cos = (dx * fx + dy * fy) / (distance * facingLength);
                angle = (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, cos))));
                if (angle > interactor.cone / 2f) {
                    continue;
                }
            }
            float score = distance * (1f + angle / 180f);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
