package dev.ember2d.demo.shooter;

import dev.ember2d.core.Phase;
import dev.ember2d.ecs.EntitySystem;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.topdown.shooter.Aim;
import dev.ember2d.render.Sprite;

/**
 * Keeps the player's gun in the player's hand, rotated to the aim (about its grip) and flipped when aiming left. The
 * gun is drawn on its own render layer ({@code hand}) above the y-sorted world.
 */
final class GunHolderSystem extends EntitySystem {

    private final int player;

    GunHolderSystem(World world, int player) {
        super(Phase.LATE, world.family(GunHolder.class, Transform.class, Sprite.class));
        this.player = player;
    }

    @Override
    protected void process(int entity, float delta) {
        Transform owner = world().get(player, Transform.class);
        Aim aim = world().get(player, Aim.class);
        if (owner == null || aim == null) {
            return;
        }
        Transform t = world().get(entity, Transform.class);
        t.position.set(owner.position.x + aim.aimX * 0.2f, owner.position.y + 0.35f + aim.aimY * 0.1f);
        t.previous.set(t.position);
        t.scale.set(0.7f, 0.7f);   // the pack's gun art is large next to the 16 px character
        t.rotation = aim.angle;
        t.previousRotation = aim.angle;
        world().get(entity, Sprite.class).flipY = aim.aimX < 0f;
    }

    /** Marks the gun entity. */
    static final class GunHolder {
    }
}
