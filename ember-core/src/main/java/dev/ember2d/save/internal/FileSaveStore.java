package dev.ember2d.save.internal;

import dev.ember2d.core.Log;
import dev.ember2d.save.LoadResult;
import dev.ember2d.save.SaveData;
import dev.ember2d.save.SaveMigration;
import dev.ember2d.save.SaveResult;
import dev.ember2d.save.SaveStore;
import dev.ember2d.save.SlotInfo;
import dev.ember2d.save.SlotMeta;
import dev.ember2d.save.SlotState;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The engine's {@link SaveStore} (F-013, I-230) over {@link SaveFiles}. A slot file is a {@link SaveData} with the
 * fields {@code format} (1), {@code version}, {@code timestamp} (milliseconds as a string: a long does not fit the
 * tree's numbers), {@code playTime}, {@code scene}, {@code summary} and {@code data}.
 */
public final class FileSaveStore implements SaveStore {

    /** The slot file layout; changes only if this class changes how it writes slots. */
    static final int FORMAT = 1;
    static final String SETTINGS = "settings.json";
    static final String GLOBAL = "global.json";

    private static final Log LOG = Log.get("save");

    private final SaveFiles files;
    private final int slotCount;
    private final int version;
    private final LongSupplier clock;
    private final Map<Integer, SaveMigration> migrations = new HashMap<>();
    private final SaveData settings;
    private final SaveData global;
    private String settingsWritten;
    private String globalWritten;
    private Runnable suspendSave;

    public FileSaveStore(SaveFiles files, int slotCount, int version, LongSupplier clock) {
        this.files = Objects.requireNonNull(files, "files");
        if (slotCount < 1) {
            throw new IllegalArgumentException("slotCount must be >= 1 (was " + slotCount + ")");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1 (was " + version + ")");
        }
        this.slotCount = slotCount;
        this.version = version;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = readTree(SETTINGS);
        this.settingsWritten = settings.toJson();
        this.global = readTree(GLOBAL);
        this.globalWritten = global.toJson();
    }

    /** Reads settings or global data; a damaged file is logged and replaced on the next flush (kept as .bak). */
    private SaveData readTree(String name) {
        try {
            String text = files.read(name);
            return text == null ? new SaveData() : SaveData.parse(text);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warn("Ignoring damaged " + name + " in " + files.describe() + ": " + e.getMessage());
            return new SaveData();
        }
    }

    @Override
    public int slotCount() {
        return slotCount;
    }

    @Override
    public int version() {
        return version;
    }

    @Override
    public void addMigration(int fromVersion, SaveMigration migration) {
        if (fromVersion < 1 || fromVersion >= version) {
            throw new IllegalArgumentException("fromVersion must be from 1 to " + (version - 1) + " (was "
                    + fromVersion + ")");
        }
        if (migrations.putIfAbsent(fromVersion, Objects.requireNonNull(migration, "migration")) != null) {
            throw new IllegalArgumentException("A migration from version " + fromVersion + " is already registered");
        }
    }

    @Override
    public SaveResult save(int slot, SlotMeta meta, SaveData data) {
        String name = slotFile(slot);
        Objects.requireNonNull(meta, "meta");
        SaveData file = new SaveData().set("format", FORMAT).set("version", version)
                .set("timestamp", Long.toString(clock.getAsLong())).set("playTime", meta.playTimeSeconds())
                .set("scene", meta.scene()).set("summary", meta.summary());
        file.set("data", Objects.requireNonNull(data, "data"));
        try {
            files.write(name, file.toJson());
            LOG.info("Saved slot " + slot);
            return SaveResult.success();
        } catch (IOException | RuntimeException e) {
            LOG.error("Saving slot " + slot + " failed", e);
            return SaveResult.failed("Saving slot " + slot + " failed: " + e.getMessage());
        }
    }

    @Override
    public LoadResult load(int slot) {
        String name = slotFile(slot);
        boolean backup = files.exists(name + ".bak");
        String text;
        try {
            text = files.read(name);
        } catch (IOException e) {
            return damaged(slot, backup, "cannot read the save: " + e.getMessage());
        }
        if (text == null) {
            return backup ? damaged(slot, true, "the save file is missing (an interrupted save?)")
                    : new LoadResult(SlotState.EMPTY, null, SlotInfo.without(slot, SlotState.EMPTY, false, ""));
        }
        return decode(slot, text, backup);
    }

    @Override
    public LoadResult loadBackup(int slot) {
        String name = slotFile(slot) + ".bak";
        try {
            String text = files.read(name);
            return text == null
                    ? new LoadResult(SlotState.EMPTY, null, SlotInfo.without(slot, SlotState.EMPTY, false, ""))
                    : decode(slot, text, true);
        } catch (IOException e) {
            return damaged(slot, true, "cannot read the backup: " + e.getMessage());
        }
    }

    private LoadResult decode(int slot, String text, boolean backup) {
        SaveData file;
        try {
            file = SaveData.parse(text);
        } catch (IllegalArgumentException e) {
            return damaged(slot, backup, "the save is not readable (" + e.getMessage() + ")");
        }
        if (file.getInt("format", -1) != FORMAT) {
            return damaged(slot, backup, "unknown save format " + file.getInt("format", -1));
        }
        int fileVersion = file.getInt("version", 0);
        long timestamp;
        try {
            timestamp = Long.parseLong(file.getString("timestamp", ""));
        } catch (NumberFormatException e) {
            return damaged(slot, backup, "the timestamp is missing or not a number");
        }
        if (fileVersion < 1 || !file.has("data")) {
            return damaged(slot, backup, fileVersion < 1 ? "the version is missing" : "the data is missing");
        }
        if (fileVersion > version) {
            SlotInfo info = new SlotInfo(slot, SlotState.TOO_NEW, fileVersion, timestamp,
                    file.getFloat("playTime", 0f), file.getString("scene", ""), file.getString("summary", ""), backup,
                    "saved by a newer version of the game (save version " + fileVersion + ", this game reads up to "
                            + version + ")");
            return new LoadResult(SlotState.TOO_NEW, null, info);
        }
        SaveData data;
        try {
            data = file.child("data");
        } catch (IllegalStateException e) {
            return damaged(slot, backup, "the data is not an object");
        }
        for (int v = fileVersion; v < version; v++) {
            SaveMigration step = migrations.get(v);
            if (step == null) {
                return damaged(slot, backup, "no migration from save version " + v + " to " + (v + 1));
            }
            try {
                step.migrate(data);
            } catch (RuntimeException e) {
                LOG.error("Migrating slot " + slot + " from version " + v + " failed", e);
                return damaged(slot, backup, "migrating from version " + v + " failed: " + e.getMessage());
            }
        }
        SlotInfo info = new SlotInfo(slot, SlotState.OK, fileVersion, timestamp, file.getFloat("playTime", 0f),
                file.getString("scene", ""), file.getString("summary", ""), backup, "");
        return new LoadResult(SlotState.OK, data, info);
    }

    private static LoadResult damaged(int slot, boolean backup, String problem) {
        LOG.warn("Slot " + slot + " is damaged: " + problem + (backup ? " (a backup exists)" : ""));
        return new LoadResult(SlotState.DAMAGED, null, SlotInfo.without(slot, SlotState.DAMAGED, backup, problem));
    }

    @Override
    public List<SlotInfo> slots() {
        List<SlotInfo> out = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            out.add(load(i).info());
        }
        return out;
    }

    @Override
    public boolean delete(int slot) {
        String name = slotFile(slot);
        boolean any = files.delete(name);
        any |= files.delete(name + ".bak");
        files.delete(name + ".tmp");
        return any;
    }

    @Override
    public SaveData settings() {
        return settings;
    }

    @Override
    public SaveData global() {
        return global;
    }

    @Override
    public SaveResult flush() {
        List<String> errors = new ArrayList<>(2);
        String s = settings.toJson();
        if (!s.equals(settingsWritten)) {
            if (write(SETTINGS, s, errors)) {
                settingsWritten = s;
            }
        }
        String g = global.toJson();
        if (!g.equals(globalWritten)) {
            if (write(GLOBAL, g, errors)) {
                globalWritten = g;
            }
        }
        return errors.isEmpty() ? SaveResult.success() : SaveResult.failed(String.join("; ", errors));
    }

    private boolean write(String name, String json, List<String> errors) {
        try {
            files.write(name, json);
            LOG.debug("Wrote " + name);
            return true;
        } catch (IOException | RuntimeException e) {
            LOG.error("Writing " + name + " failed", e);
            errors.add("Writing " + name + " failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void setSuspendSave(Runnable hook) {
        this.suspendSave = hook;
    }

    /** Engine: on suspend, runs the game's suspend save (errors logged), then flushes. */
    public void onSuspend() {
        if (suspendSave != null) {
            try {
                suspendSave.run();
            } catch (RuntimeException e) {
                LOG.error("The suspend save failed", e);
            }
        }
        flush();
    }

    /** Where the saves are, for log lines. */
    public String location() {
        return files.describe();
    }

    private String slotFile(int slot) {
        if (slot < 0 || slot >= slotCount) {
            throw new IllegalArgumentException("slot must be from 0 to " + (slotCount - 1) + " (was " + slot + ")");
        }
        return "slot" + slot + ".json";
    }
}
