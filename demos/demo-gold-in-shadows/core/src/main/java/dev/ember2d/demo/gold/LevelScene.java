package dev.ember2d.demo.gold;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonValue;
import dev.ember2d.animation.AnimationLibrary;
import dev.ember2d.animation.AnimationSystem;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.audio.Audio;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.core.Log;
import dev.ember2d.data.MapSpawner;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.kit.bullets.BulletPatterns;
import dev.ember2d.kit.bullets.BulletRenderer;
import dev.ember2d.kit.bullets.BulletSystem;
import dev.ember2d.kit.bullets.BulletTypes;
import dev.ember2d.kit.bullets.BulletWorld;
import dev.ember2d.kit.common.Damaged;
import dev.ember2d.kit.common.Died;
import dev.ember2d.kit.common.Health;
import dev.ember2d.kit.common.HealthSystem;
import dev.ember2d.kit.common.HitFlashSystem;
import dev.ember2d.kit.common.HitSystem;
import dev.ember2d.kit.common.LifetimeSystem;
import dev.ember2d.kit.topdown.TopDownSystem;
import dev.ember2d.kit.topdown.ai.AiSystem;
import dev.ember2d.kit.topdown.ai.PathPlanner;
import dev.ember2d.kit.topdown.shooter.Aim;
import dev.ember2d.kit.topdown.shooter.AimSystem;
import dev.ember2d.kit.topdown.shooter.ShotEvents;
import dev.ember2d.kit.topdown.shooter.Weapon;
import dev.ember2d.kit.topdown.shooter.WeaponLibrary;
import dev.ember2d.kit.topdown.shooter.WeaponSystem;
import dev.ember2d.kit.topdown.vision.FogLayer;
import dev.ember2d.kit.topdown.vision.VisionField;
import dev.ember2d.kit.topdown.vision.VisionSettings;
import dev.ember2d.kit.topdown.vision.VisionSystem;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;

/**
 * One level of the campaign (F-029): the converted Tiled map, its walls, everything its objects spawn, and the systems
 * that run the original game's rules. Render layers, back to front: {@code floor} (the map's base, floor, wall and prop
 * tiles), {@code world} (actors, sorted by y), {@code bullets}, {@code hand} (gun, hands, effects), {@code overhead}
 * (the map's overhead tiles) and {@code fog}.
 */
final class LevelScene extends Scene {

    private static final Log LOG = Log.get("gold");
    /** The original's hit shake: 2.5 px for 0.25 s. */
    static final float HIT_SHAKE = 2.5f / 16f;
    static final float HIT_SHAKE_SECONDS = 0.25f;

    private final Campaign campaign;
    private final int level;
    private EngineContext context;
    private PickupSystem pickups;
    private FloatingTexts texts;
    private boolean leaving;
    private Input input;
    private World world;
    private Hud hud;
    private FogLayer fog;
    private CreatureOverlay overlay;
    private int player;

    /** Plays the campaign's current level (1, 2 or 3). */
    LevelScene(Campaign campaign) {
        this.campaign = campaign;
        this.level = campaign.level;
    }

    @Override
    protected String assetGroup() {
        return "level" + level;
    }

    @Override
    protected void enter(EngineContext context) {
        this.context = context;
        world = world();
        input = context.input();
        Audio audio = context.audio();
        float ppu = context.renderer().pixelsPerUnit();
        context.renderer().setLayers(new RenderLayer("floor", SortMode.NONE), new RenderLayer("world", SortMode.Y_SORT),
                new RenderLayer("bullets", SortMode.NONE), new RenderLayer(Effects.LAYER, SortMode.NONE),
                new RenderLayer("overhead", SortMode.NONE), new RenderLayer("fog", SortMode.NONE));
        TileMap map = TileMap.load(context.assets(), "map.level" + level, ppu);
        Rectangle bounds = map.bounds(new Rectangle());
        Collision collision = new Collision(world, context.events());
        int walls = Walls.build(world, map);

        BulletTypes types = BulletTypes.parse(context.assets().get("bullets", JsonValue.class),
                context.assets().path("bullets"));
        BulletWorld bulletWorld = new BulletWorld(1024, types);
        bulletWorld.setCullArea(new Rectangle(bounds.x - 2f, bounds.y - 2f, bounds.width + 4f, bounds.height + 4f));
        HealthSystem health = new HealthSystem(world, context.events());
        BulletSystem bullets = new BulletSystem(world, bulletWorld, new BulletPatterns(context.assets(), types),
                context.events(), health, context.time());
        bullets.setWallMask(Walls.MOVE_MASK, collision);
        bullets.setWallStopListener((type, x, y) -> Effects.impact(world, x, y));

        new MapSpawner(context.prefabs()).spawn(world, map);
        player = world.findByName("player");
        world.tag(player, "player");
        boolean touch = Gdx.app != null && Gdx.app.getType() == Application.ApplicationType.Android;
        world.get(player, Aim.class).mouse = !touch;   // touch screens and pads aim with a stick
        Difficulty difficulty = GoldGame.difficulty();
        Health h = world.get(player, Health.class);
        h.max = difficulty.maxHealth;
        h.current = difficulty.maxHealth;
        h.damageMultiplier = difficulty.damageMultiplier;
        HeroSystem.equip(world, player);

        VisionField field = new VisionField(collision, Walls.SIGHT_MASK, new VisionSettings());
        PathPlanner planner = new PathPlanner(Walls.navigation(map));
        TopDownSystem movement = new TopDownSystem(world, collision, input, context.events());
        CreatureCatalog creatures = new CreatureCatalog(context.assets().get("creatures", JsonValue.class));
        world.addSystem(new AimSystem(world, input));
        world.addSystem(new LifetimeSystem(world));
        world.addSystem(new CreatureSystem(world, creatures, collision, health, bulletWorld, movement,
                context.prefabs(), planner, context.events(), audio));                     // FIXED, in this order
        world.addSystem(new AiSystem(world, planner));
        world.addSystem(movement);
        texts = new FloatingTexts(context.assets(), Effects.LAYER);
        pickups = new PickupSystem(world, campaign, input, context.prefabs(), audio, texts);
        world.addSystem(pickups);
        world.addSystem(new SpikeSystem(world, health, context.assets(), audio));
        world.addSystem(new ExitSystem(world, context.events(), context.assets(), audio, texts));
        world.addSystem(new WeaponSystem(world, bullets, new WeaponLibrary(context.assets(), types), context.events(),
                input));
        world.addSystem(health);
        world.addSystem(new HitSystem(world, health));
        world.addSystem(new HitFlashSystem(world, context.events()));
        world.addSystem(bullets);
        world.addSystem(new CollisionSystem(world, collision));
        world.addSystem(new AnimationSystem(world, new AnimationLibrary(context.assets()), context.events()));
        world.addSystem(new HeroSystem(world, context.events()));                           // UPDATE
        world.addSystem(new GoldCameraSystem(world, context.renderer().camera()));         // LATE
        world.addSystem(new VisionSystem(world, field, context.events()));
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        BulletRenderer bulletRenderer = new BulletRenderer(bulletWorld, context.assets(), "bullets", ppu);
        bulletRenderer.setVisibility(field::isVisible);   // the creatures' shots are hidden in the dark
        sprites.addLayerContent(bulletRenderer);
        overlay = new CreatureOverlay(world, context.assets(), Effects.LAYER);
        sprites.addLayerContent(overlay);
        sprites.addLayerContent(texts);
        fog = new FogLayer(field, context.renderer().camera(), "fog");
        sprites.addLayerContent(fog);

        hud = new Hud(ui(), context.assets());
        context.events().on(ShotEvents.Fired.class, e -> audio.playVaried("sfx.shoot", 0.05f));
        context.events().on(ShotEvents.ReloadStarted.class, e -> audio.play("sfx.reload"));
        context.events().on(Damaged.class, e -> {
            if (e.target() == player) {
                GoldCameraSystem.shake(world, HIT_SHAKE, HIT_SHAKE_SECONDS);
                hud.flash();
                audio.play("sfx.hurt");
            }
        });
        context.events().on(Died.class, e -> {
            if (e.entity() == player) {
                audio.play("sfx.death");
            } else if (world.has(e.entity(), Creature.class)) {
                campaign.kills++;
            }
        });
        context.events().on(HeroDied.class, e -> retry());
        context.events().on(ExitSystem.LevelCleared.class, e -> cleared());
        context.audio().playMusic("music.dungeon", 0.5f);
        LOG.info("Level " + level + " (" + map.properties().get("title", "", String.class) + "): " + walls
                + " wall boxes, " + world.family(Creature.class).size() + " creatures, " + world.entityCount()
                + " entities");
    }

    /** Until the death screen exists (I-650): the level starts again with the gold and kills it started with. */
    private void retry() {
        campaign.retryLevel();
        leave(new LevelScene(campaign));
    }

    /**
     * The player walked out through the exit: the next level, with the gold carried over and the progress saved for
     * Continue. After the last level the campaign ends (until the end screen exists, I-650: a new campaign starts).
     */
    private void cleared() {
        LOG.info("Level " + level + " cleared: " + campaign.levelGold() + " gold, " + campaign.levelKills()
                + " kills, " + Math.round(campaign.levelTime) + " s; score " + campaign.gold);
        boolean next = campaign.advance(context.saves().global());
        context.saves().flush();
        if (next) {
            campaign.startLevel();
            leave(new LevelScene(campaign));
        } else {
            leave(new LevelScene(Campaign.fresh()));
        }
    }

    private void leave(Scene next) {
        if (!leaving) {
            leaving = true;
            context.scenes().replace(next, dev.ember2d.app.Transition.fade(0.5f));
        }
    }

    @Override
    protected void update(float delta) {
        if (input.justPressed("pause")) {
            Gdx.app.exit();   // until the pause menu exists (I-650)
        }
        campaign.levelTime += delta;
        texts.update(delta);
        Health h = world.get(player, Health.class);
        Weapon weapon = world.get(player, Weapon.class);
        Hero hero = world.get(player, Hero.class);
        int magazine = 30;
        hud.hint(pickups.chestInReach() ? "E: OPEN CHEST" : "");
        hud.update(delta, h == null ? 0 : h.current, h == null ? 0 : h.max, campaign.gold, hero == null ? 0 : hero.keys,
                creaturesLeft(), weapon == null || weapon.ammo < 0 ? magazine : weapon.ammo,
                magazine, weapon != null && weapon.reloading);
    }

    /** Creatures still alive (dying ones fade out first). */
    private int creaturesLeft() {
        int alive = 0;
        for (int e : world.family(Creature.class).entities().toArray()) {
            if (world.get(e, Creature.class).state != Creature.State.DEAD) {
                alive++;
            }
        }
        return alive;
    }

    @Override
    protected void exit() {
        overlay.dispose();
        fog.dispose();
    }
}
