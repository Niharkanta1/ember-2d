package dev.ember2d.demo.shooter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import dev.ember2d.app.EngineContext;
import dev.ember2d.ecs.Transform;
import dev.ember2d.kit.common.Health;

/**
 * The eye (I-340): a bullet-hell boss with three phases chosen by its health. Each phase runs its pattern
 * ({@code patterns/boss1..3.json}) again and again; a phase change clears the boss's bullets into coins. A health bar
 * shows at the top; bullet count and frame rate are logged every 2 seconds.
 */
final class BossScene extends StageScene {

    private int boss;
    private int handle = -1;
    private ProgressBar bar;
    private float logWait = 2f;
    private int peakBullets;

    BossScene(Run run) {
        super(run);
    }

    @Override
    protected String mapId() {
        return "map.boss";
    }

    @Override
    protected String assetGroup() {
        return "boss";
    }

    @Override
    protected void built(EngineContext context) {
        boss = world.findByName("boss");
        audio.playMusic("music.boss", 0.5f);
        Table top = new Table(ui().skin());   // the boss bar, over the HUD at the top centre
        top.setFillParent(true);
        top.top().padTop(4f);
        top.add(new Label("THE EYE", ui().skin(), "title")).padRight(6f);
        bar = new ProgressBar(0f, 1f, 0.01f, false, ui().skin());
        bar.setValue(1f);
        top.add(bar).width(110f);
        ui().stage().addActor(top);
        LOG.info("Boss fight starts: health " + world.get(boss, Health.class).max);
    }

    @Override
    protected void tick(float delta) {
        Health h = world.get(boss, Health.class);
        if (h == null || h.dead) {
            return;
        }
        float ratio = h.current < 0 ? 1f : h.current / (float) h.max;
        bar.setValue(ratio);
        int phase = ratio > 0.66f ? 1 : ratio > 0.33f ? 2 : 3;
        Boss b = world.get(boss, Boss.class);
        if (phase != b.phase) {
            if (b.phase != 0) {
                bullets.clear(ENEMY_TEAM, true);   // a phase change turns the boss's bullets into coins
                audio.play("sfx.boss_phase");
                context().renderer().camera().addTrauma(0.6f);
            }
            b.phase = phase;
            bullets.stop(handle);
            handle = -1;
            LOG.info("Boss phase " + phase);
        }
        if (handle < 0 || !bullets.running(handle)) {
            Transform t = world.get(boss, Transform.class);
            handle = bullets.runAt("pattern.boss" + b.phase, t.position.x, t.position.y + 1.5f, player, ENEMY_TEAM);
        }
        peakBullets = Math.max(peakBullets, bulletWorld.size());
        logWait -= delta;
        if (logWait <= 0f) {
            logWait = 2f;
            LOG.info("Bullets " + bulletWorld.size() + " (peak " + peakBullets + "), fps "
                    + (Gdx.graphics == null ? 0 : Gdx.graphics.getFramesPerSecond()));
        }
    }

    @Override
    protected void died(int entity) {
        if (entity == boss) {
            bullets.stop(handle);
            bullets.clear(ENEMY_TEAM, true);
            audio.stopMusic(1f);
            run.score += 500;
            world.destroy(boss);
            won();
        }
    }
}
