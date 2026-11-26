package dev.ember2d.save.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ember2d.save.LoadResult;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.SlotInfo;
import dev.ember2d.save.SlotMeta;
import dev.ember2d.save.SlotState;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Save slots on a real folder (F-013, I-230): atomic writes, crashes, damage, migrations, settings. */
class FileSaveStoreTest {

    @TempDir
    File dir;
    long now = 1_000L;

    FileSaveStore store(int version) {
        return new FileSaveStore(new DirSaveFiles(dir), 3, version, () -> now);
    }

    static SaveData progress(int coins) {
        return new SaveData().set("coins", coins);
    }

    static final SlotMeta META = new SlotMeta("level1", 75.5f, "Coins: 3");

    @Test
    void aSlotRoundTripsWithItsMetadata() {
        FileSaveStore store = store(1);
        assertThat(store.load(0).state()).isEqualTo(SlotState.EMPTY);
        assertThat(store.save(0, META, progress(3)).ok()).isTrue();

        LoadResult r = store(1).load(0);
        assertThat(r.ok()).isTrue();
        assertThat(r.data().getInt("coins", 0)).isEqualTo(3);
        assertThat(r.info()).isEqualTo(new SlotInfo(0, SlotState.OK, 1, 1_000L, 75.5f, "level1", "Coins: 3", false,
                ""));
        assertThat(store.slots()).extracting(SlotInfo::state).containsExactly(SlotState.OK, SlotState.EMPTY,
                SlotState.EMPTY);
        assertThatThrownBy(() -> store.load(3)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("slot must be from 0 to 2 (was 3)");
    }

    @Test
    void theSecondSaveKeepsTheFirstAsABackup() {
        FileSaveStore store = store(1);
        store.save(1, META, progress(1));
        now = 2_000L;
        store.save(1, META, progress(2));
        assertThat(store.load(1).data().getInt("coins", 0)).isEqualTo(2);
        assertThat(store.load(1).info().backupAvailable()).isTrue();
        assertThat(store.loadBackup(1).data().getInt("coins", 0)).isEqualTo(1);
        assertThat(store.delete(1)).isTrue();
        assertThat(store.load(1).state()).isEqualTo(SlotState.EMPTY);
        assertThat(store.loadBackup(1).state()).isEqualTo(SlotState.EMPTY);
        assertThat(store.delete(1)).isFalse();
    }

    @Test
    void aCrashBeforeTheRenameLeavesThePreviousSave() {
        DirSaveFiles files = new DirSaveFiles(dir);
        FileSaveStore store = new FileSaveStore(files, 3, 1, () -> now);
        store.save(0, META, progress(1));

        files.setAfterStep(step -> {
            if (step.equals("tmp")) {
                throw new IllegalStateException("power cut");
            }
        });
        assertThat(store.save(0, META, progress(2)).ok()).isFalse();
        assertThat(store(1).load(0).data().getInt("coins", 0)).as("the tmp file is ignored").isEqualTo(1);

        files.setAfterStep(step -> {
            if (step.equals("backup")) {
                throw new IllegalStateException("power cut");
            }
        });
        assertThat(store.save(0, META, progress(3)).ok()).isFalse();
        LoadResult r = store(1).load(0);
        assertThat(r.state()).isEqualTo(SlotState.DAMAGED);
        assertThat(r.info().problem()).isEqualTo("the save file is missing (an interrupted save?)");
        assertThat(r.info().backupAvailable()).isTrue();
        assertThat(store(1).loadBackup(0).data().getInt("coins", 0)).as("the previous save").isEqualTo(1);

        files.setAfterStep(step -> { });
        assertThat(store.save(0, META, progress(4)).ok()).as("the next save works again").isTrue();
        assertThat(store.load(0).data().getInt("coins", 0)).isEqualTo(4);
    }

    @Test
    void aCorruptFileIsDamagedAndTheBackupIsOffered() throws IOException {
        FileSaveStore store = store(1);
        store.save(2, META, progress(5));
        store.save(2, META, progress(6));
        write("slot2.json", "{\"format\": 1, \"vers");
        LoadResult r = store.load(2);
        assertThat(r.state()).isEqualTo(SlotState.DAMAGED);
        assertThat(r.data()).isNull();
        assertThat(r.info().problem()).startsWith("the save is not readable (not valid JSON");
        assertThat(r.info().backupAvailable()).isTrue();
        assertThat(store.loadBackup(2).data().getInt("coins", 0)).isEqualTo(5);

        write("slot2.json", "{\"format\": 1, \"version\": 1, \"timestamp\": \"5\"}");
        assertThat(store.load(2).info().problem()).isEqualTo("the data is missing");
        write("slot2.json", "{\"format\": 9}");
        assertThat(store.load(2).info().problem()).isEqualTo("unknown save format 9");
    }

    @Test
    void olderSavesAreMigratedAndNewerOnesRefused() {
        store(1).save(0, META, new SaveData().set("gold", 10));

        FileSaveStore v3 = store(3);
        v3.addMigration(1, d -> {
            d.set("coins", d.getInt("gold", 0));
            d.remove("gold");
        });
        assertThat(v3.load(0).info().problem()).isEqualTo("no migration from save version 2 to 3");
        v3.addMigration(2, d -> d.set("lives", 3));
        LoadResult r = v3.load(0);
        assertThat(r.ok()).isTrue();
        assertThat(r.info().version()).as("the file's version").isEqualTo(1);
        assertThat(r.data().toJson()).isEqualTo(new SaveData().set("coins", 10).set("lives", 3).toJson());
        assertThatThrownBy(() -> v3.addMigration(2, d -> { })).hasMessageContaining("already registered");
        assertThatThrownBy(() -> v3.addMigration(3, d -> { })).hasMessage("fromVersion must be from 1 to 2 (was 3)");

        v3.save(1, META, progress(1));
        LoadResult tooNew = store(1).load(1);
        assertThat(tooNew.state()).isEqualTo(SlotState.TOO_NEW);
        assertThat(tooNew.info().problem()).contains("save version 3", "reads up to 1");
        assertThat(tooNew.info().scene()).isEqualTo("level1");
    }

    @Test
    void aFailingMigrationDamagesTheLoadNotTheFile() {
        store(1).save(0, META, progress(1));
        FileSaveStore v2 = store(2);
        v2.addMigration(1, d -> {
            throw new IllegalStateException("bad data");
        });
        assertThat(v2.load(0).info().problem()).isEqualTo("migrating from version 1 failed: bad data");
        assertThat(store(1).load(0).ok()).isTrue();
    }

    @Test
    void settingsAndGlobalDataAreWrittenOnlyWhenChanged() throws IOException {
        FileSaveStore store = store(1);
        assertThat(store.flush().ok()).isTrue();
        assertThat(new File(dir, "settings.json")).as("nothing changed: nothing written").doesNotExist();

        store.settings().set("audio.music", 0.4f);
        store.global().child("best").set("level1", 31.5f);
        assertThat(store.flush().ok()).isTrue();
        long written = new File(dir, "settings.json").lastModified();
        assertThat(new File(dir, "global.json")).exists();

        FileSaveStore again = store(1);
        assertThat(again.settings().getFloat("audio.music", 1f)).isEqualTo(0.4f);
        assertThat(again.global().child("best").getFloat("level1", 0f)).isEqualTo(31.5f);
        assertThat(again.flush().ok()).isTrue();
        assertThat(new File(dir, "settings.json.bak")).as("not rewritten").doesNotExist();
        assertThat(new File(dir, "settings.json").lastModified()).isEqualTo(written);

        write("settings.json", "not json");
        assertThat(store(1).settings().isEmpty()).as("damaged settings are ignored").isTrue();
    }

    @Test
    void theSuspendHookRunsThenFlushes() {
        FileSaveStore store = store(1);
        store.setSuspendSave(() -> store.save(0, META, progress(9)));
        store.settings().set("x", 1);
        store.onSuspend();
        assertThat(store(1).load(0).data().getInt("coins", 0)).isEqualTo(9);
        assertThat(store(1).settings().getInt("x", 0)).isEqualTo(1);

        store.setSuspendSave(() -> {
            throw new IllegalStateException("boom");
        });
        store.settings().set("x", 2);
        store.onSuspend();   // logged, and the settings are still written
        assertThat(store(1).settings().getInt("x", 0)).isEqualTo(2);
    }

    @Test
    void theMemoryStoreWorksTheSameWithoutFiles() {
        FileSaveStore store = new FileSaveStore(new MemorySaveFiles(), 1, 1, () -> 7L);
        store.save(0, META, progress(1));
        store.save(0, META, progress(2));
        assertThat(store.load(0).data().getInt("coins", 0)).isEqualTo(2);
        assertThat(store.loadBackup(0).data().getInt("coins", 0)).isEqualTo(1);
        assertThat(store.location()).isEqualTo("memory (not kept)");
    }

    void write(String name, String text) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
