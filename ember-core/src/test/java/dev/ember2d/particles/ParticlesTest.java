package dev.ember2d.particles;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import dev.ember2d.HeadlessGdx;
import dev.ember2d.core.Phase;
import dev.ember2d.core.internal.TimeState;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.RenderTestKit;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Particles (F-014, I-510): pooling, completion, drawing in one layer, and effects attached to entities. */
class ParticlesTest {

    static Texture texture;

    @BeforeAll
    static void gdx() {
        HeadlessGdx.install();
        texture = new Texture(new Pixmap(8, 8, Pixmap.Format.RGBA8888));
    }

    @AfterAll
    static void done() {
        HeadlessGdx.clear();
    }

    /** A small one-shot effect: 0.1 s of emission, particles live 0.2 s, 8 px wide. */
    static ParticleEffect burst() {
        ParticleEmitter e = new ParticleEmitter();
        e.setMinParticleCount(0);
        e.setMaxParticleCount(20);
        e.getDuration().setLow(100f);
        e.getEmission().setHigh(200f);
        e.getLife().setHigh(200f);
        e.getXScale().setHigh(8f);
        e.getVelocity().setActive(true);
        e.getVelocity().setHigh(50f);
        e.getAngle().setActive(true);
        e.getAngle().setHigh(0f, 360f);
        e.setContinuous(false);
        e.setSprites(Array.with(new Sprite(new TextureRegion(texture))));
        ParticleEffect effect = new ParticleEffect();
        effect.getEmitters().add(e);
        return effect;
    }

    final Map<String, ParticleEffect> effects = new HashMap<>(Map.of("fx.burst", burst()));
    final Particles particles = new Particles(effects::get, 16f, "fx");

    void run(float seconds) {
        for (float t = 0f; t < seconds; t += 1f / 60f) {
            particles.update(1f / 60f);
        }
    }

    @Test
    void anEffectRunsUntilCompleteAndIsFreedBackToItsPool() {
        Particles.Effect hit = particles.spawn("fx.burst", 2f, 3f);
        assertThat(particles.active()).isEqualTo(1);
        run(0.1f);
        assertThat(hit.isValid()).isTrue();
        assertThat(hit.isComplete()).isFalse();
        run(0.5f);
        assertThat(hit.isValid()).as("freed when complete").isFalse();
        assertThat(particles.active()).isZero();
        Particles.Effect again = particles.spawn("fx.burst", 0f, 0f);
        assertThat(again).as("the handle is reused").isSameAs(hit);
        assertThat(again.generation()).isGreaterThan(1);
    }

    @Test
    void effectsAreScaledToWorldUnits() {
        particles.spawn("fx.burst", 0f, 0f);
        ParticleEffect prototype = effects.get("fx.burst");
        assertThat(prototype.getEmitters().first().getXScale().getHighMax()).as("the asset is untouched").isEqualTo(8f);
    }

    @Test
    void manyHitsAfterWarmUpAllocateNothing() {
        for (int i = 0; i < 10; i++) {   // warm-up: the pool and the particles exist
            particles.spawn("fx.burst", i, 0f);
            particles.spawn("fx.burst", 0f, i);
            run(0.4f);
        }
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 200; i++) {
            particles.spawn("fx.burst", i, 0f);
            particles.spawn("fx.burst", 0f, i);
            run(0.4f);
        }
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        assertThat(allocated).as("bytes allocated over 400 effects").isLessThan(16 * 1024);
    }

    @Test
    void clearFreesEverythingAndDrawingUsesOnlyItsLayer() {
        particles.spawn("fx.burst", 0f, 0f);
        Particles.Effect stopped = particles.spawn("fx.burst", 1f, 0f);
        stopped.stop();
        run(0.05f);
        RenderTestKit kit = new RenderTestKit();
        particles.drawLayer("world", kit.batch);
        assertThat(kit.calls).as("another layer").isEmpty();
        particles.drawLayer("fx", kit.batch);
        assertThat(kit.calls).as("particles drawn").isNotEmpty();
        particles.clear();
        assertThat(particles.active()).isZero();
        assertThat(stopped.isValid()).isFalse();
    }

    @Test
    void anEntityEffectFollowsTheEntityAndGoesWithIt() {
        World world = new World(false);
        TimeState time = new TimeState(1f / 60f);
        world.addSystem(new ParticleSystem(world, particles));
        int torch = world.create();
        Transform t = world.add(torch, new Transform());
        t.position.set(5f, 5f);
        ParticleSource source = world.add(torch, new ParticleSource());
        source.effect = "fx.burst";
        source.offsetY = 1f;
        world.runPhase(Phase.LATE, time);
        assertThat(particles.active()).isEqualTo(1);
        world.destroy(torch);
        world.runPhase(Phase.LATE, time);
        assertThat(particles.active()).as("freed with the entity").isZero();
    }

    @Test
    void aOneShotEntityIsDestroyedWhenItsEffectCompletes() {
        World world = new World(false);
        TimeState time = new TimeState(1f / 60f);
        world.addSystem(new ParticleSystem(world, particles));
        int puff = world.create();
        world.add(puff, new Transform());
        ParticleSource source = world.add(puff, new ParticleSource());
        source.effect = "fx.burst";
        source.despawnWhenDone = true;
        int stays = world.create();
        world.add(stays, new Transform());
        ParticleSource keep = world.add(stays, new ParticleSource());
        keep.effect = "fx.burst";
        for (int i = 0; i < 60; i++) {
            world.runPhase(Phase.LATE, time);
            particles.update(1f / 60f);
        }
        world.runPhase(Phase.LATE, time);
        assertThat(world.isAlive(puff)).isFalse();
        assertThat(world.isAlive(stays)).isTrue();
        assertThat(keep.finished).as("not started again").isTrue();
        assertThat(particles.active()).isZero();
    }
}
