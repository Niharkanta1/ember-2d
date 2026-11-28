package dev.ember2d.kit.topdown.vision;

import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;
import dev.ember2d.kit.topdown.shooter.Aim;
import dev.ember2d.render.Sprite;
import java.util.Objects;

/**
 * Updates the field of view from the {@link Viewer} and hides {@link HiddenInFog} entities outside it (F-030). Runs
 * in {@code LATE}, after movement and aiming, so the fog and the hidden sprites match the drawn frame. Without a
 * viewer the field is not updated and nothing is hidden.
 */
@PublicApi
public final class VisionSystem extends EntitySystem {

    private final VisionField field;
    private final EventBus events;
    private final Family viewers;
    private boolean hasViewer;

    public VisionSystem(World world, VisionField field, EventBus events) {
        super(Phase.LATE, world.family(Transform.class, HiddenInFog.class));
        this.field = Objects.requireNonNull(field, "field");
        this.events = Objects.requireNonNull(events, "events");
        this.viewers = world.family(Transform.class, Viewer.class);
    }

    public VisionField field() {
        return field;
    }

    @Override
    protected void begin() {
        IntArray found = viewers.entities();
        hasViewer = found.size > 0;
        if (!hasViewer) {
            return;
        }
        int viewer = found.first();
        Transform t = world().get(viewer, Transform.class);
        Viewer v = world().get(viewer, Viewer.class);
        Aim aim = world().get(viewer, Aim.class);
        float angle = aim != null ? aim.angle : v.angle;
        field.update(t.position.x, t.position.y + v.eyeY, angle);
    }

    @Override
    protected void process(int entity, float delta) {
        HiddenInFog fog = world().get(entity, HiddenInFog.class);
        Transform t = world().get(entity, Transform.class);
        boolean seen = !hasViewer || field.isVisible(t.position.x, t.position.y + fog.centerY, fog.radius);
        Sprite sprite = world().get(entity, Sprite.class);
        if (sprite != null) {
            sprite.visible = seen;
        }
        if (!fog.known) {
            fog.known = true;
            fog.inView = seen;
            return;
        }
        if (seen != fog.inView) {
            fog.inView = seen;
            events.queue(seen ? new CameIntoView(entity) : new WentOutOfView(entity));
        }
    }
}
