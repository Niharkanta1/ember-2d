package dev.ember2d.particles;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import dev.ember2d.assets.Assets;
import dev.ember2d.core.PublicApi;
import dev.ember2d.render.LayerContent;
import java.util.Objects;
import java.util.function.Function;

/**
 * Particle effects (F-014, I-510): libGDX particle effects ({@code particle} assets, made with the libGDX particle
 * editor), pooled per effect and drawn in one render layer. Effects are authored in pixels and placed in world units.
 *
 * <pre>{@code
 * Particles particles = new Particles(ctx.assets(), ctx.renderer().pixelsPerUnit(), "fx");
 * spriteRenderSystem.addLayerContent(particles);
 * particles.spawn("fx.hit", x, y);      // one-shot: freed when complete
 * // every frame: particles.update(delta); in exit(): particles.clear()
 * }</pre>
 */
@PublicApi
public final class Particles implements LayerContent {

    private static final int INITIAL = 4;
    private static final int MAX_POOLED = 64;

    /** A running effect. The handle is invalid once the effect is freed ({@link #isValid()}). */
    public static final class Effect {
        private Particles owner;
        private ParticleEffectPool.PooledEffect effect;
        private int generation;

        /** Whether the effect is still running (not freed). */
        public boolean isValid() {
            return effect != null;
        }

        /** Moves the effect (world units). */
        public void position(float x, float y) {
            if (effect != null) {
                effect.setPosition(x, y);
            }
        }

        /** Lets the effect finish: emitters stop emitting, live particles fade out, then it is freed. */
        public void stop() {
            if (effect != null) {
                effect.allowCompletion();
            }
        }

        /** Whether all particles are gone. */
        public boolean isComplete() {
            return effect == null || effect.isComplete();
        }

        /** Frees the effect now. */
        public void free() {
            if (effect != null) {
                owner.free(this);
            }
        }

        /** Bumped each time the handle is reused, so callers can tell a reused handle from theirs. */
        public int generation() {
            return generation;
        }
    }

    private final Function<String, ParticleEffect> prototypes;
    private final float scale;
    private final String layer;
    private final ObjectMap<String, ParticleEffectPool> pools = new ObjectMap<>();
    private final Array<Effect> active = new Array<>(false, 32);
    private final Array<Effect> freeHandles = new Array<>(false, 32);

    /**
     * @param pixelsPerUnit the renderer's pixels per world unit; effects are scaled by its inverse
     * @param layer the render layer the effects are drawn in
     */
    public Particles(Assets assets, float pixelsPerUnit, String layer) {
        this(id -> assets.get(id, ParticleEffect.class), pixelsPerUnit, layer);
    }

    Particles(Function<String, ParticleEffect> prototypes, float pixelsPerUnit, String layer) {
        if (pixelsPerUnit <= 0f) {
            throw new IllegalArgumentException("pixelsPerUnit must be positive: " + pixelsPerUnit);
        }
        this.prototypes = Objects.requireNonNull(prototypes, "prototypes");
        this.scale = 1f / pixelsPerUnit;
        this.layer = Objects.requireNonNull(layer, "layer");
    }

    /** Starts an effect at a point (world units); it is freed when complete. */
    public Effect spawn(String effectId, float x, float y) {
        ParticleEffectPool.PooledEffect pooled = pool(effectId).obtain();
        pooled.reset();
        pooled.setPosition(x, y);
        pooled.start();
        Effect handle = freeHandles.size > 0 ? freeHandles.pop() : new Effect();
        handle.owner = this;
        handle.effect = pooled;
        handle.generation++;
        active.add(handle);
        return handle;
    }

    private ParticleEffectPool pool(String effectId) {
        ParticleEffectPool pool = pools.get(effectId);
        if (pool == null) {
            ParticleEffect prototype = new ParticleEffect(Objects.requireNonNull(prototypes.apply(effectId),
                    () -> "no particle effect \"" + effectId + "\""));
            prototype.scaleEffect(scale);   // pixels to world units, once per effect
            pool = new ParticleEffectPool(prototype, INITIAL, MAX_POOLED);
            pools.put(effectId, pool);
        }
        return pool;
    }

    /** Advances every effect and frees the completed ones. */
    public void update(float delta) {
        for (int i = active.size - 1; i >= 0; i--) {
            Effect e = active.get(i);
            e.effect.update(delta);
            if (e.effect.isComplete()) {
                free(e);
            }
        }
    }

    private void free(Effect e) {
        active.removeValue(e, true);
        e.effect.free();
        e.effect = null;
        freeHandles.add(e);
    }

    /** Running effects. */
    public int active() {
        return active.size;
    }

    /** Frees every running effect (call it when the scene exits). */
    public void clear() {
        for (int i = active.size - 1; i >= 0; i--) {
            free(active.get(i));
        }
    }

    /** The layer the effects are drawn in. */
    public String layer() {
        return layer;
    }

    @Override
    public void drawLayer(String layerName, Batch batch) {
        if (!layer.equals(layerName)) {
            return;
        }
        for (int i = 0; i < active.size; i++) {
            active.get(i).effect.draw(batch);
        }
    }
}
