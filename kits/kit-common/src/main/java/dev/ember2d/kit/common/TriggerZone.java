package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/**
 * A trigger area with filters (F-021): pickups, checkpoints, doors, level exits. The entity also needs a trigger
 * {@code Collider} (its mask filters by layer); {@link TriggerZoneSystem} publishes {@link ZoneEntered} and
 * {@link ZoneExited} for entities that pass the filters.
 */
@PublicApi
public final class TriggerZone {
    /** Only entities with this world tag; empty for any. */
    public String tag = "";
    /** Only entities of this team; -1 for any. */
    public int team = -1;
    /** Fire for the first matching entity only. */
    public boolean once;
    /** Runtime: a {@link #once} zone has fired. */
    public boolean fired;
}
