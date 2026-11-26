# 02 — Levels, prefabs, collision and animation

This chapter shows how to build a playable level on what Phase 2 delivers: input, Tiled maps, components from data, collision, animation, text, hot reload and the in-game dev tools. Examples come from level 1 of the platformer demo (`demos/demo-platformer`). Chapter 01 covers the basics this chapter builds on (scenes, ECS, time, events, assets, sprites).

> Status: draft for developer review at the Phase 2 gate.

## 1. What you get in Phase 2
- **Input:** named actions and axes from a JSON file, contexts for menus, touch gestures and on-screen controls (joystick and buttons).
- **Levels:** Tiled maps loaded as assets, drawn in render layers with parallax and animated tiles, with collision from tile properties.
- **Components from data:** a component registry, JSON prefabs, and entities placed as Tiled objects (property overrides, templates, object references).
- **Collision:** kinematic actors and solids, tile grids, one-way platforms, moving platforms, triggers and spatial queries.
- **Animation:** clips from sprite sheets, atlases and Aseprite, frame events, and an animation state machine in JSON.
- **Text:** bitmap and TrueType (FreeType) fonts, and world text on entities.
- **Development:** hot reload of maps, JSON, images and fonts, and in-game dev tools (Dear ImGui) in desktop dev builds.

## 2. Input
### The bindings file
A game declares its input once, in a JSON file listed in the manifest (`"id": "input"`, type `json`, group `global`) and named in the config (`config.inputBindings = "input"`). The demo's file, shortened:
```json
{ "version": 1,
  "contexts": {
    "gameplay": {
      "actions": { "jump": ["SPACE", "Z", "UP", "W"], "pause": ["P", "DOUBLE_TAP"],
                   "menu": ["ESCAPE", "BACK", "LONG_PRESS"] },
      "axes": { "move": { "left": ["LEFT", "A"], "right": ["RIGHT", "D"], "up": ["UP", "W"], "down": ["DOWN", "S"] } } },
    "ui": {
      "actions": { "ui_back": ["ESCAPE", "BACK", "TAP"] },
      "axes": { "ui_nav": { "left": ["LEFT", "A"], "right": ["RIGHT", "D"], "up": ["UP", "W"], "down": ["DOWN", "S"] } } } } }
```
- **Bindings:** key names are libGDX's in upper case (`SPACE`, `L_SHIFT`, `F1`, `BACK`); mouse buttons are `MOUSE_LEFT` and so on; touch gestures are `TOUCH`, `TAP`, `DOUBLE_TAP`, `LONG_PRESS`, `DRAG` and `SWIPE_LEFT`/`RIGHT`/`UP`/`DOWN`. One action can mix keys and gestures.
- **Axes:** a 1D axis has `negative`/`positive`, a 2D axis `left`/`right`/`up`/`down`. Diagonals are normalised (`"normalize": false` opts out).
- **Names are global:** an action or axis name is unique across contexts.
- The file is checked at boot; every problem is listed with the file and path.

### Reading input
```java
Input input = ctx.input();
if (input.justPressed("jump")) { ... }
boolean holding = input.pressed("jump");
input.axis2("move", moveVector);            // x right, y up, length <= 1
float held = input.heldTime("jump");
```
- Inside a `FIXED` system, `justPressed`/`justReleased` are true in the **first fixed step after** the press, even if it comes a frame later, so a tap is never missed at high frame rates. In other phases they are true in the frame of the press.
- A press and release inside one frame still give `justPressed` and `justReleased`.

### Contexts
Contexts form a stack with `gameplay` at the bottom:
```java
input.pushContext("ui");     // a menu opens: its keys are consumed, gameplay sees them as up
input.popContext();
```
- Only actions of contexts on the stack read as pressed; a key bound in a higher context is hidden from lower ones.
- Keys held across a change count as up until pressed again, so the key that opens a menu does not also trigger the menu.
- Changing the stack publishes `InputContextChanged`.
- On suspend and on desktop focus loss every held input is released.

### Touch and on-screen controls
Gesture thresholds (in dp and seconds) can be tuned in an optional `touch` section. On-screen controls go in `touchControls`:
```json
"touchControls": {
  "opacity": 0.35,
  "joystick": { "axis": "move", "mode": "floating", "radius": 56, "deadZone": 0.2 },
  "buttons": [
    { "action": "jump",  "label": "A",  "x": 80, "y": 80,  "radius": 44 },
    { "action": "pause", "label": "II", "x": 48, "y": -48, "radius": 24 } ] }
```
- The **joystick** drives a 2D axis with an analog value; a `floating` stick appears where the thumb lands in the left half of the screen.
- **Buttons** press their action like a key. Positions are dp from the bottom-right corner; a negative `y` counts from the top.
- A touch that starts on a control belongs to it and makes no gestures; stick and buttons work together.
- Shown on phones by default (`EngineConfig.touchControls = AUTO`); `ALWAYS` shows them on desktop too, where the mouse is the finger (the demo: `--args="--touch-controls"`).

## 3. Levels in Tiled
### Workflow
1. Draw the level in [Tiled](https://www.mapeditor.org) 1.10 or newer (orthogonal, not infinite), with tilesets from the game's assets folder.
2. List the map in the manifest: `{ "id": "map.level1", "path": "maps/level1.tmx", "type": "map", "group": "global" }`. Tilesets and images load with it.
3. Load it in the scene, build collision, draw it and spawn its objects:
```java
TileMap map = TileMap.load(ctx.assets(), "map.level1", ctx.renderer().pixelsPerUnit());
Collision collision = new Collision(world(), ctx.events());
collision.addTileGrid(map.collision(), 0);
new MapSpawner(ctx.prefabs()).spawn(world(), map);                         // section 4
new TileMapRenderer(map, ctx.renderer(), ctx.time()).attachTo(sprites);     // sprites = the SpriteRenderSystem
camera.bounds = map.bounds(new Rectangle());                                // keep the view inside the level
```

### Units and coordinates
- A tile of `pixelsPerUnit` pixels (16 by default) is 1 world unit. The map's bottom-left corner is world (0, 0) and y grows upwards.
- `map.tileX(worldX)`, `map.tileY(worldY)`, `map.tileCenter(x, y, out)`, `map.tile(layer, x, y)` and `map.tiled()` (the libGDX `TiledMap`) help with everything else.

### Layers
- Each tile or image layer is drawn in the render layer named by its `renderLayer` property (default `world`): `background` behind the sprites, `foreground` in front, and so on. The game declares the render layers (chapter 01, section 8).
- Within a render layer, map layers are drawn before the sprites.
- Tiled's parallax factors, offsets, opacity and image-layer repeat are honoured. Level 1's sky is an image layer with `repeatx`, `repeaty` and parallax 0.3.
- Hidden layers are not drawn; group layers pass their `renderLayer` to their children.
- **Animated tiles** run on game time: they stop when the game pauses and follow the time scale.

### Collision from tiles
A cell is **solid** when a tile in it has the boolean property `solid` (or sits on a layer named `collision`), and **one-way** when a tile has `oneWay`. Set the properties once in the tileset (level 1: `maps/terrain.tsx`, grass and dirt solid, the thin planks one-way).

## 4. Components from data
### The component registry
Every component that data can create is registered once, in the game's `ComponentSetup`:
```java
public final class DemoComponents implements ComponentSetup {
    @Override
    public void register(ComponentRegistry registry) {
        registry.register(Patrol.class, Patrol::new)
                .register(PlayerControl.class, PlayerControl::new)
                .register(Enemy.class, Enemy::new);   // ...
    }
}
// PlatformerDemo.config():  config.components = new DemoComponents();
```
- The engine components are registered already: `Transform`, `Sprite`, `Text`, `Collider`, `Actor`, `Solid`, `Animator`, `AnimationStateMachine`.
- **Settable fields** are the public non-static fields of these types: `int`, `long`, `float`, `double`, `boolean`, `String`, enums, `Color`, `Vector2`, `Rectangle` (these three also when `final`), `float[]` and `EntityRef`.
- **Android:** the build runs the same setup to write R8 keep rules (`emberKeepRules`), so registered components survive minification.

### Prefabs
A prefab is a JSON file in the manifest (`"type": "prefab"`), its ID the prefab name. The pig (`prefabs/enemy/pig.json`):
```json
{ "version": 1, "components": {
  "Sprite":   { "regionId": "tex.pig_walk", "origin": [18, 0], "z": 1 },
  "Collider": { "box": { "x": -0.75, "y": 0, "width": 1.5, "height": 1.5 } },
  "Actor": {},
  "Patrol":   { "speed": 2.5, "range": 3, "artFacesLeft": true },
  "Enemy": {},
  "Animator": { "clipSet": "anim.pig", "clip": "walk" } } }
```
Spawn from code, with optional overrides keyed `Component.field`:
```java
int pig = ctx.prefabs().spawn(world(), "enemy/pig", 20f, 4f, Map.of("Patrol.speed", 4f));
```
Every spawned entity gets a `Transform` at the position, and `EntitySpawned(entity, prefab)` is published. Errors name the file, component and field. Games rename fields with prefab versions and migrations (`ctx.prefabs().setVersion(2)`, `addMigration(...)`).

### Placing entities in Tiled
`MapSpawner` turns every object that has a **class** (Tiled's Class field, saved as `type`) into an instance of the prefab with that ID:
- **Position:** a point object is the entity's position; a rectangle or tile object gives its bottom-centre (the feet). The object's name becomes the entity's name (`world.findByName("player")`).
- **Overrides:** custom properties named `Component.field` (or a unique bare `field`) override prefab values. The sign's text in level 1 is a property `Text.text` on the `level/start` object.
- **Templates** (`.tx`) and **object references** work as in Tiled. A reference property fills an `EntityRef` (the camera's `CameraFollow.target` points at the player object), a `Vector2` (a point), a `Rectangle` or a `float[]` (a polyline).
- All problems of a map are reported together, naming the object and property.

## 5. Collision
### Bodies
| Component | What it is |
| --- | --- |
| `Collider` | `box` (offset from the position and size, in world units), `layer` (bit 0–31), `mask` (layers it meets), `trigger` |
| `Actor` | a body moved only through `Collision`; actors never block each other |
| `Solid` | blocks actors; `oneWay` blocks only from above |

A box `(-0.4375, 0, 0.875, 1.375)` is a body 0.875 wide and 1.375 tall standing on its position.

### Moving
```java
// a FIXED system, after input, before CollisionSystem
MoveResult r = collision.moveAndSlide(player, body.vx * delta, body.vy * delta);
if (r.hitY) body.vy = 0f;
boolean grounded = collision.isTouching(player, Direction.DOWN);
```
- `moveAndSlide` moves x then y and stops each axis at the exact contact; nothing tunnels at any speed. `moveAndCollide` stops at the first contact (projectiles, dashes).
- **One-way platforms** block only from above. To drop through, set `Actor.dropThroughTicks` (the demo: down + jump).
- **Moving platforms:** move a `Solid` with `collision.moveSolid(solid, dx, dy)`; actors standing on it ride along and actors in the way are pushed. An actor that cannot be pushed out gets `ActorSquished`.
- Register `CollisionSystem` **after** all movement systems.

### Triggers and queries
- A collider with `trigger = true` never blocks; `CollisionSystem` publishes queued `TriggerEntered(trigger, actor)` and `TriggerExited` (level 1: checkpoints and the goal).
- "Still inside" is a query: `collision.inside(trigger, out)`.
- Queries: `overlapBox(area, mask, out)`, `overlapPoint`, `nearest`, `raycast` (tiles included), `raycastAll`, and `bounds(entity, out)`. Level 1 checks enemies and spikes with `overlapBox` every step, because actors do not trigger each other and spikes should hurt again when blinking ends.

### Collider view
Add `CollisionDebugSystem` and press **F3**: tiles near the camera, solids, actors, triggers and each actor's last contacts are drawn.

## 6. Animation
### Clip sets
A clip set (`"type": "clips"`) names the frames of each clip. Level 1's frog (`anim/frog.clips.json`), shortened:
```json
{ "version": 1, "defaults": { "frameDuration": 0.05 },
  "clips": {
    "idle": { "sheet": "tex.frog_idle", "frameWidth": 32, "frameHeight": 32, "frames": [0, 10] },
    "run":  { "sheet": "tex.frog_run",  "frameWidth": 32, "frameHeight": 32, "frames": [0, 11],
              "events": { "2": "step", "8": "step" } },
    "hit":  { "sheet": "tex.frog_hit",  "frameWidth": 32, "frameHeight": 32, "frames": [0, 6],
              "frameDuration": 0.06, "mode": "once" } } }
```
- **Sources:** a sprite sheet (`sheet`, strip or grid), an atlas sequence (`"atlas": "atlas.hero", "regions": "hero/run"`) or an Aseprite JSON export (`"aseprite": "ase.hero", "texture": "tex.hero", "tag": "run"`, with its own durations and trimmed frames).
- `mode`: `loop` (default), `once`, `pingpong`. `events`: frame index to event name.

### Animator
```java
Animator a = world.get(e, Animator.class);   // prefab: "Animator": { "clipSet": "anim.pig", "clip": "walk" }
a.play("hit");                               // restart a clip
a.playIfNotPlaying("walk");
a.isFinished();                               // a "once" clip reached its end
```
`AnimationSystem` (`UPDATE`) advances animators in game time and writes the frame into the `Sprite`. Frame events arrive as queued `AnimationEvent(entity, clip, name)` (the demo logs footsteps). Facing is `Sprite.flipX`.

### State machine
For characters, code sets **parameters** and a JSON state machine (`"type": "asm"`) picks the clip. Level 1's frog (`anim/frog.asm.json`), shortened:
```json
{ "version": 1, "clipSet": "anim.frog",
  "parameters": { "grounded": "bool", "speedX": "float", "velY": "float", "hit": "trigger" },
  "initial": "idle",
  "states": {
    "idle": { "clip": "idle" },
    "run":  { "clip": "run", "speed": 0.125, "speedParam": "speedX" },
    "jump": { "clip": "jump" },
    "fall": { "clip": "fall" },
    "hit":  { "clip": "hit", "exitOnFinish": true } },
  "transitions": [
    { "from": "*",    "to": "hit",  "when": "hit", "priority": 10 },
    { "from": "idle", "to": "run",  "when": "grounded && speedX > 0.5" },
    { "from": "run",  "to": "idle", "when": "grounded && speedX <= 0.5" },
    { "from": "*",    "to": "jump", "when": "!grounded && velY > 0" },
    { "from": "*",    "to": "fall", "when": "!grounded && velY <= 0" },
    { "from": "fall", "to": "idle", "when": "grounded" } ] }
```
```java
AnimationStateMachine asm = world.get(player, AnimationStateMachine.class);
asm.setBool("grounded", grounded);
asm.setFloat("speedX", Math.abs(body.vx));
asm.trigger("hit");
```
- **Conditions:** terms joined by `&&`: a bool or trigger, `!bool`, or `number-param OP number`. Two transitions make an "or".
- **Choice:** valid transitions from the current state and from `*` (never into the current state); the highest `priority` wins, then file order. `minTime` and `exitTime` delay a transition.
- **States:** `exitOnFinish` returns to `initial` when the clip ends; `speedParam` scales playback (`run` plays faster the faster you move).
- Triggers are cleared after every evaluation.
- Register `AsmSystem` after the systems that set parameters and **before** `AnimationSystem`. Both share one `AnimationLibrary`:
```java
AnimationLibrary clips = new AnimationLibrary(ctx.assets());
world.addSystem(new AsmSystem(world, new AsmLibrary(ctx.assets(), clips), ctx.events()));
world.addSystem(new AnimationSystem(world, clips, ctx.events()));
```

## 7. Text
Fonts are assets. A TrueType font is rendered to a bitmap font when its group loads:
```json
{ "id": "font.pixel", "path": "fonts/PressStart2P-Regular.ttf", "type": "freetype", "group": "global",
  "font": { "size": 8, "mono": true, "shadowOffsetX": 1, "shadowOffsetY": 1, "shadowColor": "#000000A0" } }
```
Font options: `size` (pixels), `mono` (no anti-aliasing, for pixel fonts), `color`, `borderWidth`/`borderColor`, shadow offset and colour, `characters` (`"default"` or a list), `markup` (libGDX colour tags). Bitmap fonts stay type `font`. One `.ttf` can back several entries at different sizes.

World text is a component, drawn by a `TextRenderer` attached to the sprite system:
```java
new TextRenderer(world, ctx.renderer(), ctx.assets(), ctx.time()).attachTo(sprites);

Text text = world.add(entity, new Text());
text.font = "font.pixel";
text.text = "Level complete!\n1:23 - 2 deaths";
text.align = TextAlign.CENTER;    // the anchor is the baseline's left end, centre or right end
text.offset.set(0f, 4.5f);        // world units from the entity
```
- One font pixel is one virtual pixel; `scale` multiplies it. `wrapWidth` (world units) wraps lines; `\n` breaks them.
- Text is drawn after the sprites of its `layer`, snapped to whole pixels, and laid out again only when it changes.
- `TextDraw.draw(batch, font, text, x, y, align, wrapWidth, pixelsPerUnit)` draws one-off text inside your own render code.

## 8. Hot reload
In a desktop dev run (`.\gradlew.bat :demos:demo-platformer:desktop:run`), saved files show up in the running game within about half a second:

| You save | What happens |
| --- | --- |
| a map, its tileset or a tileset image | the map reloads and the current scene restarts with a fresh world, so moved enemies and new platforms appear |
| a prefab | later spawns use it (entities already spawned do not change) |
| a clip set or state machine | animators switch on their next update |
| a texture or atlas | sprites using `regionId` look their region up again |
| a font | text is laid out again |
| the input bindings | applied at once |
| `manifest.json` | a warning: restart the game |

- **A broken file never crashes the game:** the old version stays, and the error is logged with the file and the problem (and shown in the dev tools).
- **Your own game:** set `config.assetsDir` to the source assets folder and `config.assetWatcher = DevTools.watch(new DirectoryWatcher(Path.of(dir)))` in a dev-only launcher; the demo's `DevLauncher` does this, and the Gradle `run` task passes the folder.
- A scene can take over a map reload itself by overriding `onAssetReloaded(AssetReloaded e)` and returning true. `AssetReloaded` and `AssetReloadFailed` events are published for game code.
- Release builds and Android have no watcher and read the packed assets.

## 9. Dev tools
Press **F1** in a desktop dev run. Panels open from the **Panels** menu at the top:

| Panel | Use it to |
| --- | --- |
| Entities | list the current scene's entities; filter by name, tag or component; select one |
| Inspector | see and edit the selected entity's components live (numbers, text, enums, colours, vectors, rectangles); **Copy as prefab JSON** |
| Animation | see the state machine's state, time in state and last transition; change parameters, fire triggers |
| Time | pause, **step one fixed tick** while paused, change the time scale |
| Frame | frame-time graph, FPS, engine time, entity count |
| Debug draw | the collider view (the same as F3) |
| Events | the last 200 events with their values; filter, pause, clear |
| Assets | every asset, a **Reload** button each, and the hot reload history and last error |

- While a panel has the mouse or keyboard, the game does not see those inputs.
- **Your own game:** wrap the game in a dev-only launcher: `new Lwjgl3Application(DevTools.wrap(new EmberGame(config)), windowConfig)`.
- The tools are in `ember-devtools`, which only dev runs use. The release build refuses to package them (`verifyReleaseClasspath`), and Android never has them; use the F3 overlay there.

## 10. Level 1, step by step
### The player
`PlayerControl` holds the movement tuning; every value can be changed in the prefab, by hot reload or live in the inspector:

| Field | Default | Effect |
| --- | --- | --- |
| `maxSpeed` | 8 | top running speed (units/s) |
| `groundAccel` / `groundDecel` | 60 / 70 | speeding up / slowing down on the ground (units/s²) |
| `airAccel` / `airDecel` | 35 / 20 | the same in the air |
| `gravity`, `fallMultiplier`, `maxFall` | 40, 1.6, 25 | falling is faster than rising, for a snappier jump |
| `jumpSpeed` | 16 | about 3.2 units high when held |
| `jumpCut` | 0.45 | releasing jump while rising cuts the upward speed: a short hop |
| `coyoteTicks` | 6 | a jump still works 6 fixed ticks (0.1 s) after running off a ledge |
| `bufferTicks` | 6 | a jump pressed up to 6 ticks before landing starts on landing |
| `stompBounce` | 12 | the bounce after landing on an enemy |

`PlayerControlSystem` (`FIXED`) reads input, moves with `moveAndSlide`, feeds the state machine and publishes `Jumped` and `Landed`.

### The rules
`LevelSystem` (`FIXED`, after `CollisionSystem`):
- **Enemies:** landing on one while falling defeats it (hit clip, then removed) and bounces the player; touching one otherwise hurts.
- **Spikes** hurt; so does falling out of the map.
- **Hurt:** the hit clip plays for 0.42 s with input off, the player returns to the last checkpoint (or the start) and blinks for 1 s without taking damage; deaths are counted.
- **Checkpoint:** touching the flag makes it the respawn point; the flag raises and waves.
- **Goal:** the trophy shows "Level complete!" with the time and deaths, publishes `LevelCompleted`, and the level restarts after `Goal.restartSeconds` (4).

### Editing the level
Open `demos/demo-platformer/core/assets/maps/level1.tmx` in Tiled, ideally while the game runs:
- **Add an enemy:** insert a point object on the `entities` layer with Class `enemy/pig`, standing on the ground. It patrols `Patrol.range` (3) units each way; give the object a property `Patrol.range` or `Patrol.speed` to change one pig.
- **Add spikes:** a point object with Class `hazard/spikes` on the ground.
- **Move the checkpoint or goal:** drag the object; it must stand on solid ground (the map test checks this).
- **Change the ground:** paint with the terrain tileset; grass and dirt are solid, the thin planks are one-way.
- **Change the sign:** edit the `Text.text` property of the `start` object (multi-line text is fine).

Save, and the game restarts the level with your change. `LevelMapTest` checks that every object names a prefab in the manifest and that the player, checkpoint and goal stand on ground.

## 11. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| `unknown component "X" (registered: [...])` in a prefab or map | the component is not in the game's `ComponentSetup` | register it in `config.components` |
| A map object does not appear | it has no Class (`type`), so it is not an entity | set its Class to a prefab ID |
| The map fails to load naming an object and "Tiled 1.10 or later" | the map was saved with Tiled 1.9 (`class=`) | save it with Tiled 1.10 or newer |
| The player falls through the floor | the tiles have no `solid` property, or the tile grid was not added | set `solid` in the tileset; `collision.addTileGrid(map.collision(), 0)` |
| Triggers never fire | `CollisionSystem` is missing or registered before the movement systems | register it after all movement |
| The player can stand inside spikes | the hazard only reacts to `TriggerEntered` while the player was invulnerable | check overlap every step, as `LevelSystem` does |
| An animation never changes | `AnimationSystem` runs before `AsmSystem`, or the parameter is never set | register `AsmSystem` first; set parameters every step |
| `State machine "..." has no parameter "x"` | the parameter is not declared in the ASM file | add it under `parameters` with its type |
| Text shows `#10` instead of a new line | a newline escaped in a Tiled attribute | use Tiled's multi-line property (saved as element text) |
| A saved file changes nothing | not a dev run, or the file is not in the manifest | run with `gradlew run`; add the file to the manifest (manifest changes need a restart) |
| The game ignores keys while the dev tools are open | an ImGui text field or window has focus | click the game area, or press F1 to hide the tools |
| `Call requires API level NN` | a Java method missing on Android 6 | use an alternative available on API 23 |

## 12. Where to look when something breaks
1. **Log** — categories `tilemap`, `data`, `animation`, `assets`, `hotreload`, `devtools`, `scene`, `demo` (plus chapter 01's). Prefab, map and animation errors list every problem with the file and field.
2. **Collider view** — F3, or Debug draw in the dev tools.
3. **Dev tools** (F1) — the inspector shows live component values; the Events panel shows what was published; the Assets panel shows the last reload error.
4. **Tests** — `.\gradlew.bat build` runs the engine tests and the demo's movement and map tests (`PlatformerMovementTest`, `LevelMapTest`).
5. **Specs** — F-005 and F-022 (input and touch), F-009 (Tiled maps), F-010 (prefabs and placement), F-006 (collision), F-007 (animation), F-004 (text), F-008 (hot reload), F-018 (dev tools), F-015 (level 1).
