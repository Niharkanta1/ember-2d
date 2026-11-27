package dev.ember2d.demo.shooter;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.core.Log;
import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletRenderer;
import dev.ember2d.kit.bullets.BulletSystem;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.BulletWorld;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;
import dev.ember2d.ui.MenuList;

/**
 * Bullet stress test (F-024 acceptance): the boss arena with an emitter that keeps about 3,000 bullets alive. The HUD
 * shows the bullet count and frame rate; every 2 seconds they are logged with the average time of one bullet step.
 */
final class StressScene extends Scene {

    private static final Log LOG = Log.get("shooter");

    private BulletWorld bulletWorld;
    private Input input;
    private Label stats;
    private float logWait = 2f;
    private long stepStart;
    private long stepNanos;
    private int steps;

    @Override
    protected String assetGroup() {
        return "boss";
    }

    @Override
    protected void enter(EngineContext context) {
        World world = world();
        input = context.input();
        input.pushContext("ui");
        context.renderer().setLayers(new RenderLayer("floor", SortMode.NONE),
                new RenderLayer("bullets", SortMode.NONE));
        float ppu = context.renderer().pixelsPerUnit();
        TileMap map = TileMap.load(context.assets(), "map.boss", ppu);
        Rectangle bounds = map.bounds(new Rectangle());
        float cx = bounds.x + bounds.width / 2f;
        float cy = bounds.y + bounds.height / 2f;
        context.renderer().camera().position.set(cx, cy);
        BulletTypes types = BulletTypes.parse(context.assets().get("bullets", JsonValue.class),
                context.assets().path("bullets"));
        bulletWorld = new BulletWorld(4096, types);
        BulletSystem bullets = new BulletSystem(world, bulletWorld, new BulletPatterns(context.assets(), types),
                context.events(), null, context.time());
        world.addSystem(new StartClock(world));    // FIXED: start the clock,
        world.addSystem(bullets);                  // step the bullets,
        world.addSystem(new StopClock(world));     // stop the clock (a world takes one system per class)
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(),
                context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        sprites.addLayerContent(new BulletRenderer(bulletWorld, context.assets(), "bullets", ppu));
        bullets.runAt("pattern.stress", cx, cy, World.NONE, 2);

        ShooterUi.useSkin(ui(), context.assets());
        Table top = new Table(ui().skin());
        stats = new Label("", ui().skin(), "title");
        top.add(stats).left().row();
        MenuList menu = new MenuList(ui());
        menu.add("Back", () -> context.scenes().replace(new MenuScene(), Transition.fade(0.4f)));
        menu.onBack(() -> context.scenes().replace(new MenuScene(), Transition.fade(0.4f)));
        top.add(menu).left().padTop(4f);
        ui().root().add(top).expand().top().left().pad(4f);
        LOG.info("Bullet stress test started");
    }

    @Override
    protected void update(float delta) {
        int fps = Gdx.graphics == null ? 0 : Gdx.graphics.getFramesPerSecond();
        stats.setText("Bullets " + bulletWorld.size() + "  FPS " + fps);
        logWait -= delta;
        if (logWait <= 0f) {
            logWait = 2f;
            long average = steps == 0 ? 0 : stepNanos / steps / 1000;
            LOG.info("Stress: " + bulletWorld.size() + " bullets, " + fps + " fps, bullet step " + average + " us");
            stepNanos = 0;
            steps = 0;
        }
    }

    @Override
    protected void exit() {
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
    }

    /** Starts timing a bullet step (FIXED; its family is empty, it only uses begin()). */
    private final class StartClock extends EntitySystem {
        StartClock(World world) {
            super(Phase.FIXED, world.family(Marker.class));
        }

        @Override
        protected void begin() {
            stepStart = System.nanoTime();
        }

        @Override
        protected void process(int entity, float delta) {
        }
    }

    /** Stops timing a bullet step. */
    private final class StopClock extends EntitySystem {
        StopClock(World world) {
            super(Phase.FIXED, world.family(Marker.class));
        }

        @Override
        protected void begin() {
            stepNanos += System.nanoTime() - stepStart;
            steps++;
        }

        @Override
        protected void process(int entity, float delta) {
        }
    }

    /** No entity has it. */
    static final class Marker {
    }
}
