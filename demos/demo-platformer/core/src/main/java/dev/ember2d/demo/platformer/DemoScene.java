package dev.ember2d.demo.platformer;

import com.badlogic.gdx.math.Vector2;
import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.camera.CameraFollow;
import dev.ember2d.camera.CameraFollowSystem;
import dev.ember2d.core.Log;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.input.Input;
import dev.ember2d.input.InputContextChanged;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;

/**
 * Demo scene. Phase 1: a sprite patrols left and right while fixed markers show the camera scrolling; motion must look
 * smooth at 60 Hz and 144 Hz. Phase 2 input check: the player sprite moves with the {@code move} axis (arrows/WASD)
 * or walks to a tapped point ({@code tap}: touch or mouse click), {@code pause} (P, double tap) pauses game time,
 * {@code menu} (Escape, Android back, long press) opens a menu context in which the same keys move a cursor and
 * {@code ui_back} (Escape, back, tap) closes it again.
 */
final class DemoScene extends Scene {

    private static final Log LOG = Log.get("demo");
    private static final float CURSOR_SPEED = 10f;

    private final Vector2 nav = new Vector2();
    private Input input;
    private Sprite cursorSprite;
    private Transform cursor;
    private Sprite playerSprite;
    private Transform playerTransform;
    private PlayerControl playerControl;

    @Override
    protected void enter(EngineContext context) {
        World world = world();
        input = context.input();

        for (int i = -16; i <= 16; i += 2) {
            int marker = world.create();
            world.add(marker, Transform.class).teleport(i, -2f);
            Sprite sprite = world.add(marker, new Sprite());
            sprite.regionId = "tex.player";
            sprite.tint.set(0.35f, 0.3f, 0.45f, 1f);
            sprite.z = 0f;
        }

        int patroller = world.create();
        world.setName(patroller, "patroller");
        world.add(patroller, Transform.class).teleport(0f, 0f);
        Sprite patrolSprite = world.add(patroller, new Sprite());
        patrolSprite.regionId = "tex.player";
        patrolSprite.origin.set(8f, 0f);
        patrolSprite.tint.set(0.6f, 0.9f, 0.6f, 1f);
        patrolSprite.z = 1f;
        world.add(patroller, new Patrol());

        int player = world.create();
        world.setName(player, "player");
        playerTransform = world.add(player, Transform.class);
        playerTransform.teleport(-4f, 0f);
        playerSprite = world.add(player, new Sprite());
        playerSprite.regionId = "tex.player";
        playerSprite.origin.set(8f, 0f);   // anchor at the feet, centred
        playerSprite.z = 2f;
        playerControl = world.add(player, new PlayerControl());

        int menuCursor = world.create();
        world.setName(menuCursor, "menu cursor");
        cursor = world.add(menuCursor, Transform.class);
        cursorSprite = world.add(menuCursor, new Sprite());
        cursorSprite.regionId = "tex.player";
        cursorSprite.origin.set(8f, 8f);
        cursorSprite.tint.set(1f, 0.8f, 0.2f, 0f);   // hidden until the menu context is on top
        cursorSprite.z = 3f;

        int camera = world.create();
        CameraFollow follow = world.add(camera, new CameraFollow());
        follow.target = world.ref(player);
        follow.deadZone.setSize(4f, 2f);

        world.addSystem(new PatrolSystem(world));
        world.addSystem(new PlayerControlSystem(world, input));
        world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));
        world.addSystem(new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time()));

        context.events().on(InputContextChanged.class, e -> LOG.info("Input context: " + e.top()));
    }

    @Override
    protected void update(float delta) {
        if ("gameplay".equals(input.topContext())) {
            if (input.justPressed("pause")) {
                boolean paused = !context().time().paused();
                context().time().setPaused(paused);
                playerSprite.tint.a = paused ? 0.5f : 1f;
                LOG.info(paused ? "Paused" : "Running");
            }
            if (input.justPressed("tap")) {
                input.pointerWorld(0, playerControl.target);
                playerControl.hasTarget = true;
            }
            if (input.justPressed("menu")) {
                playerControl.hasTarget = false;
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
