package dev.ember2d.kit.common;

import dev.ember2d.core.PublicApi;

/** Spawns a prefab at intervals, in bursts or waves, with a cap on living instances (F-021); see {@link SpawnerSystem}. */
@PublicApi
public final class Spawner {
    /** Prefab ID to spawn. */
    public String prefab = "";
    /** Seconds between spawns. */
    public float interval = 2f;
    /** Instances per spawn. */
    public int burst = 1;
    /** Instances to spawn in all; 0 for no end. */
    public int total;
    /** At most this many living instances. */
    public int maxAlive = 5;
    /** Seconds before the first spawn (after the zone, with {@link #waitFor}). */
    public float delay;
    /** Name of a {@link TriggerZone} entity to wait for; empty to start at once. */
    public String waitFor = "";
    /** Spawn position relative to the spawner. */
    public float offsetX;
    public float offsetY;
    /** Runtime: started (no zone to wait for, or the zone fired). */
    public boolean started;
    /** Runtime: seconds to the next spawn. */
    public float wait = -1f;
    /** Runtime: instances spawned so far. */
    public int spawned;
}
