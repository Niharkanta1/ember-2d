package dev.ember2d.demo.platformer;

import dev.ember2d.core.Log;
import dev.ember2d.save.LoadResult;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.SaveResult;
import dev.ember2d.save.SaveStore;
import dev.ember2d.save.SlotMeta;

/**
 * The campaign's progress (I-250) in save slot 0: the highest unlocked level and the total play time. A damaged slot
 * falls back to its backup, and to a fresh start when that is not readable either.
 */
final class Progress {

    static final int SLOT = 0;
    private static final Log LOG = Log.get("demo");

    private final SaveStore saves;
    private int unlocked = 1;
    private float playSeconds;

    private Progress(SaveStore saves) {
        this.saves = saves;
    }

    /** Reads slot 0 (never throws; problems are logged). */
    static Progress load(SaveStore saves) {
        Progress p = new Progress(saves);
        LoadResult r = saves.load(SLOT);
        if (!r.ok() && r.info() != null && r.info().backupAvailable()) {
            LOG.warn("Progress save: " + r.info().problem() + "; using the backup");
            r = saves.loadBackup(SLOT);
        }
        if (r.ok()) {
            SaveData d = r.data();
            p.unlocked = Math.max(1, Math.min(Levels.count(), d.getInt("unlocked", 1)));
            p.playSeconds = Math.max(0f, d.getFloat("playSeconds", 0f));
        } else if (r.info() != null && r.info().problem() != null && !r.info().problem().isEmpty()) {
            LOG.warn("Progress save not readable (" + r.info().problem() + "); starting at level 1");
        }
        return p;
    }

    /** The highest level that can be played, 1 to {@link Levels#count()}. */
    int unlocked() {
        return unlocked;
    }

    boolean isUnlocked(int level) {
        return level <= unlocked;
    }

    float playSeconds() {
        return playSeconds;
    }

    /** Records a finished level: adds its time, unlocks the next level and writes slot 0. */
    SaveResult completed(int level, float seconds) {
        playSeconds += seconds;
        unlocked = Math.max(unlocked, Math.min(Levels.count(), level + 1));
        SaveData data = new SaveData().set("unlocked", unlocked).set("playSeconds", playSeconds);
        SaveResult result = saves.save(SLOT, new SlotMeta("level" + unlocked, playSeconds,
                "Level " + unlocked + " of " + Levels.count()), data);
        if (!result.ok()) {
            LOG.error("Saving progress failed: " + result.error());
        }
        return result;
    }
}
