package dev.ember2d.demo.platformer;

import dev.ember2d.camera.CameraFollow;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;
import dev.ember2d.kit.platformer.PlatformerKit;

/** The demo's components usable from prefabs and Tiled (F-010); also read by the build for R8 keep rules. */
public final class DemoComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        PlatformerKit.register(registry);   // PlatformerBody, Patroller, MovingPlatform, Ladder
        registry.register(CameraFollow.class, CameraFollow::new)
                .register(Enemy.class, Enemy::new)
                .register(Hazard.class, Hazard::new)
                .register(Checkpoint.class, Checkpoint::new)
                .register(Goal.class, Goal::new);
    }
}
