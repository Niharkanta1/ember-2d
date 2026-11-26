package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.IntArray;
import dev.ember2d.animation.AnimationEvent;
import dev.ember2d.animation.AnimationLibrary;
import dev.ember2d.animation.AnimationSystem;
import dev.ember2d.animation.AsmLibrary;
import dev.ember2d.animation.AsmSystem;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.camera.CameraFollow;
import dev.ember2d.camera.CameraFollowSystem;
import dev.ember2d.collision.ActorSquished;
import dev.ember2d.collision.Collision;
import dev.ember2d.collision.CollisionDebugSystem;
import dev.ember2d.collision.CollisionSystem;
import dev.ember2d.collision.TriggerEntered;
import dev.ember2d.collision.TriggerExited;
import dev.ember2d.core.Log;
import dev.ember2d.data.MapSpawner;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.input.InputContextChanged;
import dev.ember2d.render.RenderLayer;
import dev.ember2d.render.SortMode;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;
import dev.ember2d.render.TextRenderer;
import dev.ember2d.tilemap.TileMap;
import dev.ember2d.tilemap.TileMapRenderer;
import java.util.Map;

/**
 * Demo scene, a small platformer test level.
 * <ul>
 *   <li>Map (I-110): a Tiled level with a parallax sky (background layer), animated water and torch, bushes in front
 *       of the player (foreground layer); the camera stays inside the map.</li>
 *   <li>Prefabs (I-112): the player, the patrollers, the camera (following the player through an object reference),
 *       a moving platform and a trigger zone are placed in the map; one more patroller is spawned from code.</li>
 *   <li>Collision (I-120): the player walks ({@code move}), jumps ({@code jump}: Space, Up, W), drops through
 *       the one-way planks with down + jump, rides the moving platform; patrollers turn at walls; the glow zone tints
 *       the player while inside; a squish respawns the player. F3 shows the colliders.</li>
 *   <li>Animation (I-130, I-135): the player's state machine (anim/hero.asm.json) picks idle, run (faster the
 *       faster you move), jump, fall and a short land squash from a sprite sheet; the player faces its walking
 *       direction; patrollers play run; the run clip's footstep frame events are logged in dev builds.</li>
 *   <li>Text (I-140): a sign near the start (prefab {@code prop/sign}, its text a Tiled property) and a label riding
 *       the moving platform, in a pixel font rendered from a TrueType file (FreeType).</li>
 *   <li>Input (I-100, I-105): {@code pause} (P, double tap) pauses game time; {@code menu} (Escape, Android back,
 *       long press) opens a menu context in which the same keys move a cursor and {@code ui_back} closes it.</li>
 *   <li>On-screen controls (I-122, touch screens or desktop {@code --touch-controls}): a floating joystick on the
 *       left half walks, button A jumps, button II pauses.</li>
 * </ul>
 */
final class DemoScene extends Scene {

    private static final Log LOG = Log.get("demo");
    private static final float CURSOR_SPEED = 10f;
    private static final float SPAWN_X = 4f;
    private static final float SPAWN_Y = 2f;

    private final Vector2 nav = new Vector2();
    private Input input;
    private Sprite cursorSprite;
    private Transform cursor;
    private int player;
    private Sprite playerSprite;
    private Transform playerTransform;
    private boolean glowing;

    @Override
    protected void enter(EngineContext context) {
        World world = world();
        input = context.input();
        context.renderer().setLayers(new RenderLayer("background", SortMode.NONE), new RenderLayer("world", SortMode.Z),
                new RenderLayer("foreground", SortMode.NONE));
        TileMap map = TileMap.load(context.assets(), "map.demo", context.renderer().pixelsPerUnit());
        Collision collision = new Collision(world, context.events());
        collision.addTileGrid(map.collision(), 0);
        LOG.info("Map " + map.source() + ": " + map.width() + "x" + map.height() + " tiles, "
                + map.collision().solidCount() + " solid, " + map.collision().oneWayCount() + " one-way");

        IntArray placed = new MapSpawner(context.prefabs()).spawn(world, map);
        int extra = context.prefabs().spawn(world, "enemy/patroller", 20f, SPAWN_Y,
                Map.of("Patrol.speed", 2f, "Patrol.minX", 19f, "Patrol.maxX", 31f));
        world.setName(extra, "patroller_code");
        LOG.info("Spawned " + placed.size + " entities from the map and 1 from code");
        player = world.findByName("player");
        playerTransform = world.get(player, Transform.class);
        playerSprite = world.get(player, Sprite.class);

        int menuCursor = world.create();
        world.setName(menuCursor, "menu cursor");
        cursor = world.add(menuCursor, Transform.class);
        cursorSprite = world.add(menuCursor, new Sprite());
        cursorSprite.regionId = "tex.player";
        cursorSprite.origin.set(8f, 8f);
        cursorSprite.tint.set(1f, 0.8f, 0.2f, 0f);   // hidden until the menu context is on top
        cursorSprite.z = 3f;

        world.get(world.findByName("camera"), CameraFollow.class).bounds = map.bounds(new Rectangle());

        world.addSystem(new MovingPlatformSystem(world, collision));   // solids first: riders see where they are
        world.addSystem(new PatrolSystem(world, collision));
        world.addSystem(new PlayerControlSystem(world, collision, input));
        world.addSystem(new CollisionSystem(world, collision));        // after all movement: hash and triggers
        world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));
        AnimationLibrary clips = new AnimationLibrary(context.assets());
        world.addSystem(new AsmSystem(world, new AsmLibrary(context.assets(), clips), context.events()));
        world.addSystem(new AnimationSystem(world, clips, context.events()));   // after the state machine
        SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
        world.addSystem(sprites);
        new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
        new TextRenderer(world, context.renderer(), context.assets(), context.time()).attachTo(sprites);
        world.addSystem(new CollisionDebugSystem(world, collision, context.debug(), context.renderer()));

        context.events().on(InputContextChanged.class, e -> LOG.info("Input context: " + e.top()));
        context.events().on(TriggerEntered.class, e -> glow(e.trigger(), e.actor(), true));
        context.events().on(TriggerExited.class, e -> glow(e.trigger(), e.actor(), false));
        context.events().on(AnimationEvent.class, e -> {
            if (e.entity() == player && e.name().equals("step")) {
                LOG.debug("Footstep");   // a frame event from the run clip (dev builds log DEBUG)
            }
        });
        context.events().on(ActorSquished.class, e -> {
            if (e.actor() == player) {
                LOG.info("Squished by " + world.name(e.solid()) + "; respawning");
                playerTransform.teleport(SPAWN_X, SPAWN_Y);
                world.get(player, PlayerControl.class).vy = 0f;
            }
        });
    }

    private void glow(int trigger, int actor, boolean inside) {
        if (actor != player) {
            return;
        }
        glowing = inside;
        LOG.info((inside ? "Entered " : "Left ") + world().name(trigger));
        tintPlayer();
    }

    private void tintPlayer() {
        playerSprite.tint.set(glowing ? 0.55f : 1f, glowing ? 0.8f : 1f, 1f, context().time().paused() ? 0.5f : 1f);
    }

    @Override
    protected void update(float delta) {
        if ("gameplay".equals(input.topContext())) {
            if (input.justPressed("pause")) {
                boolean paused = !context().time().paused();
                context().time().setPaused(paused);
                tintPlayer();
                LOG.info(paused ? "Paused" : "Running");
            }
            if (input.justPressed("menu")) {
                input.pushContext("ui");
                cursor.teleport(playerTransform.position.x, playerTransform.position.y + 2f);
                cursorSprite.tint.a = 1f;
            }
        } else {
            input.axis2("ui_nav", nav);
            cursor.teleport(cursor.position.x + nav.x * CURSOR_SPEED * context().time().unscaledDelta(),
                    cursor.position.y + nav.y * CURSOR_SPEED * context().time().unscaledDelta());
            if (input.justPressed("ui_back")) {
                input.popContext();
                cursorSprite.tint.a = 0f;
            }
        }
    }
}
