# 03 — Platformer kit, UI, audio, saves and releases

This chapter shows how to turn a playable level into a small complete game with what Phase 3 delivers: the platformer kit, audio, menus and HUD, on-screen controls, settings and saves, scene transitions, the Tiled type generator, and release builds for desktop and Android. Examples come from the platformer demo (`demos/demo-platformer`), which has three levels, a main menu, pause, a HUD, sound, settings and saved progress. Chapter 02 covers the basics this chapter builds on (input, Tiled maps, prefabs, collision, animation).

> Status: draft for the developer's review at the Phase 3 gate. Not approved yet.

## 1. What you get in Phase 3
- **Platformer kit (`kit-platformer`):**
  - Running, jumping, coyote time, jump buffering and one-way planks.
  - Patrolling enemies and moving platforms.
  - Optional air jumps, dash, wall slide and wall jump, ladders and platform momentum, each switched on per entity.
- **Audio:** sounds and music by asset ID, volume categories, music crossfades, limits on overlapping sounds, and pausing with the app.
- **UI:** a Scene2D layer per scene, a generated pixel skin, keyboard, gamepad and touch menus, HUD counters and a confirm dialog.
- **On-screen controls:** joystick or D-pad and buttons drawn with the skin, hidden in menus and when a keyboard is used, a layout editor, vibration, and safe areas for notches.
- **Settings and saves:**
  - Settings the engine applies itself.
  - Save slots with safe writes and backups, version upgrades, and global data shared by all slots.
- **Scenes:** fades between scenes, a loading screen, and preloading the next scene's assets.
- **Tiled:** a generator that writes your components and prefabs into the Tiled project, so Tiled offers the right fields.
- **Releases:** desktop zips with their own Java (no Java install needed), and a minified Android bundle (AAB) for Google Play.

## 2. The platformer kit
### Setting it up
Register the kit's components in your `ComponentSetup`, next to your own:
```java
public final class DemoComponents implements ComponentSetup {
    public void register(ComponentRegistry registry) {
        PlatformerKit.register(registry);   // PlatformerBody, Patroller, MovingPlatform, Ladder
        registry.register(Enemy.class, Enemy::new).register(Goal.class, Goal::new);
    }
}
```
Then add the systems in the scene's `enter`, in this order:
```java
Collision collision = new Collision(world, context.events());
collision.addTileGrid(map.collision(), 0);
world.addSystem(new MovingPlatformSystem(world, collision));   // solids move first, so riders see where they are
world.addSystem(new PatrollerSystem(world, collision));
world.addSystem(new PlatformerSystem(world, collision, input, context.events()));
world.addSystem(new CollisionSystem(world, collision));        // after all movement: triggers
```
`PlatformerSystem` reads these input names:
- the `move` axis (left and right; down with jump drops through a one-way plank; up and down climb ladders)
- the `jump` action
- the `dash` action

### The player prefab
A player is an `Actor` with a `PlatformerBody`. Every field has a sensible default, so `{}` is enough:
```json
{ "version": 1, "components": {
    "Sprite":   { "regionId": "tex.frog_jump", "origin": [16, 0], "z": 2 },
    "Collider": { "box": { "x": -0.4375, "y": 0, "width": 0.875, "height": 1.375 } },
    "Actor": {},
    "PlatformerBody": {},
    "Animator": { "clipSet": "anim.frog", "clip": "idle" },
    "AnimationStateMachine": { "definition": "asm.frog" } } }
```
Useful `PlatformerBody` fields (units are tiles and seconds; "ticks" are fixed steps of 1/60 s):

| Field | Default | What it does |
| --- | --- | --- |
| `maxSpeed`, `groundAccel`, `airAccel` | 8, 60, 35 | running |
| `jumpSpeed`, `gravity`, `fallMultiplier` | 16, 40, 1.6 | jump height (about 3.2 tiles) and a faster fall |
| `jumpCut` | 0.45 | how much releasing jump early cuts the jump |
| `coyoteTicks`, `bufferTicks` | 6, 6 | late and early jump presses still count |
| `airJumps` | 0 | extra jumps in the air |
| `dash`, `dashSpeed`, `airDashes` | false, 20, 1 | a dash on the `dash` action |
| `wallSlide`, `wallJump` | false, false | slide down walls and jump off them |
| `ladders` | false | climb `Ladder` areas |
| `inheritPlatformVelocity` | false | keep a moving platform's speed when jumping off |

The system publishes `Jumped` (with a `JumpKind`), `Landed` and `Dashed`. The demo plays sounds on them:
```java
context.events().on(Jumped.class, e -> audio.playVaried("sfx.jump", 0.06f));
```
It also writes the animation state machine parameters that the definition declares: `grounded`, `speedX`, `velY`, `wallSliding`, `dashing` and `climbing`, and the triggers `airJump` and `wallJump`.

### Switching mechanics on per level
Each demo level switches on its mechanics from the map, not from code. In Tiled, select the player object and edit its `PlatformerBody` class property, which Tiled offers once the type generator has run (section 8). Level 2 turns on `wallSlide` and `wallJump`. Level 3 sets `airJumps` to 1 and `dash` to true. The map saves it like this:
```xml
<object id="1" name="player" type="player" x="72" y="224">
  <properties>
    <property name="PlatformerBody" type="class" propertytype="PlatformerBody">
      <properties>
        <property name="airJumps" type="int" value="1"/>
        <property name="dash" type="bool" value="true"/>
      </properties>
    </property>
  </properties>
  <point/>
</object>
```
The log confirms what the map set: `Player mechanics (from the map): air jumps 1, dash true, wall jump false`.

### Enemies and platforms
- **`Patroller`:** walks between `minX` and `maxX`, or `range` tiles around its start, at `speed`. It falls with `gravity`, can turn at ledges (`turnAtLedges`), and flips its sprite (`artFacesLeft` when the art faces left).
- **`MovingPlatform`:** moves `distance` tiles at `speed` and back, horizontally or `vertical`. Riders move with it.

## 3. Audio
List sounds and music in the manifest. They load with their group like any asset:
```json
{"id": "sfx.jump",    "path": "sfx/jump.wav",    "type": "sound", "group": "global", "maxInstances": 2},
{"id": "music.level", "path": "music/level.wav", "type": "music", "group": "global"}
```
Play them through `context.audio()`:
```java
Audio audio = context.audio();
audio.play("sfx.stomp");
audio.playVaried("sfx.jump", 0.06f);          // a little random pitch, so repeats don't sound identical
audio.playAt("sfx.hit", x, y);                // panned by position; skipped when far outside the view
audio.playMusic("music.level", 0.5f);         // crossfades from the current track over 0.5 s
audio.stopMusic(1f);
audio.setVolume(AudioCategory.MUSIC, 0.4f);   // MASTER, MUSIC, SFX
```
- **Overlapping sounds:** `maxInstances` (or `audio.setLimit`) caps how many copies of one sound play at once; a new one stops the oldest.
- **Pausing:** pause and resume the music yourself for a pause menu (`pauseMusic` and `resumeMusic`). When the app is suspended, the engine pauses all audio and resumes it afterwards.
- **Fades:** they run on real time, so they work while the game is paused.
- **Volumes:** saved with the settings (section 6), so they need no code of their own.

## 4. Menus and HUD
### The scene's UI
Each scene has its own Scene2D layer, created on first use with `ui()` and disposed when the scene exits. It draws above the world and gets pointer and key events before the game does:
```java
Ui ui = ui();
ui.setSkin(UiSkins.pixel(context.assets().get("font.pixel", BitmapFont.class)), true);   // disposed with the UI
Table hud = new Table(ui.skin());
HudCounter time = new HudCounter(ui.skin(), "Time", null);
hud.add(time).left();
ui.root().add(hud).expand().top().left().pad(4f);
// every frame:
time.setSeconds(level.levelTime());
```
- **Size:** in `UiMode.VIRTUAL` (the default) the UI uses the game's virtual resolution (320 x 180 in the demo) and the world's pixel scale. `UiMode.SCREEN` uses the window size divided by `uiScale` (0 means from the screen density).
- **Safe areas:** `ui.safeInsets()` keeps widgets clear of notches.
- **Touch sizes:** `ui.dp(40)` gives a finger-sized length in UI units.

### Menus
A `MenuList` is a column of buttons that works with the keyboard, a gamepad, touch and the Android back button:
```java
input.pushContext("ui");   // menus read ui_nav, ui_confirm and ui_back
MenuList menu = new MenuList(ui);
menu.add("Resume", this::closePause);
menu.addValue("Music", () -> percent(audio.volume(AudioCategory.MUSIC)),
        v -> audio.setVolume(AudioCategory.MUSIC, v / 100f), 0, 100, 10);   // left and right change the value
menu.add("Main menu", () -> ConfirmDialog.show(ui, "Quit to the main menu?", "Yes", "No",
        () -> context().scenes().replace(new MenuScene(), Transition.fade(0.5f))));
menu.onBack(this::closePause);
menu.setDisabled(2, true);   // greyed out and skipped, like the locked levels in the demo's level list
```
For a pause menu:
- push the `ui` input context;
- pause time (`context.time().setPaused(true)`) and the music;
- open an overlay table.

Closing undoes all three. The demo's `DemoScene.openPause` and `closePause` show the whole pattern.

## 5. On-screen controls
Phones get on-screen controls from the `touchControls` block of the bindings file:
```json
"touchControls": {
  "opacity": 0.35,
  "joystick": { "axis": "move", "mode": "floating", "radius": 56, "deadZone": 0.2 },
  "buttons": [
    { "action": "jump",  "label": "A",  "x": 80,  "y": 80,  "radius": 44 },
    { "action": "dash",  "label": "B",  "x": 176, "y": 64,  "radius": 34 },
    { "action": "pause", "label": "II", "x": 48,  "y": -48, "radius": 24 } ] }
```
- **Units and corners:** positions and sizes are in dp. The joystick and D-pad are placed from the bottom left corner, and buttons from the bottom right corner (the thumbs' corners). A negative `y` on a button counts from the top edge, like the demo's pause button.
- **D-pad:** `"dpad": { "axis": "move", "x": 96, "y": 96, "radius": 56, "diagonals": true }` replaces the joystick.
- **When they show:** `EngineConfig.touchControls` is `AUTO` by default: shown on phones, and hidden while a keyboard or gamepad is used. `ALWAYS` and `NEVER` force them. On desktop, `--touch-controls` shows them for testing, and `--safe-insets=L,T,R,B` (dev mode) simulates a notch.
- **Hidden in menus:** controls show only while their actions' input context is on top.
- **Hiding a button:** a game can hide one at run time. The demo shows B only when dash is on:
  ```java
  input.setTouchButtonVisible("dash", body.dash);
  ```
- **Vibration:** `input.vibrate(40)` buzzes for 40 ms on phones when vibration is on (`setHapticsEnabled`).
- **Layout editor:** `TouchLayoutEditor.open(ui, input, onDone)` lets the player drag controls, resize them, change the opacity and reset them. Save the result with `context.saveSettings()` in `onDone`.
- **Custom look:** a skin named by `EngineConfig.touchControlsSkin` can supply `touch-base`, `touch-knob`, `touch-button`, `touch-button-down`, `touch-dpad` and `touch-dpad-down`.

## 6. Settings and saves
### Where saves live
Set a folder name and the engine keeps saves on disk:
```java
config.saveFolder = "demo-platformer";   // desktop: ~/.ember2d/demo-platformer; Android: the app's private storage
config.saveSlots = 3;
config.saveVersion = 1;
```
Without `saveFolder`, saves live in memory only, which suits tests and jams.

### Settings
The engine applies its own settings at boot, before the first scene:
- volumes (`audio.master`, `audio.music`, `audio.sfx`)
- vibration (`input.haptics`)
- the controls layout (`input.touchLayout`)
- rebound keys (`input.bindings`)
- `ui.scale`

To store them, call `context.saveSettings()`. The demo does this when the player leaves its settings menu. The engine also saves them on suspend and at shutdown. Your own settings go in the same tree under other keys:
```java
SaveData settings = context.saves().settings();
settings.set("game.difficulty", 2);
context.saves().flush();
```
Keys can be rebound from code: `input.rebindAction("jump", List.of("W", "UP"))`, and `resetBinding("jump")` undoes it. The rebinding survives reloads of the bindings file and is saved with the settings.

### Save slots
A slot holds a tree of values (`SaveData`) plus metadata for a load screen:
```java
SaveData data = new SaveData().set("unlocked", 2).set("playSeconds", 75.5f);
SaveResult r = context.saves().save(0, new SlotMeta("level2", 75.5f, "Level 2 of 3"), data);

LoadResult loaded = context.saves().load(0);
if (loaded.ok()) {
    int unlocked = loaded.data().getInt("unlocked", 1);   // getters take a default
} else if (loaded.info().backupAvailable()) {
    loaded = context.saves().loadBackup(0);               // the previous save
}
```
- **Safe writes:** a write goes to a temporary file first, and the previous save becomes the backup. A crash in the middle never leaves a broken slot without a backup.
- **Slot states:** `EMPTY`, `OK`, `DAMAGED` (with a readable `problem`) or `TOO_NEW` (written by a newer game version).
- **Slot list:** `saves().slots()` lists every slot's state, time, play time, scene and summary for a load menu.
- **Upgrades:** when you change the save format, raise `config.saveVersion` and register an upgrade for each old version:
  ```java
  context.saves().addMigration(1, d -> { d.set("coins", d.getInt("gold", 0)); d.remove("gold"); });
  ```
- **Your own types:** a `SaveCodec<T>` (`write(T, SaveData)` and `read(SaveData)`) stores them with `data.put("hero", hero, HERO_CODEC)`. Saves never use reflection, so they keep working in a minified Android build.
- **Global data:** `saves().global()` holds data shared by all slots, such as best times or unlocked extras. Write it with `flush()`. The demo keeps `best.level1` to `best.level3` there.
- **Saving on suspend:** `saves().setSuspendSave(() -> ...)` runs when the app is suspended, because Android may close a suspended app without warning.

The demo's `Progress` class shows a whole slot in about 60 lines:
- it loads slot 0, falling back to the backup and then to a fresh start;
- it unlocks the next level when one is finished;
- it saves with a summary for the slot list.

## 7. Scene transitions and preloading
```java
context.scenes().replace(new DemoScene(2), Transition.fade(0.5f));    // black; fade(seconds, color) for others
context.scenes().push(new ShopScene(), Transition.fade(0.3f));        // the level stays below the shop
context.scenes().pop(Transition.cut());                               // no fade, but still no blocking load
```
- **The fade:** the cover fades in, and the old scene stays on screen, frozen. At full cover the scenes switch, and the new one runs while the cover fades out.
- **Loading during the fade:** the new scene's asset group loads in the background during the fade. If it isn't ready at full cover, a loading bar shows (after 0.1 s, so short loads don't flash). Replace it with `config.loadingScreen`, or set it to `null` for a plain colour.
- **During a transition:** taps and key presses do not reach the old scene's menus. Changes you ask for meanwhile wait until the transition ends.
- **Preloading:** `context.scenes().preload("level2")` starts loading a group early. The demo's menu preloads the newest unlocked level while the player reads it. The scene that uses the group takes it over; `cancelPreload` releases one you no longer need.
- **The plain methods:** `push`, `pop` and `replace` without a transition still switch at the next frame and load blocking.

## 8. Tiled types from your prefabs
Run the generator after changing prefabs or components:
```
./gradlew :demos:demo-platformer:core:tiledTypes
```
It writes `assets/maps/platformer.tiled-project`:
- **One class per component:** with its fields and defaults, plus enum types for enum fields.
- **One class per prefab:** holding the prefab's values.

Open the maps through that project in Tiled. When you give an object the class `enemy/pig`, Tiled lists its components and fields with the prefab's values. Fields you edit are saved as class-typed properties, which the spawner applies.

Wiring and checks:
- **Runtime:** set `config.tiledProject = "maps/platformer.tiled-project"` so the game reads class-typed properties.
- **A stale file:** the demo's `TiledProjectTest` fails when the committed project is out of date.
- **What is left out:** `Transform`, sprite flips, fields set by object references, and fields that start as null. libGDX copies every class member into each object, so these would overwrite values that come from the object itself. Set them with plain properties such as `Sprite.regionId`.
- **Which property wins:** a plain property (`Patroller.speed` or `speed`) always wins over a class-typed one.

## 9. Releases
### Desktop
```
./gradlew :demos:demo-platformer:packageWinX64     # also packageLinuxX64, packageMacX64, packageMacM1
```
- **The zip:** `demos/demo-platformer/desktop/build/construo/dist/platformer-demo-winX64.zip` holds the game, a trimmed Java runtime and `platformer-demo.exe`. Players need no Java.
- **Release mode:** packaged builds run in release mode: no dev tools, no file watching, and a crash log in the per-user app folder (`%APPDATA%\<game>` on Windows).
- **Version:** the window title and the main menu show the Gradle version.

### Android
```
./gradlew :demos:demo-platformer:android:assembleDebug     # APK for devices and emulators
./gradlew :demos:demo-platformer:android:bundleRelease     # AAB for Google Play, minified with R8
```
- **Version:** the version code comes from the Gradle version (`1.2.3` gives `10203`), and the version name is the version itself.
- **Minifying:** R8 keeps everything prefabs and maps need, because the keep rules are generated from your `ComponentSetup` on every build.
- **Crash traces:** keep `build/outputs/mapping/release/mapping.txt` for each uploaded bundle. Google Play uses it to make crash traces readable.
- **Signing for Google Play:** create an upload key once and keep it out of the repository:
  ```
  keytool -genkeypair -v -keystore ember-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
  ```
  Then add these to `~/.gradle/gradle.properties`:
  ```
  ember.signing.storeFile=C:/keys/ember-upload.jks
  ember.signing.storePassword=...
  ember.signing.keyAlias=upload
  ember.signing.keyPassword=...
  ```
  Without these properties, the release build is signed with the debug key and warns `no upload key`. Such builds are fine for testing but cannot be uploaded.
- **CI:** builds the bundle on pushes to `main` and attaches it with its mapping file as `platformer-demo-android-release`.

## 10. The demo, step by step
- **Menus:** Play opens the level list. Unlocked levels show their best time, and locked levels are greyed out. Settings has volumes, Vibration and Edit controls (on touch screens), and Back.
- **Levels:**
  - Level 1, Meadow: running, jumping, planks, pigs, spikes and a moving platform.
  - Level 2, Cliffs: a cliff that needs wall jumps, and a lift (a vertical moving platform).
  - Level 3, Sky: spike pits wider than a normal jump, crossed with the air jump or a dash.
- **Finishing a level:** the goal shows the time, deaths and best time, saves the progress and offers "Next level". After level 3 the title is "You finished the demo!".
- **New levels:** `assets-src/demo-platformer/maps/make_levels.py` started levels 2 and 3 from simple layouts. Edit them in Tiled from here on.
- **Level tests:**
  - `LevelMapTest` checks every level: one player, one goal, a checkpoint, and objects standing on ground.
  - `LevelReachabilityTest` walks from the player to the goal with a cautious jump model, so a level that can't be finished fails the build. It also checks that levels 2 and 3 can't be finished without their new mechanic.

## 11. Common mistakes
- **A mechanic does nothing:**
  - It is off by default. Check the "Player mechanics" log line, or the `PlatformerBody` in the dev tools inspector.
  - Dash also needs a `dash` action in the bindings.
- **"There is no class with name 'X' in given Tiled project file" when a map loads:** the project file is older than the map's class properties. Run `tiledTypes` again.
- **Tiled shows no fields for a class:** the maps were opened without the project file, or the generator hasn't run since the prefab changed.
- **A menu doesn't react to the keyboard:** push the `ui` input context, and pop it when the menu closes. Scenes exiting with menus open should pop back to `gameplay`, as the demo's `exit` does.
- **Settings are lost after a restart:**
  - `saveFolder` is not set, so saves are in memory only.
  - `saveSettings()` was never called after a change.
- **Progress is lost on Android:** Android may close a suspended app. Save when it matters (level end), and use `setSuspendSave` for anything else.
- **A loading bar flashes between scenes:** expected for slow groups. Preload the next scene's group from the menu or the previous level.
- **The release bundle is rejected by Google Play:** it was signed with the debug key; set the `ember.signing.*` properties.
- **A component works in debug but not in the release build:** it isn't registered in the `ComponentSetup`, so no keep rule was generated for it.

## 12. Where to look when something breaks
- **Logs:**
  - The `demo` tag reports maps, spawned entities and the player's mechanics.
  - `scene` reports scenes entering and exiting, `assets` reports group loads and unloads, and `save` reports where saves live and any skipped bad setting.
  - On Android, read them with `adb logcat`.
- **Save files:** in `~/.ember2d/<folder>` on desktop: `slotN.json`, `.bak` backups, `settings.json` and `global.json`. They are readable JSON.
- **Dev tools (F1, desktop dev builds):** the inspector shows and edits `PlatformerBody`, `Patroller` and other components live. F3 shows colliders.
- **Tests:**
  - `LevelReachabilityTest` names the column where a level gets stuck.
  - `TiledProjectTest` names the task to run.
  - The engine's `SceneTransitionTest` and `FileSaveStoreTest` show the exact behaviour of transitions and saves.
