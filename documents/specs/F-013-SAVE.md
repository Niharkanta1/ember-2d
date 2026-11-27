# F-013 — Save Slots, World Flags and Settings

- Status: Settings, save slots and global data (I-230) Ready: expanded and approved 2026-11-27 under the developer's blanket approval up to Phase 6 (see "Design (I-230)"); world flags Ready (I-430, approved 2026-11-27; see "World flags (I-430)")
- Phase: 3 (settings, save slots), 5 (world flags)
- Module / package: `ember-core` / `ember.save`
- Depends on: F-001, F-010 (`ember.data` migrations)
- Tasks: I-230, I-430

## Goal
Reliable, versioned persistence of player progress and settings, kept separate from authored data.

## Scope
**What is saved:** game-defined save objects (plain Java classes the game registers) plus **world flags** — a key/value store (`bool`, `int`, `string`) for persistent world state such as `chest.town.01.opened`. ECS snapshots are not saved.

**Save slots**
- N slots (configurable); each slot = JSON file with metadata (`version`, timestamp, play time, scene ID, game-defined summary).
- Atomic write: write to `slotN.json.tmp`, flush, then rename over `slotN.json`; keep `slotN.json.bak` of the previous save.
- Corrupt or unreadable file → error result with reason; the backup is offered.
- Version migration through `ember.data`.
- Location: libGDX local storage under a per-game folder (app-private storage on Android).
- Android: an automatic save is written on app suspend when the game allows it (game-defined hook), because Android may kill a backgrounded app.
- **Global persistent data** (not per slot): unlocked CGs, read-text tracking, achievements-style flags; stored in `global.json`.

**Settings store**
- Separate file `settings.json`: volumes, key bindings, window mode, UI scale, language, text speed, on-screen control layout, haptics.
- Loaded at boot before input and audio; saved on change.

## Non-goals
- Cloud saves, encryption, compression.

## API sketch
```java
@PublicApi public interface SaveStore {
    <T> void register(Class<T> type);
    SaveResult save(int slot, Object... objects); LoadResult load(int slot);
    WorldFlags flags(); List<SlotInfo> slots();
}
@PublicApi public interface WorldFlags { boolean getBool(String k); void set(String k, boolean v); int getInt(String k); void set(String k, int v); }
@PublicApi public interface Settings { float get(String key, float def); void set(String key, float v); /* plus int/bool/string */ }
```

## Design (I-230)
Status: Ready (approved 2026-11-27, blanket approval up to Phase 6).

**No reflection.** Rule 10 of `ARCHITECTURE.md` keeps reflection in `ember.data` and R8 would rename save classes' fields. So the API sketch's `register(Class)` / `save(slot, Object...)` becomes an explicit tree:
- **`SaveData`:** an ordered key/value tree. Values are boolean, int, float, string, nested `SaveData` and lists of `SaveData`. It writes and reads JSON (`toJson()`, `SaveData.parse`).
- **`SaveCodec<T>`:** a game writes and reads its own objects (`write(T, SaveData)`, `read(SaveData)`), used through `put(key, value, codec)` and `get(key, codec)`.
- **Typed getters** take a default and convert numbers (an int read as float and back).

**Save slots (`SaveStore`, `ctx.saves()`).**
- **Slots:** `EngineConfig.saveSlots` (default 3), numbered from 0.
- **API:** `save(slot, SlotMeta, SaveData)`, `load(slot)`, `loadBackup(slot)`, `slots()`, `delete(slot)`.
- **Slot file:** `slotN.json` holds `{format, version, timestamp, playTime, scene, summary, data}`.
- **Atomic write:**
  1. write `slotN.json.tmp` and sync it
  2. move the old `slotN.json` to `slotN.json.bak` (replacing the older backup)
  3. rename the tmp file to `slotN.json`

  A crash at any step leaves the previous save as `slotN.json` or `slotN.json.bak`.
- **Load results** (`LoadResult`):
  - `OK`, or `EMPTY` (no file)
  - `DAMAGED`: unreadable, not JSON, a missing field, or only a backup present. The result carries the reason and whether a backup exists.
  - `TOO_NEW`: the file's version is above `EngineConfig.saveVersion`.
- **Migrations:** `addMigration(fromVersion, SaveMigration)` upgrades the data one version at a time on load. A missing step gives `DAMAGED` with the reason. `ember.data` has no migration framework yet, so migrations live in `ember.save`.
- **Location:**
  - **Android:** `Gdx.files.local("saves")` (app-private).
  - **Desktop:** `Gdx.files.external(".ember2d/<saveFolder>")` (user home).
  - **No folder:** `EngineConfig.saveFolder` (a plain name) null keeps everything in memory (tests, demos without saves).
- **Suspend hook:** `setSuspendSave(Runnable)`. On Android pause the engine runs it (the game saves there if it allows), then flushes the settings and global data.

**Global data and settings.**
- **Global data:** `saves.global()`, a `SaveData` kept in `global.json`.
- **Settings:** `saves.settings()`, a `SaveData` kept in `settings.json`.
- **When they are written:** both are written atomically by `flush()` only when changed. The engine flushes on pause and dispose; games may call it.
- **Engine settings** (`app`, keys in `EngineSettings`):
  - **What:** `audio.master`, `audio.music`, `audio.sfx`, `input.haptics`, `input.touchLayout` (layout JSON), `input.bindings` (action → input names), `ui.scale`.
  - **At boot** they are applied after the bindings load and before the first scene.
  - **`ctx.saveSettings()`** copies the current values back and flushes (also done on pause and dispose).
  - **Bad stored values** are logged and skipped, never fatal.
- **Rebinding:**
  - **API:** `Input.rebindAction(action, inputs)`, `resetBinding(action)`, `bindingOverrides()`.
  - **Behaviour:** overrides apply over the bindings file, survive hot reload and keep the context stack.

**Demo.**
- saves to `demo-platformer`
- the settings menu's volumes, the edited touch layout and a new **Vibration on/off** item persist (saved when leaving the settings menu)
- the best time per level is kept in global data and shown on the results panel

**Not in I-230:**
- world flags (I-430)
- a load/save slot menu in the demo (the demo has no mid-level progress worth slots; slots are tested in unit tests)
- a key-rebinding menu (API only)
- a UI scale menu item (the key is applied at boot)

## API sketch (I-230)
```java
package dev.ember2d.save;
@PublicApi public final class SaveData {            // ordered tree, JSON in and out
    boolean getBool(String k, boolean d); int getInt(String k, int d); float getFloat(String k, float d);
    String getString(String k, String d); SaveData set(String k, boolean|int|float|String v);
    SaveData child(String k); /* get or create */ List<SaveData> list(String k); /* get or create */
    <T> SaveData put(String k, T v, SaveCodec<T> c); <T> T get(String k, SaveCodec<T> c, T d);
    boolean has(String k); boolean remove(String k); Set<String> keys(); String toJson(); static SaveData parse(String json);
}
@PublicApi public interface SaveCodec<T> { void write(T value, SaveData out); T read(SaveData in); }
@PublicApi public interface SaveMigration { void migrate(SaveData data); }
@PublicApi public record SlotMeta(String scene, float playTimeSeconds, String summary) { }
@PublicApi public record SlotInfo(int slot, SlotState state, int version, long timestamp, float playTimeSeconds,
        String scene, String summary, boolean backupAvailable, String problem) { }
@PublicApi public enum SlotState { EMPTY, OK, DAMAGED, TOO_NEW }
@PublicApi public record SaveResult(boolean ok, String error) { }
@PublicApi public record LoadResult(SlotState state, SaveData data, SlotInfo info) { }
@PublicApi public interface SaveStore {
    int slotCount(); int version(); void addMigration(int fromVersion, SaveMigration m);
    SaveResult save(int slot, SlotMeta meta, SaveData data); LoadResult load(int slot); LoadResult loadBackup(int slot);
    List<SlotInfo> slots(); boolean delete(int slot);
    SaveData settings(); SaveData global(); SaveResult flush(); void setSuspendSave(Runnable hook);
}
// internal: FileSaveStore over SaveFiles (DirSaveFiles with java.io, MemorySaveFiles)
// app: EngineConfig.saveFolder/saveSlots/saveVersion; EngineContext.saves(), saveSettings(); EngineSettings keys
// input: InputBindings.withActionInputs(action, inputs); Input.rebindAction/resetBinding/bindingOverrides
```

## World flags (I-430)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6.

**What they are:**
- **The store:** `WorldFlags` (in `ember.save`, available as `context.flags()`) is the persistent state of the world in the current play-through. For example `chest.town.01.opened = true`, `npc.elder.talked = 2` or `door.cellar = "open"`.
- **Values:** bool, int and String.
- **Keys:** non-empty strings. Dotted names are a convention, not a structure, so the keys are flat.

**API:**
- **Getters with defaults:** `getBool(key, def)`, `getInt(key, def)`, `getString(key, def)`. A flag of another type gives the default.
- **Changing flags:** `set(key, bool/int/String)`, `has(key)`, `remove(key)`, `clear()`, `keys()`.
- **`toggle(key)`:** flips a bool.
- **`add(key, delta)`:** for int counters.
- **Events:** setting a different value publishes `FlagChanged(key)` on the event bus, so doors and chests can react. Setting the same value publishes nothing.

**Saving:**
- **Into a slot:** flags are per play-through, so they belong in a save slot, not in global data. `save(SaveData out)` writes them under the child `flags`, and `load(SaveData in)` replaces them. A slot without flags clears them.
- **New game:** `clear()` empties them; no event is published.

**Wiring:** the `EngineContext.Builder` creates one `WorldFlags` per context. `Builder.flags()` exposes it for tests.

**Tests:**
- Typed getters with defaults, and a type mismatch giving the default.
- Toggle and add.
- Events only on change.
- The round trip through a slot.
- `load` replaces the flags, and a slot without flags clears them.
- Bad keys are rejected.

## Decisions in I-230
Implemented under the blanket approval of 2026-11-27. Settled while implementing:
- **Choosing the folder:** `save.internal.SaveFolders` picks the folder, because `ArchitectureTest` (rule 9) allows `Gdx.files` only in `ember.assets` and `ember.save`.
- **Slot timestamps** are stored as a string of milliseconds: the tree's numbers are int and float.
- **Settings are compared as JSON at `flush()`** rather than tracked with a dirty flag, so edits deep in nested trees are caught too. A damaged `settings.json` or `global.json` is logged and replaced on the next flush (the damaged file becomes the `.bak`).
- **Engine settings:**
  - `EngineSettings` is public for its key constants; `apply` and `capture` stay package-private.
  - The default touch layout is not stored.
  - `ui.scale` is read only (games write it).
- **Demo:**
  - The main menu shows only the panel while the settings are open: with 40 dp items, Back ran off a 1080 px screen (found on the emulator).
  - "Vibration" gives a sample buzz when switched on.
  - The results panel shows "First clear!", "New best!" or the best time.
- **Verified on the emulator:** changed Music and Vibration, force-stopped the app, and the values were back after a restart (`files/saves/settings.json` checked).
- **Not verified:**
  - the desktop folder `~/.ember2d/demo-platformer` (not run on desktop in this session)
  - the suspend save on a device kill (unit-tested only)

## Tests (I-230)
- **`SaveData`:**
  - JSON round trip of every value type, nested children and lists
  - defaults and number conversion
  - codecs
  - parse errors
- **`FileSaveStore`** on a temp folder:
  - save/load round trip and slot metadata
  - a crash after the tmp write and after the move to backup (the previous save still loads, directly or as a backup)
  - corrupt JSON gives `DAMAGED` with a backup offered
  - migration from version 1 to 3; a missing step; `TOO_NEW`
  - delete
  - settings and global data written only when changed and read back by a new store
  - the in-memory mode
- **Engine:**
  - settings applied at boot (volumes, haptics, layout, bindings)
  - `saveSettings` captures them
  - bad values skipped
  - the suspend hook runs on pause
- **Input:** a rebind keeps the context stack and survives `setBindings`; unknown action or input names are rejected.
- **Manual (emulator):** change volumes, vibration and the layout, kill the app, start again, and they are kept.

## Acceptance criteria
- Round trip of save objects and world flags.
- Simulated crash between write and rename leaves the previous save loadable.
- Corrupt file → clear error and backup offered.
- Older save version migrates.
- Settings persist independently of save slots; bindings from settings override input defaults.
