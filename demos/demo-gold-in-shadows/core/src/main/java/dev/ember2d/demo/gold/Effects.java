package dev.ember2d.demo.gold;

import dev.ember2d.animation.Animator;
import dev.ember2d.ecs.Transform;
import dev.ember2d.ecs.World;
import dev.ember2d.kit.common.Lifetime;
import dev.ember2d.render.Sprite;

/**
 * Short sprite effects (F-029): the rifle's muzzle flash and bullet impacts, played once from {@code anim.fx} and then
 * removed by kit-common's {@code Lifetime}. Same sheets and frame order as the original's {@code EffectType}.
 *
 * <p>Effects are often made while a system runs (a shot during the weapon step), when added components are queued,
 * so this class only uses the instances {@code add} returns.
 */
final class Effects {

    static final String LAYER = "hand";

    private Effects() {
    }

    /** A muzzle flash at the rifle's muzzle, turned along the shot. */
    static int muzzle(World world, float x, float y, float degrees, boolean flipped) {
        int e = world.create();
        Transform t = world.add(e, new Transform());
        t.teleport(x, y);
        t.rotation = degrees;
        t.previousRotation = degrees;
        Sprite sprite = sprite(world, e, "muzzle", 0.3f);
        sprite.flipY = flipped;
        return e;
    }

    /** A bullet impact where a shot stopped. */
    static int impact(World world, float x, float y) {
        int e = world.create();
        world.add(e, new Transform()).teleport(x, y);
        sprite(world, e, "impact", 0.35f);
        return e;
    }

    private static Sprite sprite(World world, int e, String clip, float seconds) {
        Sprite sprite = world.add(e, new Sprite());
        sprite.origin.set(8f, 8f);
        sprite.layer = LAYER;
        Animator animator = new Animator();
        animator.clipSet = "anim.fx";
        animator.play(clip);
        world.add(e, animator);
        world.add(e, new Lifetime()).seconds = seconds;
        return sprite;
    }
}
