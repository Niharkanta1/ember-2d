# 05 — RPG and visual novel

This chapter shows how to build story-driven games with what Phase 5 delivers:
- interaction and inventories for top-down RPGs;
- world flags that remember what the player did;
- Ink stories through the dialogue runner and the dialogue box;
- `kit-novel` for staged scenes, either as a full visual novel or as a cutscene inside another genre;
- string tables for localisation.

Examples come from two demos:
- **The RPG demo** (`demos/demo-rpg`): a town and a cellar, an elder who gives a key, a locked door, a chest, and a cutscene.
- **The novel demo** (`demos/demo-novel`): "Kero and the Lost Cherries", one chapter with two endings.

Chapters 02 to 04 cover maps, prefabs, UI, saves and the top-down kit.

> Status: draft for the developer's review at the Phase 5 gate. Not approved yet.

## 1. What you get in Phase 5
- **`kit-topdown`, package `rpg`:**
  - `Interactable`, `Interactor` and `InteractionSystem`: talk, open and search, with a prompt for the focused object.
  - `ItemCatalog` (JSON items), `Inventory` (stacks, capacity, save and load) and `ItemPickup`.
- **`kit-common`:** `DialogueRunner`, which runs Ink stories (compiled by inklecate, run by blade-ink) behind a small API: lines, speakers, tags, choices, knots, variables, game functions and saving.
- **`kit-novel`:**
  - `NovelDirector` runs a story as a staged scene from Ink tags.
  - `NovelView` presents it: backgrounds, characters, CGs, a dialogue box, choices, and Auto, Skip, Log and Hide.
  - `NovelMemory` remembers read lines and unlocked CGs; `NovelSettings` holds the reader settings.
- **Engine additions:**
  - `ember.ui.DialogueBox`: name plate, typewriter text, choices.
  - `ember.save.WorldFlags` (`context.flags()`) with `FlagChanged` events.
  - `ember.i18n.Strings` (`context.strings()`): string tables per language.

## 2. Interaction
Register the RPG components with `RpgKit.register(registry)` (next to `CommonKit` and `TopDownKit`). The player gets an `Interactor`; anything it can use gets an `Interactable`:
```json
"Interactable": { "prompt": "Talk", "action": "elder", "range": 1.5 }
```
- **Choosing the target:** `InteractionSystem(world, input, events)` picks, for each interactor, the nearest enabled interactable within `range` and inside a 120° cone around the body's facing. Anything closer than 0.3 counts in every direction.
- **The prompt:** when the target changes it publishes `InteractionFocus(interactor, target, prompt)`. The demo shows "E: Talk" (desktop) or "A: Talk" (touch) from it.
- **Using it:** pressing the interactor's `inputAction` (`interact`) publishes `Interacted(interactor, target, action)`. For a touch button or a cutscene, call `interaction.interact(player)`.
- **Busy players:** while the player's `TopDownBody.locked` is true (a conversation, a cutscene), nothing is focused and presses are ignored.

`action` is yours to define. In the RPG demo it is the name of an Ink knot, so one handler serves every NPC, door and chest:
```java
context.events().on(Interacted.class, e -> converse(e.action()));
```

## 3. Items and inventory
Items are one JSON asset:
```json
{ "version": 1, "items": {
    "key.cellar": { "name": "Cellar key", "icon": "atlas.rpg/key", "tags": ["key"] },
    "tonic": { "name": "Tonic", "icon": "atlas.rpg/tonic", "stack": 9, "tags": ["consumable"] } } }
```
```java
ItemCatalog items = ItemCatalog.parse(assets.get("items", JsonValue.class), assets.path("items"));
int added = inventory.add(items, "tonic", 2);   // how many fitted
boolean taken = inventory.remove("tonic", 1);   // all or nothing
```
- **Filling slots:** `add` fills existing stacks first, then empty slots, up to `capacity` (12). `remove` takes all or nothing.
- **Queries:** `count`, `has`, `hasTag`, and the slots `item(i)` / `count(i)`.
- **Saving:** `save(SaveData)` and `load(SaveData, catalog)`. Items the catalog no longer lists are skipped, and stacks are clamped.
- **Pickups:** an entity with `ItemPickup` and a `TriggerZone` is collected by `ItemPickupSystem`, which publishes `ItemPickedUp`.
- **In the demo:** the inventory lives in the play-through object and is added to each area's player entity, so the same object travels between scenes.

## 4. World flags
`context.flags()` is the state of the world in the current play-through: `door.cellar = true`, `npc.elder.talked = 2`.
- **Reading:** `getBool`, `getInt` and `getString`, each with a default.
- **Changing:** `set`, `toggle` and `add`.
- **Events:** changing a value publishes `FlagChanged(key)`.
- **Saving:** flags belong to a save slot: `flags.save(data)` and `flags.load(data)`. `clear()` starts a new game.

The pattern for things that stay changed, from `AreaScene`:
```java
context.events().on(FlagChanged.class, e -> showFlags(true));   // a story set a flag: open now, with the animation
showFlags(false);                                               // entering an area: show them as the flags say
```
`showFlags` opens every `Door` whose flag is set: it plays `door_open` (or `door_opened` without animation), removes the door's `Solid` and disables its `Interactable`. It dulls every opened `Chest`. Because the scene reads the flags on entry, doors and chests stay open after travelling and after loading.

## 5. Ink dialogue
### Writing and compiling
Write stories in Ink (Inky is a good editor) and compile them with inklecate. The demos have a Gradle task for it. Put the compiler's path in `~/.gradle/gradle.properties`:
```
ember.inklecate=C:/tools/inklecate/inklecate.exe
```
Then run:
```
./gradlew :demos:demo-rpg:core:compileInk
```
- **Inputs and outputs:** it compiles `assets-src/demo-rpg/ink/*.ink` into `assets/ink/*.ink.json`.
- **Committed output:** the compiled files are committed, so a normal build needs no inklecate.
- **Manifest:** list each compiled story as a `json` asset.

### The runner
```java
DialogueRunner story = DialogueRunner.fromAsset(assets, "ink.town");
story.bind("give", args -> inventory.add(items, (String) args[0], ((Number) args[1]).intValue()) > 0);
story.jumpTo("elder");
while (story.canContinue()) {
    story.next();
    show(story.speaker(), story.text());   // "Grik: Hello" -> speaker "Grik", text "Hello"
}
List<String> choices = story.choices();     // then story.choose(i) and continue
```
- **Speakers:** a line written `Name: text` gives the speaker and the text. A `#speaker:id` tag overrides the speaker.
- **Tags:** `tags()` lists the line's tags as written; `tag("mood")` reads one value.
- **Game functions:** declare them in Ink as `EXTERNAL give(item, count)` and give each one an Ink fallback, so the story also runs in Inky. Bound functions replace the fallbacks.
- **Functions run once:** blade-ink can look ahead past a line; `bind` makes sure game code with side effects still runs exactly once per call.
- **Variables:** `variable(name)` and `setVariable(name, value)`.
- **Saving:** `save(SaveData)` and `load(SaveData)` keep the position, the variables and the visit counts.

The demo's town story binds `has`, `give`, `take`, `flag` and `set_flag`. Its knots then decide everything themselves:
```ink
=== cellar_door ===
{
- has("key.cellar"):
    ~ set_flag("door.cellar")
    The key turns. The old door creaks open.
- else:
    The door is locked. Someone in town must have the key.
}
-> END
```

### The dialogue box
`DialogueBox` (in `ember.ui`) shows a line with a name plate and typewriter text.
- **Pressing:** a press (`ui_confirm`, a click or a tap) first completes the line, then calls the advance handler.
- **Choices:** `showChoices(options, index -> ...)` puts them in the box.
- **Ignored presses:** a press in the frame a line appears is ignored, so the press that started a conversation does not skip its first line. While another menu has the keyboard (a pause menu), the box ignores its confirm action.

`Conversation` in the RPG demo is a complete example in 90 lines. It pushes the `ui` input context, plays a knot line by line, shows the choices, and calls back when the knot ends.

### Saving a play-through
The RPG demo's slot 0 holds everything that makes a play-through (`Game.write()`):
```java
data.set("area", area).set("x", x).set("y", y);
inventory.save(inv);  data.set("inventory", inv);
flags.save(f);        data.set("flags", f);
story.save(s);        data.set("story", s);
```
- **When it saves:** on arriving in an area, from the pause menu, and when Android sends the app to the background (`saves.setSuspendSave`).
- **Avoid saving inside a travel zone:** the demo saves on arrival at a marker, never inside the zone. Otherwise loading would travel straight back.

## 6. Visual novels with kit-novel
### The cast
```json
{ "version": 1, "characters": {
    "kero": { "name": "Kero", "color": "#8BE38B", "position": "left", "default": "normal",
              "expressions": { "normal": "atlas.novel/kero_normal", "surprised": "atlas.novel/kero_surprised",
                               "happy": "atlas.novel/kero_happy" } } } }
```
- **Positions:** `left`, `center` and `right`, plus named anchors under `positions`.
- **Speakers:** a line's speaker is matched to a character by ID or name, so `Kero: Hello` gets Kero's name plate colour.

### Staging tags
Tags on their own line run before the next line:
```ink
# bg: bg.meadow [transition=fade 1.0]
# music: music.meadow fade=1.5
# show: kero left
Kero: What a fine day for a walk!
# show: pip surprised right transition=slide
# sfx: sfx.pop
Pip: Kero! Kero! My cherries are gone!
```
| Tag | Effect |
| --- | --- |
| `bg: id [transition=fade 1.0]` | Background (a texture or atlas region) |
| `show: id [expression] [position] [transition=fade/slide/cut]` | Show or move a character |
| `expr: id expression` / `hide: id` | Change expression / hide |
| `music: id [fade=2]` / `stopmusic [fade=2]` | Music |
| `sfx: id` / `voice: id` | Sound, voice line |
| `cg: id` / `hidecg` | Full-screen picture, unlocked in the gallery |
| `wait: 0.5` / `shake: 0.4` / `flash: white 0.2` | Timing and effects |

- **Unknown staging tags:** in dev builds they fail with the story and the line named; in release builds they are logged and skipped.
- **Game tags:** plain tags without a colon (`#ending_together`) pass through to the game. So do dialogue tags such as `speaker:` and `mood:`.

### Directing and presenting
```java
NovelView view = new NovelView(ui, assets, audio, input, renderer.camera(), new NovelSettings().load(saves.settings()));
NovelDirector director = new NovelDirector(DialogueRunner.fromAsset(assets, "ink.chapter1"), cast, view,
        new NovelMemory(saves.global()), devMode);
view.setOnLine(line -> /* line.tags(), line.speakerId() */);
view.play(director, () -> /* the end */);
// every frame: view.update(delta)
```
- **`NovelDirector`:**
  - It runs the tags, keeps the `StageState` (background, music, characters, CG) and skips lines that only stage.
  - It hands out lines and choices, and keeps the last 100 lines in the backlog.
- **`NovelView`** draws on the scene's UI:
  - The background, the characters, the CG and the `DialogueBox`.
  - Choices as a menu.
  - **Auto** advances after the text plus `autoDelay`.
  - **Skip** fast-forwards and stops at any unread line, unless `skipUnread` is on.
  - **Log** shows the backlog with voice replay. **Hide** hides the UI until the next tap.
- **`NovelMemory`:** read lines and unlocked CGs live in the global save data, so Skip passes text read in any earlier play-through, and the gallery (`unlockedCgs()`) fills up over play-throughs.
- **`NovelSettings`:** `textSpeed`, `autoDelay` and `skipUnread`, kept in the settings under `novel.*`. The novel demo's Settings screen edits them.

### Save anywhere
```java
director.save(data);   // story position, stage, the line on screen, the backlog
...
director.load(data);   // redraws the stage at once
view.play(director, onEnd);   // shows the saved line again, then continues
```
The novel demo saves from its Menu. After killing the app and choosing Continue, the background, the characters with their expressions and the line on screen are exactly as they were.

### Story scenes inside other genres
The RPG cutscene runs the same view over the game world:
- **No background:** without a `bg:` tag the world stays visible.
- **Smaller characters:** `view.setCharacterArea(0.33f, 0.5f)` stands them on the dialogue box at half the screen height.
- **Before:** the scene locks the player and pushes the `ui` input context.
- **Afterwards:** it sets a flag (`cutscene.cellar`), so the cutscene never plays again.

## 7. Localisation
- **String tables:** one JSON asset per language, `strings.en`, `strings.de`:
  ```json
  { "version": 1, "language": "de", "strings": { "menu.play": "Spielen", "hud.score": "Punkte {0}" } }
  ```
- **Lookups:**
  - `context.strings().get("menu.play")` returns the current language's text, falls back to the default language (`EngineConfig.defaultLanguage`), and gives `[menu.play]` for a missing key.
  - `format("hud.score", 120)` fills the placeholders.
- **Switching language:** `setLanguage("de")` switches and publishes `LanguageChanged`. The choice is kept in the settings (`ui.language`).
- **Localised assets:** `strings.localised("ink.town")` gives `ink.town.de` when that asset exists. A translated Ink story keeps the knots, choices and functions and only changes the text, so a state saved in one language loads in the other.
- **The demos are in English only:** they do not use string tables yet. Localising one is a follow-up.

## 8. Running the demos
- **RPG demo, desktop:**
  ```
  ./gradlew :demos:demo-rpg:desktop:run
  ```
  WASD or the arrows move, E, Space or Enter interact and advance, P or Escape pauses.
- **RPG demo, Android:**
  ```
  ./gradlew :demos:demo-rpg:android:installDebug
  ```
  The left stick moves, A interacts, and you tap the box to advance.
- **Novel demo, desktop:**
  ```
  ./gradlew :demos:demo-novel:desktop:run
  ```
  Click, Space or Enter advance; Escape or the Menu button opens the menu.
- **Novel demo, Android:**
  ```
  ./gradlew :demos:demo-novel:android:installDebug
  ```
  Tap to advance; use the buttons at the top.
- **Making the data again:**
  - `assets-src/demo-rpg/make_assets.py` and `assets-src/demo-novel/make_assets.py`. The novel's script also composes its backgrounds from the art pack.
  - The `sound/make_sounds.py` scripts make the sounds.
  - `compileInk` compiles the stories.

## 9. Common mistakes
- **A game function runs twice:** this happens with a raw blade-ink story bound without `lookaheadSafe=false`. Use `DialogueRunner.bind`, which handles it.
- **"Story … unknown staging tag "ending:"":** a game tag with a colon is read as a staging command. Use a plain tag (`#ending_together`).
- **A story with an `EXTERNAL` function will not run in a test:** bind the function, or give it an Ink fallback.
- **A widget draws as a black box:** it was created before the scene set its skin. Call `useSkin` first.
- **The first line of a conversation is skipped:** the box ignores the starting press by itself. If you build your own box, ignore presses in the frame a line appears.
- **Pressing confirm in the pause menu also advances the story:** use `DialogueBox`, which yields to menus, or stop your own reader while a menu is open.
- **A loaded game travels straight back:** the save was written inside a travel zone. Save on arrival, at a marker.
- **Pixel Adventure "Hit" frames show a white shape:** the first two frames of those sheets are white flashes; pick a later frame.
- **A `ConfirmDialog` message runs off the screen:** its label does not wrap. Keep messages short.

## 10. Where to look when something breaks
- **Logs:**
  - `rpg`: areas built, saves.
  - `novel` (the director's tag problems in release builds) and `novel-demo` (endings, saves).
  - Story problems show up as exceptions naming the story file and the line.
  - On Android: `adb logcat | grep -E "rpg|novel|scene"`.
- **F3 / dev overlay:** colliders and trigger zones (travel zones are the yellow boxes), and entity counts.
- **Tests:**
  - `InteractionTest` and `InventoryTest`: focus rules and stacking.
  - `DialogueRunnerTest`: the runner, including the run-once rule for functions.
  - `NovelDirectorTest`: every staging tag, saving mid-scene and the backlog.
  - `RpgDataTest` plays the whole quest through the story functions and saves it.
  - `ChapterTest` reads both endings and checks every asset the story names.
