package dev.ember2d.demo.shooter;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.app.Transition;
import dev.ember2d.audio.Audio;
import dev.ember2d.camera.CameraFollow;
import dev.ember2d.camera.CameraFollowSystem;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionDebugSystem;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.core.Log;
import dev.ember2d.data.MapSpawner;
import dev.ember2d.ecs.Family;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.kit.bullets.BulletCleared;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletRenderer;
import dev.ember2d.kit.bullets.BulletSystem;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.BulletWorld;
import dev.ember2d.kit.bullets.Grazed;
import dev.ember2d.kit.common.Damaged;
import dev.ember2d.kit.common.Despawn;
import dev.ember2d.kit.common.Died;
import dev.ember2d.kit.common.EntityPool;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.kit.common.HitFlashSystem;
import dev.ember2d.kit.common.Hitbox;
import dev.ember2d.kit.common.HitSystem;
import dev.ember2d.kit.common.LifetimeSystem;
import dev.ember2d.kit.common.SpawnerSystem;
import dev.ember2d.kit.common.TriggerZoneSystem;
import dev.ember2d.kit.common.ZoneEntered;
import dev.ember2d.kit.topdown.TopDownBody;
import dev.ember2d.kit.topdown.TopDownSystem;
import dev.ember2d.kit.topdown.ai.AiSystem;
import dev.ember2d.kit.topdown.ai.PathPlanner;
import dev.ember2d.kit.topdown.ai.Steering;
import dev.ember2d.kit.topdown.shooter.Aim;
import dev.ember2d.kit.topdown.shooter.AimSystem;
import dev.ember2d.kit.topdown.shooter.ShotEvents;
import dev.ember2d.kit.topdown.shooter.Weapon;
import dev.ember2d.kit.topdown.shooter.WeaponLibrary;
import dev.ember2d.kit.topdown.shooter.WeaponSystem;
import dev.ember2d.particles.Particles;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.save.SaveData;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;
import dev.ember2d.ui.ConfirmDialog;
import dev.ember2d.ui.MenuList;
import dev.ember2d.ui.Ui;

/**
 * A stage of the shooter demo: a Tiled map with the player, built from the kits.
 * <ul>
 *   <li>Movement and aiming: kit-topdown ({@code TopDownBody}, {@code Aim}); weapons fire through kit-bullets.</li>
 *   <li>Enemies follow paths around walls (gdx-ai through kit-topdown), hurt on contact (kit-common hit boxes) and die
 *       to bullets (kit-bullets targets and kit-common health); they leave pooled splatter and pickups.</li>
 *   <li>A HUD (health, score, ammo), pause, and the end panels.</li>
 * </ul>
 * Subclasses add the room's waves or the boss.
 */
abstract class StageScene extends Scene {

    protected static final Log LOG = Log.get("shooter");
    protected static final int PLAYER_TEAM = 1;
    protected static final int ENEMY_TEAM = 2;

    protected final Run run;
    protected World world;
    protected Input input;
    protected Audio audio;
    protected int player;
    protected BulletWorld bulletWorld;
    protected BulletSystem bullets;
    protected HealthSystem health;
    protected Rectangle bounds;
    private EntityPool splats;
    private Label hp;
    private Label score;
    private Label ammo;
    private Table pausePanel;
    private boolean paused;
    private boolean over;
    private int clearedCount;
    private Family contact;
    private Family hunters;
    private float contactWait;
    private Particles particles;
    private float dustWait;

    StageScene(Run run) {
        this.run = run;
    }

    /** The map's asset ID. */
    protected abstract String mapId();

    /** The stage was built: add what is special to it. */
    protected void built(EngineContext context) {
    }

    /** Every frame while playing. */
    protected void tick(float delta) {
    }

    @Override
    protected void enter(EngineContext context) {
        world = world();
        input = context.input();
        audio = context.audio();
        context.renderer().setLayers(new RenderLayer("floor", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT),
                new RenderLayer("fx", SortMode.NONE), new RenderLayer("hand", SortMode.NONE),
                new RenderLayer("bullets", SortMode.NONE));
        float ppu = context.renderer().pixelsPerUnit();
        TileMap map = TileMap.load(context.assets(), mapId(), ppu);
        bounds = map.bounds(new Rectangle());
        Collision collision = new Collision(world, context.events());
        collision.addTileGrid(map.collision(), 0);

        BulletTypes types = BulletTypes.parse(context.assets().get("bullets", JsonValue.class),
                context.assets().path("bullets"));
        bulletWorld = new BulletWorld(4096, types);
        bulletWorld.setCullArea(new Rectangle(bounds.x - 2f, bounds.y - 2f, bounds.width + 4f, bounds.height + 4f));
        health = new HealthSystem(world, context.events());
        bullets = new BulletSystem(world, bulletWorld, new BulletPatterns(context.assets(), types), context.events(),
                health, context.time());
        bullets.setWallMask(1, collision);

        new MapSpawner(context.prefabs()).spawn(world, map);
        player = world.findByName("player");
        world.tag(player, "player");
        boolean touch = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        world.get(player, Aim.class).mouse = !touch;   // touch screens aim with the aim stick
        if (run.health >= 0) {
            world.get(player, Health.class).current = run.health;
        }
        world.get(world.findByName("camera"), CameraFollow.class).bounds = bounds;
        int gun = world.create();
        world.add(gun, new Transform());
        Sprite gunSprite = world.add(gun, new Sprite());
        gunSprite.regionId = "atlas.shooter/gun.smg";
        gunSprite.origin.set(5f, 8f);
        gunSprite.layer = "hand";
        world.add(gun, new GunHolderSystem.GunHolder());

        PathPlanner paths = new PathPlanner(map.collision());
        world.addSystem(new AimSystem(world, input));
        world.addSystem(new SpawnerSystem(world, context.prefabs(), context.events()));
        world.addSystem(new GunnerSystem(world, bullets, player));
        world.addSystem(new LifetimeSystem(world));
        world.addSystem(new AiSystem(world, paths));                     // FIXED, in this order:
        world.addSystem(new TopDownSystem(world, collision, input, context.events()));
        world.addSystem(new WeaponSystem(world, bullets, new WeaponLibrary(context.assets(), types), context.events(),
                input));
        world.addSystem(health);
        world.addSystem(new HitSystem(world, health));
        world.addSystem(new HitFlashSystem(world, context.events()));   // enemies flash white when hit (I-520)
        world.addSystem(bullets);
        world.addSystem(new TriggerZoneSystem(world, context.events()));
        world.addSystem(new CollisionSystem(world, collision));
        world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));
        world.addSystem(new GunHolderSystem(world, player));
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        sprites.addLayerContent(new BulletRenderer(bulletWorld, context.assets(), "bullets", ppu));
        particles = new Particles(context.assets(), ppu, "fx");   // hit sparks and dust (I-510)
        sprites.addLayerContent(particles);
        world.addSystem(new CollisionDebugSystem(world, collision, context.debug(), context.renderer()));
        splats = EntityPool.ofPrefab(world, context.prefabs(), "fx/splat", 8);
        contact = world.family(Hitbox.class);
        hunters = world.family(Steering.class);

        wireEvents(context);
        buildHud(context);
        built(context);
        LOG.info("Stage " + map.source() + " ready: " + world.entityCount() + " entities");
    }

    private void wireEvents(EngineContext context) {
        context.events().on(Died.class, e -> {
            if (e.entity() == player) {
                lost();
                return;
            }
            Transform t = world.get(e.entity(), Transform.class);
            Enemy enemy = world.get(e.entity(), Enemy.class);
            if (t != null && enemy != null) {
                run.score += enemy.score;
                audio.playVaried("sfx.kill", 0.1f);
                splats.obtain(t.position.x, t.position.y);
                float roll = MathUtils.random();
                if (roll < 0.08f) {
                    context.prefabs().spawn(world, "pickup/heart", t.position.x, t.position.y);
                } else if (roll < 0.4f) {
                    context.prefabs().spawn(world, "pickup/coin", t.position.x, t.position.y);
                }
                Despawn.despawn(world, e.entity());
            }
            died(e.entity());
        });
        context.events().on(Damaged.class, e -> {
            if (e.target() == player) {
                audio.play("sfx.hurt");
                input.vibrate(60);
                context.renderer().camera().addTrauma(0.4f);
            } else {
                audio.playVaried("sfx.hit", 0.15f);
                Transform t = world.get(e.target(), Transform.class);
                if (t != null) {
                    particles.spawn("fx.hit", t.position.x, t.position.y + 0.45f);
                }
            }
        });
        context.events().on(Grazed.class, e -> {
            run.score += 1;
            audio.play("sfx.graze");
        });
        context.events().on(BulletCleared.class, e -> {
            if (clearedCount++ % 6 == 0) {   // a coin for every sixth cleared bullet
                context.prefabs().spawn(world, "pickup/coin", e.x(), e.y());
            }
        });
        context.events().on(ZoneEntered.class, e -> {
            Pickup pickup = world.get(e.zone(), Pickup.class);
            if (pickup != null && e.entity() == player) {
                run.score += pickup.score;
                health.heal(player, pickup.heal);
                audio.play("sfx.pickup");
                Despawn.despawn(world, e.zone());
            }
            Exit exit = world.get(e.zone(), Exit.class);
            if (exit != null && exit.open && e.entity() == player && !over) {
                over = true;
                run.health = world.get(player, Health.class).current;
                context.scenes().replace(new BossScene(run), Transition.fade(0.8f));
            }
        });
        context.events().on(ShotEvents.Fired.class, e -> {
            if (e.entity == player) {
                audio.playVaried("sfx.shoot", 0.1f);
            }
        });
        context.events().on(ShotEvents.ReloadStarted.class, e -> audio.play("sfx.reload"));
    }

    /** An entity died (after the stage's own handling). */
    protected void died(int entity) {
    }

    private void buildHud(EngineContext context) {
        Ui ui = ui();
        ShooterUi.useSkin(ui, context.assets());
        Table hud = new Table(ui.skin());
        hp = new Label("", ui.skin());
        score = new Label("", ui.skin());
        ammo = new Label("", ui.skin());
        hud.add(hp).left().row();
        hud.add(score).left().padTop(2f).row();
        hud.add(ammo).left().padTop(2f);
        ui.root().add(hud).expand().top().left().pad(4f);
    }

    @Override
    protected void update(float delta) {
        if (!over && "gameplay".equals(input.topContext()) && (input.justPressed("pause") || input.justPressed("menu"))) {
            openPause();
        }
        if (!over && !paused) {
            run.seconds += delta;
            contactWait -= delta;
            if (contactWait <= 0f) {
                // Contact damage: a hit box hits a target once per activation, so restart them every second; the
                // player's invulnerability (1 s) keeps it fair.
                contactWait = 1f;
                for (int i = 0; i < contact.entities().size; i++) {
                    world.get(contact.entities().get(i), Hitbox.class).restart();
                }
            }
            for (int i = 0; i < hunters.entities().size; i++) {
                Steering s = world.get(hunters.entities().get(i), Steering.class);
                if (s.target == null) {
                    s.target = world.ref(player);   // spawned enemies hunt the player (a prefab cannot name it)
                }
            }
            tick(delta);
            particles.update(delta);
            TopDownBody body = world.get(player, TopDownBody.class);
            dustWait -= delta;
            if (body.moving && dustWait <= 0f) {   // a puff under running feet
                dustWait = 0.14f;
                Transform t = world.get(player, Transform.class);
                particles.spawn("fx.dust", t.position.x, t.position.y + 0.05f);
            }
        }
        Health h = world.get(player, Health.class);
        hp.setText("HP " + Math.max(0, h.current));
        score.setText("Score " + run.score);
        Weapon w = world.get(player, Weapon.class);
        ammo.setText(w.reloading ? "Reloading..." : "Ammo " + Math.max(0, w.ammo));
    }

    // ---- pause and the end ----

    private void openPause() {
        paused = true;
        context().time().setPaused(true);
        input.pushContext("ui");
        pausePanel = ShooterUi.overlay(ui());
        Ui ui = ui();
        MenuList menu = new MenuList(ui);
        menu.add("Resume", this::closePause);
        menu.add("Main menu", () -> ConfirmDialog.show(ui, "Quit to the main menu?", "Yes", "No",
                () -> context().scenes().replace(new MenuScene(), Transition.fade(0.5f))));
        menu.onBack(this::closePause);
        ShooterUi.show(pausePanel, "Paused", menu);
    }

    private void closePause() {
        if (!paused) {
            return;
        }
        paused = false;
        pausePanel.getParent().remove();
        input.popContext();
        context().time().setPaused(false);
    }

    /** The player won: shows the score and the best score. */
    protected void won() {
        if (over) {
            return;
        }
        over = true;
        endPanel("Victory!", true);
    }

    private void lost() {
        if (over) {
            return;
        }
        over = true;
        world.get(player, TopDownBody.class).locked = true;
        world.get(player, Weapon.class).fireAction = "";
        world.get(player, Weapon.class).trigger = false;
        endPanel("You died", false);
    }

    private void endPanel(String title, boolean victory) {
        input.pushContext("ui");
        Ui ui = ui();
        Table panel = ShooterUi.overlay(ui);
        MenuList menu = new MenuList(ui);
        menu.add(victory ? "Play again" : "Retry", () -> context().scenes().replace(new RoomScene(new Run()),
                Transition.fade(0.5f)));
        menu.add("Main menu", () -> context().scenes().replace(new MenuScene(), Transition.fade(0.5f)));
        ShooterUi.show(panel, title, menu);
        SaveData global = context().saves().global();
        int best = global.getInt(Run.BEST_KEY, 0);
        Table stats = new Table(ui.skin());
        stats.add(new Label("Score  " + run.score, ui.skin())).left().row();
        if (victory && run.score > best) {
            global.set(Run.BEST_KEY, run.score);
            context().saves().flush();
            stats.add(new Label("New best!", ui.skin(), "title")).left();
        } else {
            stats.add(new Label("Best   " + best, ui.skin())).left();
        }
        panel.row();
        panel.add(stats).padTop(6f);
        LOG.info(title + ": score " + run.score + " after " + (int) run.seconds + " s");
    }

    @Override
    protected void exit() {
        particles.clear();
        while (!"gameplay".equals(input.topContext())) {
            input.popContext();
        }
        if (context().time().paused()) {
            context().time().setPaused(false);
        }
    }
}
