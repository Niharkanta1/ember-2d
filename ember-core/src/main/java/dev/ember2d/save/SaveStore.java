package dev.ember2d.save;

import dev.ember2d.core.PublicApi;
import java.util.List;

/**
 * Save slots, global data and the settings store (F-013), from {@code ctx.saves()}.
 *
 * <p>Slots are written atomically with a backup of the previous save; loading never throws for bad files but reports
 * {@link SlotState#DAMAGED} or {@link SlotState#TOO_NEW}. Older saves are upgraded by the registered migrations.
 * {@link #settings()} and {@link #global()} are live trees written by {@link #flush()} when they changed; the engine
 * flushes on suspend and at shutdown.
 */
@PublicApi
public interface SaveStore {

    int slotCount();

    /** The current save version ({@code EngineConfig.saveVersion}). */
    int version();

    /** Registers the step that upgrades data from {@code fromVersion} to {@code fromVersion + 1}. */
    void addMigration(int fromVersion, SaveMigration migration);

    /** Writes a slot; the previous save becomes the slot's backup. */
    SaveResult save(int slot, SlotMeta meta, SaveData data);

    LoadResult load(int slot);

    /** Loads the slot's backup: the save before the last one. */
    LoadResult loadBackup(int slot);

    /** Every slot's metadata, in slot order. */
    List<SlotInfo> slots();

    /** Deletes a slot and its backup; returns whether there was anything to delete. */
    boolean delete(int slot);

    /** The settings (volumes, controls and the game's own keys), kept in {@code settings.json}. */
    SaveData settings();

    /** Data shared by all slots (unlocks, best times), kept in {@code global.json}. */
    SaveData global();

    /** Writes the settings and global data if they changed since the last write. */
    SaveResult flush();

    /** A save the game wants written when the app is suspended (Android may kill it); null for none. */
    void setSuspendSave(Runnable hook);
}
