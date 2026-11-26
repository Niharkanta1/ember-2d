package dev.ember2d.demo.platformer;

import dev.ember2d.camera.CameraFollow;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;

/** The demo's components usable from prefabs and Tiled (F-010); also read by the build for R8 keep rules. */
public final class DemoComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        registry.register(Patrol.class, Patrol::new)
                .register(PlayerControl.class, PlayerControl::new)
                .register(CameraFollow.class, CameraFollow::new);
    }
}
