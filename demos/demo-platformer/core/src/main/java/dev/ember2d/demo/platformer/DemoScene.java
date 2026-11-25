package dev.ember2d.demo.platformer;

import dev.ember2d.app.EngineContext;
import dev.ember2d.app.Scene;
import dev.ember2d.camera.CameraFollow;
import dev.ember2d.camera.CameraFollowSystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.render.Sprite;
import dev.ember2d.render.SpriteRenderSystem;

/**
 * Phase 1 check scene: a sprite patrols left and right at a constant speed while the camera follows it through a dead
 * zone; fixed markers along the ground show the scrolling. Motion must look smooth at 60 Hz and 144 Hz.
 */
final class DemoScene extends Scene {

    @Override
    protected void enter(EngineContext context) {
        World world = world();

        for (int i = -16; i <= 16; i += 2) {
            int marker = world.create();
            world.add(marker, Transform.class).teleport(i, -2f);
            Sprite sprite = world.add(marker, new Sprite());
            sprite.regionId = "tex.player";
            sprite.tint.set(0.35f, 0.3f, 0.45f, 1f);
            sprite.z = 0f;
        }

        int player = world.create();
        world.setName(player, "player");
        world.add(player, Transform.class).teleport(0f, 0f);
        Sprite sprite = world.add(player, new Sprite());
        sprite.regionId = "tex.player";
        sprite.origin.set(8f, 0f);   // anchor at the feet, centred
        sprite.z = 1f;
        world.add(player, new Patrol());

        int camera = world.create();
        CameraFollow follow = world.add(camera, new CameraFollow());
        follow.target = world.ref(player);
        follow.deadZone.setSize(4f, 2f);

        world.addSystem(new PatrolSystem(world));
        world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));
        world.addSystem(new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time()));
    }
}
