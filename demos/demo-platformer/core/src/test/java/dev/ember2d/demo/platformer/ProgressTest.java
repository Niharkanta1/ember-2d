package dev.ember2d.demo.platformer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ember2d.save.SaveData;
import dev.ember2d.save.SlotInfo;
import dev.ember2d.save.SlotMeta;
import dev.ember2d.save.internal.FileSaveStore;
import dev.ember2d.save.internal.MemorySaveFiles;
import org.junit.jupiter.api.Test;

/** Campaign progress in save slot 0 (I-250). */
class ProgressTest {

    final MemorySaveFiles files = new MemorySaveFiles();

    FileSaveStore store() {
        return new FileSaveStore(files, 3, 1, () -> 0L);
    }

    @Test
    void finishingALevelUnlocksTheNextAndIsSaved() {
        Progress fresh = Progress.load(store());
        assertThat(fresh.unlocked()).isEqualTo(1);
        assertThat(fresh.isUnlocked(2)).isFalse();

        assertThat(fresh.completed(1, 40f).ok()).isTrue();
        Progress again = Progress.load(store());
        assertThat(again.unlocked()).isEqualTo(2);
        assertThat(again.playSeconds()).isEqualTo(40f);
        SlotInfo info = store().slots().get(Progress.SLOT);
        assertThat(info.summary()).isEqualTo("Level 2 of 3");
        assertThat(info.scene()).isEqualTo("level2");

        again.completed(1, 30f);   // replaying an earlier level keeps the newest unlock
        assertThat(Progress.load(store()).unlocked()).isEqualTo(2);
        Progress p = Progress.load(store());
        p.completed(2, 1f);
        p.completed(3, 1f);
        assertThat(Progress.load(store()).unlocked()).as("never beyond the last level").isEqualTo(3);
        assertThat(Progress.load(store()).playSeconds()).isEqualTo(72f);
    }

    @Test
    void aDamagedSlotFallsBackToTheBackupThenToAFreshStart() {
        Progress p = Progress.load(store());
        p.completed(1, 5f);
        p.completed(2, 5f);
        files.write("slot0.json", "{ broken");
        assertThat(Progress.load(store()).unlocked()).as("the backup: the last good save").isEqualTo(3);

        files.write("slot0.json.bak", "{ broken too");
        assertThat(Progress.load(store()).unlocked()).isEqualTo(1);
    }

    @Test
    void outOfRangeValuesAreClamped() {
        store().save(Progress.SLOT, new SlotMeta("x", 0f, ""), new SaveData().set("unlocked", 99)
                .set("playSeconds", -3f));
        Progress p = Progress.load(store());
        assertThat(p.unlocked()).isEqualTo(3);
        assertThat(p.playSeconds()).isZero();
    }
}
