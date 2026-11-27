package dev.ember2d.camera;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;

/**
 * Moves the camera for every {@link CameraScroll} (F-004, I-310), in {@code LATE}: by its speed on scaled time, keeping
 * the view inside its bounds; then moves every entity tagged {@code clampTag} back inside the view (both its current and
 * its previous position, so interpolation shows no jump). Publishes {@link ScrollFinished} once when the scroll reaches
 * the edge of its bounds.
 */
@PublicApi
public final class CameraScrollSystem extends EntitySystem {

    private final CameraRig rig;
    private final EventBus events;
    private final IntArray tagged = new IntArray();

    public CameraScrollSystem(World world, CameraRig rig, EventBus events) {
        super(Phase.LATE, world.family(CameraScroll.class));
        this.rig = rig;
        this.events = events;
    }

    @Override
    protected void process(int entity, float delta) {
        CameraScroll scroll = world().get(entity, CameraScroll.class);
        float halfW = rig.viewWidth() / 2f;
        float halfH = rig.viewHeight() / 2f;
        if (!scroll.finished) {
            rig.position.x += scroll.speedX * delta;
            rig.position.y += scroll.speedY * delta;
            Rectangle b = scroll.bounds;
            if (b != null) {
                boolean stopX = clampAxis(true, b.x + halfW, b.x + b.width - halfW, scroll.speedX);
                boolean stopY = clampAxis(false, b.y + halfH, b.y + b.height - halfH, scroll.speedY);
                boolean movingX = scroll.speedX != 0f;
                boolean movingY = scroll.speedY != 0f;
                if ((movingX || movingY) && (!movingX || stopX) && (!movingY || stopY)) {
                    scroll.finished = true;
                    events.publish(new ScrollFinished(entity));
                }
            }
        }
        if (scroll.clampTag == null || scroll.clampTag.isEmpty()) {
            return;
        }
        float minX = rig.position.x - halfW + scroll.margin;
        float maxX = rig.position.x + halfW - scroll.margin;
        float minY = rig.position.y - halfH + scroll.margin;
        float maxY = rig.position.y + halfH - scroll.margin;
        world().findByTag(scroll.clampTag, tagged);
        for (int i = 0; i < tagged.size; i++) {
            Transform t = world().get(tagged.get(i), Transform.class);
            if (t == null) {
                continue;
            }
            float x = Math.max(minX, Math.min(maxX, t.position.x));
            float y = Math.max(minY, Math.min(maxY, t.position.y));
            if (x != t.position.x || y != t.position.y) {
                t.previous.add(x - t.position.x, y - t.position.y);
                t.position.set(x, y);
            }
        }
    }

    /** Keeps the camera centre on one axis between min and max; true when it was stopped in its moving direction. */
    private boolean clampAxis(boolean xAxis, float min, float max, float speed) {
        float value = xAxis ? rig.position.x : rig.position.y;
        float clamped = min > max ? (min + max) / 2f : Math.max(min, Math.min(max, value));
        if (xAxis) {
            rig.position.x = clamped;
        } else {
            rig.position.y = clamped;
        }
        return (speed > 0f && clamped >= max) || (speed < 0f && clamped <= min) || min > max;
    }
}
