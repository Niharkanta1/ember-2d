package dev.ember2d.demo.gold;

import dev.ember2d.data.ComponentRegistry;
import dev.ember2d.data.ComponentSetup;
import dev.ember2d.kit.bullets.BulletsKit;
import dev.ember2d.kit.common.CommonKit;
import dev.ember2d.kit.topdown.TopDownKit;
import dev.ember2d.kit.topdown.shooter.ShooterKit;
import dev.ember2d.kit.topdown.vision.VisionKit;

/**
 * Every component Gold in Shadows' prefabs and maps create (F-029). Also drives the Android release build's R8 keep
 * rules, so a component missing here works in debug builds but not in release.
 */
public final class GoldComponents implements ComponentSetup {

    @Override
    public void register(ComponentRegistry registry) {
        CommonKit.register(registry);    // Health, Team, Hitbox, Hurtbox, Lifetime, HitFlash, ...
        BulletsKit.register(registry);   // BulletTarget
        TopDownKit.register(registry);   // TopDownBody, Steering
        ShooterKit.register(registry);   // Aim, Weapon
        VisionKit.register(registry);    // Viewer, HiddenInFog
        registry.register(Hero.class, Hero::new)
                .register(GoldCamera.class, GoldCamera::new)
                .register(Creature.class, Creature::new)
                .register(Pickup.class, Pickup::new)
                .register(Chest.class, Chest::new)
                .register(SpikeTrap.class, SpikeTrap::new)
                .register(ExitDoor.class, ExitDoor::new);
    }
}
