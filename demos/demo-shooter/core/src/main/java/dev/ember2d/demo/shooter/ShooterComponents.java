package dev.ember2d.demo.shooter;

import dev.ember2d.camera.CameraFollow;
import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;
import dev.ember2d.kit.bullets.BulletsKit;
import dev.ember2d.kit.common.CommonKit;
import dev.ember2d.kit.topdown.TopDownKit;
import dev.ember2d.kit.topdown.shooter.ShooterKit;

/** Every component the shooter demo's prefabs and maps use (also drives the R8 keep rules). */
public final class ShooterComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        CommonKit.register(registry);    // Health, Team, Hitbox, Hurtbox, Lifetime, TriggerZone, Spawner
        BulletsKit.register(registry);   // BulletTarget
        TopDownKit.register(registry);   // TopDownBody, Steering
        ShooterKit.register(registry);   // Aim, Weapon
        registry.register(CameraFollow.class, CameraFollow::new)
                .register(Enemy.class, Enemy::new)
                .register(Gunner.class, Gunner::new)
                .register(Boss.class, Boss::new)
                .register(Pickup.class, Pickup::new)
                .register(Exit.class, Exit::new);
    }
}
