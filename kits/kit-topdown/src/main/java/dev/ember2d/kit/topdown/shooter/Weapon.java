package dev.ember2d.kit.topdown.shooter;

import dev.ember2d.core.PublicApi;

/**
 * A weapon held by an entity (F-016), fired by WeaponSystem. With fireAction set the trigger is read from input;
 * otherwise AI or game code sets trigger.
 */
@PublicApi
public final class Weapon {
    /** Asset ID of the weapon definition (see WeaponLibrary). */
    public String weapon = "";
    /** Input action that pulls the trigger; empty when AI or code sets trigger. */
    public String fireAction = "";
    /** Input action that starts a reload; empty for none. */
    public String reloadAction = "";
    /** Runtime: the trigger is pulled. */
    public boolean trigger;
    /** Runtime: seconds until the next shot. */
    public float cooldown;
    /** Runtime: rounds left; -1 starts full. */
    public int ammo = -1;
    /** Runtime: seconds of reload left. */
    public float reloadLeft;
    public boolean reloading;
    /** Runtime: the trigger was pulled on the previous step (single-shot weapons need a new pull). */
    public boolean wasPulled;
}
