# F-013 — Save Slots, World Flags and Settings

- Status: Draft
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

## Acceptance criteria
- Round trip of save objects and world flags.
- Simulated crash between write and rename leaves the previous save loadable.
- Corrupt file → clear error and backup offered.
- Older save version migrates.
- Settings persist independently of save slots; bindings from settings override input defaults.
