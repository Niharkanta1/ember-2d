package dev.ember2d.particles;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import dev.ember2d.core.Phase;
import dev.ember2d.core.PublicApi;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import java.util.Objects;

/**
 * Runs the effects of {@link ParticleSource} entities (F-014, I-510), in {@code LATE} after movement: starts each
 * effect, keeps it at the entity, frees it when the entity is removed, and destroys the entity when the effect
 * completes if {@link ParticleSource#despawnWhenDone} is set. {@link Particles#update} still advances the effects.
 */
@PublicApi
public final class ParticleSystem extends EntitySystem {

    private final Particles particles;
    private final IntMap<Particles.Effect> running = new IntMap<>();
    private final IntMap<Integer> generations = new IntMap<>();
    private final IntArray gone = new IntArray();

    public ParticleSystem(World world, Particles particles) {
        super(Phase.LATE, world.family(ParticleSource.class, Transform.class));
        this.particles = Objects.requireNonNull(particles, "particles");
    }

    @Override
    protected void process(int entity, float delta) {
        ParticleSource source = world().get(entity, ParticleSource.class);
        Transform t = world().get(entity, Transform.class);
        Particles.Effect effect = running.get(entity);
        if (effect != null && (!effect.isValid() || effect.generation() != generations.get(entity))) {
            running.remove(entity);   // completed and freed by Particles.update (the handle may be reused)
            generations.remove(entity);
            effect = null;
            source.finished = true;
            if (source.despawnWhenDone) {
                world().destroy(entity);
                return;
            }
        }
        if (effect == null) {
            if (source.finished || source.effect.isEmpty()) {
                return;
            }
            effect = particles.spawn(source.effect, t.position.x + source.offsetX, t.position.y + source.offsetY);
            running.put(entity, effect);
            generations.put(entity, effect.generation());
            return;
        }
        effect.position(t.position.x + source.offsetX, t.position.y + source.offsetY);
    }

    @Override
    protected void end() {
        gone.clear();
        for (IntMap.Entry<Particles.Effect> e : running) {
            if (!world().isAlive(e.key) || world().get(e.key, ParticleSource.class) == null) {
                gone.add(e.key);
            }
        }
        for (int i = 0; i < gone.size; i++) {
            Particles.Effect effect = running.remove(gone.get(i));
            Integer generation = generations.remove(gone.get(i));
            if (effect.isValid() && generation != null && effect.generation() == generation) {
                effect.free();   // the entity went: its effect goes with it
            }
        }
    }
}
