package dev.ember2d.kit.topdown.shooter;

import dev.ember2d.core.PublicApi;

/**
 * The shooter's effect events (F-016). Each is a reused, mutable object published synchronously by
 * {@link WeaponSystem}, so shooting allocates nothing: read the fields inside the handler and do not keep the object.
 */
@PublicApi
public final class ShotEvents {

    private ShotEvents() {
    }

    /** A weapon fired one shot. */
    public static final class Fired {
        public int entity;
        public String weapon;
        public float x;
        public float y;
        public float angle;
        public int ammo;
    }

    /** A reload started (empty magazine, the reload action, or {@code WeaponSystem.reload}). */
    public static final class ReloadStarted {
        public int entity;
        public String weapon;
        public float seconds;
    }

    /** A reload finished; the magazine is full. */
    public static final class Reloaded {
        public int entity;
        public String weapon;
        public int ammo;
    }
}
