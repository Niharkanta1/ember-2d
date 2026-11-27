package dev.ember2d.kit.common;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.data.PrefabLibrary;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.event.EventBus;

/**
 * Runs {@link Spawner}s (F-021), in {@code UPDATE}: after the delay, {@code burst} instances every {@code interval}
 * seconds at the spawner's position plus the offset, until {@code total} have spawned, never more than {@code maxAlive}
 * alive (a burst spawns as many as fit). A spawner with {@code waitFor} starts when a {@link ZoneEntered} comes from the
 * zone with that name. Publishes {@link Spawned} and {@link SpawnerFinished}. Create it in a scene's {@code enter}.
 */
@PublicApi
public final class SpawnerSystem extends EntitySystem {

    private final PrefabLibrary prefabs;
    private final EventBus events;
    private final IntMap<IntArray> alive = new IntMap<>();

    public SpawnerSystem(World world, PrefabLibrary prefabs, EventBus events) {
        super(Phase.UPDATE, world.family(Spawner.class, Transform.class));
        this.prefabs = prefabs;
        this.events = events;
        events.on(ZoneEntered.class, e -> {
            IntArray spawners = family().entities();
            for (int i = 0; i < spawners.size; i++) {
                Spawner s = world().get(spawners.get(i), Spawner.class);
                if (!s.waitFor.isEmpty() && s.waitFor.equals(e.name())) {
                    s.started = true;
                }
            }
        });
    }

    @Override
    protected void process(int entity, float delta) {
        Spawner s = world().get(entity, Spawner.class);
        if (!s.started) {
            s.started = s.waitFor.isEmpty();
            if (!s.started) {
                return;
            }
        }
        if (s.total > 0 && s.spawned >= s.total) {
            return;
        }
        if (s.wait < 0f) {
            s.wait = s.delay;
        }
        s.wait -= delta;
        if (s.wait > 0f) {
            return;
        }
        int room = s.maxAlive - countAlive(entity);
        int count = Math.min(s.burst, room);
        if (s.total > 0) {
            count = Math.min(count, s.total - s.spawned);
        }
        if (count <= 0) {
            s.wait = 0f;   // full: try again next frame
            return;
        }
        Transform t = world().get(entity, Transform.class);
        IntArray mine = alive.get(entity);
        for (int i = 0; i < count; i++) {
            int spawned = prefabs.spawn(world(), s.prefab, t.position.x + s.offsetX, t.position.y + s.offsetY);
            mine.add(spawned);
            s.spawned++;
            events.publish(new Spawned(entity, spawned));
        }
        s.wait = s.interval;
        if (s.total > 0 && s.spawned >= s.total) {
            events.publish(new SpawnerFinished(entity));
        }
    }

    /** Living instances of a spawner (despawned and pooled-away instances no longer count). */
    private int countAlive(int spawner) {
        IntArray mine = alive.get(spawner);
        if (mine == null) {
            mine = new IntArray();
            alive.put(spawner, mine);
        }
        for (int i = mine.size - 1; i >= 0; i--) {
            int e = mine.get(i);
            Pooled pooled = world().get(e, Pooled.class);
            if (!world().isAlive(e) || (pooled != null && !pooled.inUse)) {
                mine.removeIndex(i);
            }
        }
        return mine.size;
    }
}
