# Saves, Settings and World Flags

Package `ember.save` (`SaveStore`, `SaveData`, `SaveCodec`, `SlotMeta`, `SlotInfo`, `SlotState`, `SaveResult`, `LoadResult`, `SaveMigration`, `WorldFlags`, `FlagChanged`). Spec F-013.

## 1. Where saves live
```java
config.saveFolder = "my-game";   // desktop: ~/.ember2d/my-game ; Android: the app's private storage
config.saveSlots = 3;
config.saveVersion = 1;
```
Without `saveFolder`, everything is kept in memory (tests, jams).

Files on desktop, all readable JSON:
- `slot0.json`, `slot1.json`, …, each with a `.bak` backup;
- `settings.json`;
- `global.json`.

## 2. `SaveData`: the tree
Saves never use reflection. They are a tree of values: bool, int, float, string, nested `SaveData` and lists of `SaveData`.
```java
SaveData data = new SaveData()
        .set("area", "cellar").set("x", 5.5f).set("unlocked", 2).set("hardMode", true);
SaveData inv = new SaveData();
inventory.save(inv);                        // kits write themselves into a child
data.set("inventory", inv);
data.list("visited").add(new SaveData().set("id", "town"));

int unlocked = data.getInt("unlocked", 1);   // getters take a default
SaveData child = data.child("inventory");
String json = data.toJson();
SaveData back = SaveData.parse(json);
```
For your own types, write a `SaveCodec<T>` (`write(T, SaveData)` and `read(SaveData)`) and use `data.put("hero", hero, HERO_CODEC)` and `data.get("hero", HERO_CODEC, defaultHero)`.

## 3. Slots
```java
SaveStore saves = context.saves();
SaveResult r = saves.save(0, new SlotMeta("cellar", playSeconds, "The cellar"), data);   // scene, play time, summary
if (!r.ok()) LOG.error("Saving failed: " + r.error());

LoadResult loaded = saves.load(0);
if (!loaded.ok() && loaded.info() != null && loaded.info().backupAvailable()) {
    loaded = saves.loadBackup(0);             // the save before the last one
}
if (loaded.ok()) { SaveData d = loaded.data(); }
```
- **Safe writes:** a temporary file is written first, and the previous save becomes the backup. A crash mid-write never leaves you without a good save.
- **`SlotState`:** `EMPTY`, `OK`, `DAMAGED` (with a readable `problem`) or `TOO_NEW` (written by a newer game).
- **Slot list:** `saves.slots()` lists each slot's state, time, play time, scene and summary, for a load menu. `saves.delete(slot)` removes a slot.
- **Upgrades:** raise `config.saveVersion` and register a step per old version:
  ```java
  saves.addMigration(1, d -> { d.set("coins", d.getInt("gold", 0)); d.remove("gold"); });
  ```
- **Suspend save:** `saves.setSuspendSave(() -> save())` runs when the app is suspended, because Android may kill a backgrounded app. Clear it in `exit()` with `setSuspendSave(null)`.

### "Continue" in a menu (from the RPG and novel demos)
```java
static boolean hasSave(SaveStore saves) {
    if (saves.slots().size() <= SLOT) return false;
    SlotInfo info = saves.slots().get(SLOT);
    return info.state() == SlotState.OK || info.backupAvailable();
}
```

## 4. Global data
`saves.global()` holds data shared by all slots: best times, unlocked galleries, read text, endings seen. Write it with `saves.flush()`.
- The platformer keeps its best times there.
- The novel demo keeps read lines and unlocked CGs there (`NovelMemory`), and the endings it has seen.

## 5. Settings
`saves.settings()` is the settings tree in `settings.json`. The engine applies its own keys at boot and writes them with `context.saveSettings()`, and also on suspend and at shutdown:

| Key | Setting |
| --- | --- |
| `audio.master`, `audio.music`, `audio.sfx` | volumes |
| `input.haptics` | vibration |
| `input.touchLayout` | moved and resized on-screen controls |
| `input.bindings` | rebound keys |
| `ui.scale` | UI scale (read only; games write it) |
| `ui.language` | language ([Localisation](Localisation)) |

Your own settings go in the same tree under your keys. kit-novel uses `novel.textSpeed`, `novel.autoDelay` and `novel.skipUnread`.
```java
SaveData settings = context.saves().settings();
settings.set("game.difficulty", 2);
context.saves().flush();
```

## 6. World flags
`context.flags()` is the state of the world in the current play-through: `door.cellar = true`, `npc.elder.talked = 2`, `quest.stage = "found"`.
```java
WorldFlags flags = context.flags();
flags.set("door.cellar", true);
boolean open = flags.getBool("door.cellar", false);
int talks = flags.add("npc.elder.talked", 1);   // counters
flags.toggle("lever.3");
flags.remove("temp");
flags.clear();                                   // a new game (no events)
```
- **Values:** bool, int and String. Keys are flat strings; dots are only a convention.
- **Events:** setting a **different** value publishes `FlagChanged(key)`.
- **Per play-through:** save flags into the slot (`flags.save(data)`, `flags.load(data)`), not into global data. A slot without flags clears them.

The pattern for persistent doors and chests is on [Guide: RPG](Guide-RPG).

## 7. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| Settings lost after restart | no `saveFolder`, or `saveSettings()` never called | set both |
| Progress lost on Android | the app was killed in the background | save at checkpoints; `setSuspendSave` |
| A loaded game puts the player in a trigger | saved while standing in a travel zone | save on arrival at a marker |
| Flags survive "New game" | not cleared | `flags.clear()` when starting a new game |
| Old saves fail after a format change | no migration | raise `saveVersion`, add a migration |
