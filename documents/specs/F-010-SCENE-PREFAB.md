# F-010 — Scenes, Data and Prefabs

- Status: Scene lifecycle Ready (expanded and approved in I-070, 2026-11-26; see "Scene lifecycle (I-070)"); component registry, prefabs and Tiled placement Ready (I-112, approved 2026-11-26; see "Prefabs and placement (I-112)" and "Decisions in I-112"); transitions, loading screen and preloading Ready (I-240, approved 2026-11-27; see "Transitions, loading screen and preloading (I-240)"); carrying entities, scene data files: Draft
- Phase: 1 (scene lifecycle), 2 (JSON prefabs and Tiled spawning), 3 (transitions, preloading)
- Module / package: `ember-core` / `ember.app` (`Scene`, `SceneManager`; ADR-022), `ember.scene` (scene data, transitions: later), `ember.data`
- Depends on: F-001, F-003, F-008, F-009, F-019
- Tasks: I-070, I-112, I-240

## Goal
A clear runtime scene model, data-driven level composition, and reusable entity definitions — with authored data kept separate from runtime state and save data.

## Scope
**Scenes (runtime)**
- `Scene` interface: `enter(ctx)`, `exit()`, `update`, `render` hooks; owns a `World`, its systems and scoped subscriptions/timers.
- `SceneManager`: `push`, `pop`, `replace`, transitions (fade, cut), loading screen while the next scene's asset group loads.
- Persistent entities carried between scenes explicitly (`carry(entity)`).

**Scene data (authored)**
- A level scene = Tiled map (F-009) + optional sidecar `level1.scene.json` (music, camera bounds, systems to enable, asset group).
- Menu/UI and novel scenes are code- or Ink-defined.

**Component registry**
- Every component type usable from data is registered once (`ComponentRegistry.register(Health.class)`), with its JSON name.
- The registry is the single source for prefab loading, Tiled overrides, save serialization, the dev-tools inspector, the Tiled type generator and generated R8 keep rules (ADR-019).

**Prefabs**
- JSON file of component defaults, referenced by prefab ID (its path under `prefabs/` without extension):
```json
{ "version": 1,
  "components": {
    "Sprite": { "regionId": "atlas.main/slime_idle", "layer": "world" },
    "Collider": { "box": { "x": -6, "y": 0, "width": 12, "height": 10 }, "layer": 2 },
    "Health": { "max": 3 },
    "AnimationStateMachine": { "definitionId": "anim.slime" },
    "PatrolPath": {} } }
```
- Instantiation: create entity → add components from prefab → apply overrides (Tiled object properties, or a map passed from code) → resolve entity references (second pass) → publish `EntitySpawned`.
- Spawning from code: `prefabs.spawn("enemy/slime", x, y)`; used by spawners and bullets-to-pickup conversions.

**Data**
- All Ember2D JSON carries `version`; `ember.data` runs registered migrations on load and validates required fields.

## Scene lifecycle (I-070)
**API sketch** (`ember.app`, ADR-022):
```java
@PublicApi public abstract class Scene {
    protected abstract void enter(EngineContext context);   // build entities and systems in world()
    protected void exit() {}
    protected void update(float delta) {}                   // UPDATE, after the world's systems; scaled delta
    protected String assetGroup() { return null; }          // loaded before enter, unloaded after exit
    protected final World world();  protected final EngineContext context();
    protected final Timers timers();  protected final Tweens tweens();   // scene-scoped
}
@PublicApi public final class SceneManager {                // context.scenes()
    public void push(Scene scene);  public void pop();  public void replace(Scene scene);
    public Scene current();  public int depth();
}
// EngineConfig.firstScene: Supplier<Scene> (null starts no scene)
```
**Behaviour**
- `push`, `pop` and `replace` take effect at the start of the next frame (before `INPUT` systems); changes requested while changes are applied (for example in `enter`) wait one more frame.
- Enter: load the scene's asset group (blocking in part 1; a loading screen comes with I-240), create its `World`, open its event scope (`context.events().on(...)` subscriptions belong to the scene), call `enter`. If `enter` throws, the scene is released and the error propagates.
- Exit: call `exit`, clear its world, close its event scope, cancel its timers and tweens, unload its asset group unless another scene in the stack uses the same group.
- Only the top scene runs; a scene below keeps its world and subscriptions but its systems, timers and `update` stop until it is on top again. Overlay scenes (a pause menu drawn over a running level) are decided with the pause menu (Phase 3).
- Per frame for the top scene: its world runs every phase; in `UPDATE` its timers and tweens advance first (like the global ones), then its world's systems, then `update(delta)`.
- Boot: `EmberGame` enters `firstScene` during `create()`, so a broken first scene fails at boot. Shutdown: scenes exit top first, before game services are disposed.
- Errors: `pop()` that would leave no scene throws (use `replace`); a scene instance already in the stack or waiting to start throws.

## Prefabs and placement (I-112)
Status: Ready (approved 2026-11-26). Covers the component registry, JSON prefabs, spawning from code, spawning from Tiled objects (F-009 "Entity placement workflow") and generated R8 keep rules. The package is `ember.data`, which may use `tilemap` (ARCHITECTURE rule 4); reflection stays inside it (rule 10).

**Component registry (`ComponentRegistry`).**
- **Registration:** a component is registered once, with its JSON name (default: simple class name) and a factory, for example `registry.register(Health.class, Health::new)`. Using the factory avoids reflective construction.
- **Engine components:** pre-registered as `Transform` and `Sprite`.
- **Game components:** games register theirs in one place, an `EngineConfig.components` (`ComponentSetup`: `void register(ComponentRegistry r)`). The runtime and the keep-rule task (below) run the same setup.
- **Fields:** the public non-static fields are discovered once per class with libGDX's reflection (`com.badlogic.gdx.utils.reflect`, portable to Android). They are cached as typed setters.

| Field type | JSON / Tiled value |
| --- | --- |
| `int`, `long`, `float`, `double`, `boolean`, `String` | number, bool, string; Tiled int/float/bool/string |
| `enum` | constant name as a string |
| `Color` (also `final`, set in place) | `"#RRGGBB"`, `"#RRGGBBAA"` or `{r,g,b,a}`; Tiled color |
| `Vector2` (also `final`) | `{x,y}` or `[x,y]`; Tiled: an object reference to a point gives its world position |
| `Rectangle` (also `final`) | `{x,y,width,height}`; Tiled: an object reference to a rectangle gives its world bounds |
| `float[]` | number list; Tiled: an object reference to a polyline or polygon gives its world vertices |
| `EntityRef` | Tiled object reference to a spawned object (resolved in the second pass) |

- **Errors:** any other field type is not settable from data, and using it is an error naming the component, field and type. Nested custom classes are out of scope (F-010 non-goal).

**Prefabs (`PrefabLibrary`, available as `context.prefabs()`).**
- **Where they live:** each prefab is a manifest entry of the new type `prefab` (a JSON file). Its ID is the prefab ID, for example `{ "id": "enemy/slime", "path": "prefabs/enemy/slime.json", "type": "prefab", "group": "level1" }`, so prefabs load with a scene's asset group. Directory scanning is not portable to Android and desktop jars.
- **Format:**
  - `{ "version": N, "components": { "Health": { "max": 3 }, ... } }`
  - A prefab is parsed and validated on first use, and then cached.
  - Errors name the file, the component and the field: unknown component, unknown field, wrong value type, missing or newer `version`.
- **Migration:**
  - Each game sets a prefab data version (`PrefabLibrary.setVersion(n)`, default 1) and registers migrations (`addMigration(fromVersion, JsonValue -> JsonValue)`).
  - Older files are upgraded step by step on load, and a newer file is an error.
  - This is how games rename fields.
- **Spawning from code:**
  - `spawn(World world, String prefab, float x, float y)` returns the entity.
  - `spawn(world, prefab, x, y, Map<String, Object> overrides)` sets overrides keyed `Component.field` or a bare field name.
  - Spawning creates the entity, adds a `Transform` at (x, y) (prefab values for scale and rotation apply), adds each component from a new instance with the prefab's values, applies overrides, then publishes `EntitySpawned(entity, prefabId)` on the world's scene event scope.

**Placement from Tiled (`MapSpawner`).**
- **Which objects:** `new MapSpawner(prefabs, events).spawn(world, tileMap)` spawns every object on every object layer that has a class (TMX `type`) as an instance of that prefab. Objects without a class are not entities, but other objects can reference them (points, rectangles, polylines).
- **Position:** the entity's `Transform` is the object's **bottom-centre** in world units (the feet, matching sprites anchored at their feet); a point object is its own position. Tiled rotation (clockwise degrees) becomes `Transform.rotation` (counter-clockwise), and the object name becomes the entity name. A flipped tile object sets `Sprite.flipX`/`flipY` when the prefab has a `Sprite`.
- **Tile images:** the tile image of a tile object is editor-only. When the prefab has no `Sprite`, the entity gets one with the tile's region, anchored bottom-centre.
- **Overrides** come from the object's custom properties:
  - `Component.field`
  - a bare `field` (it must be unique among the prefab's components; otherwise an "ambiguous" error)
  - a class-typed property named after a component (for example `Health` with members `{max: 5}`): each member overrides that component's field, which matches the Tiled types I-245 will generate
  - libGDX's own keys (`id`, `x`, `y`, `width`, `height`, `type`, `rotation`, `gid`) are ignored
- **Two passes:**
  - Pass 1 creates every entity.
  - Pass 2 applies the overrides and resolves object references: `EntityRef` gets the spawned entity; `Vector2`, `Rectangle` and `float[]` get the target's shape.
  - `EntitySpawned` events are published after pass 2.
- **Errors** name the map, the object name, its ID and the property: unknown prefab, unknown or ambiguous property, wrong type, and a reference to a missing object or to an object that was not spawned when an `EntityRef` is needed. All problems of a map are reported together.
- **Tiled 1.9 `class=`:** libGDX drops the Tiled 1.9 `class=` object attribute (S-02). The engine's map loader now rejects such objects with an error that names the object and says to save with Tiled 1.10 or later (which writes `type=`). Placement never silently loses a class.
- **Templates and object references:** both are resolved by libGDX (S-02).

**R8 keep rules.**
- **Generator:** `dev.ember2d.data.KeepRules` (a `main` in `ember.data`) runs a game's `ComponentSetup` and writes `-keep class X { <init>(); <fields>; }` for every registered component (the rule S-04 verified).
- **Gradle task:** a demo Gradle task `emberKeepRules` (JavaExec) writes `build/generated/ember-keep-rules.pro`. The Android release build type lists it in `proguardFiles`.
- **Release build:** I-112 turns on R8 for the demo's **release** build, signed with the debug key until I-255 adds real signing. That makes the F-010 acceptance check possible: prefabs spawn in a minified build on the emulator.

## API sketch (I-112)
```java
package dev.ember2d.data;

@PublicApi public interface ComponentSetup { void register(ComponentRegistry registry); }

@PublicApi public final class ComponentRegistry {
    public <T> ComponentRegistry register(Class<T> type, Supplier<T> factory);              // name = simple name
    public <T> ComponentRegistry register(String name, Class<T> type, Supplier<T> factory);
    public boolean contains(String name);
    public Set<String> names();
    public List<Class<?>> types();
    public List<FieldInfo> fields(String component);       // name, type, default value (inspector, I-245)
}
@PublicApi public record FieldInfo(String name, Class<?> type, Object defaultValue) { }

@PublicApi public final class PrefabLibrary {
    public PrefabLibrary(ComponentRegistry registry, Assets assets);
    public void setVersion(int version);
    public void addMigration(int fromVersion, UnaryOperator<JsonValue> migration);
    public boolean contains(String prefab);
    public int spawn(World world, String prefab, float x, float y);
    public int spawn(World world, String prefab, float x, float y, Map<String, Object> overrides);
}
@PublicApi public record EntitySpawned(int entity, String prefab) { }
@PublicApi public final class PrefabException extends RuntimeException { List<String> problems(); }

@PublicApi public final class MapSpawner {
    public MapSpawner(PrefabLibrary prefabs, EventBus events);
    public IntArray spawn(World world, TileMap map);       // entities in map order
}

public final class KeepRules { public static void main(String[] args); }   // args: setup class, output file
```
In `app`:
- `EngineConfig.components` (`ComponentSetup`, default none)
- `EngineContext.prefabs()`
- `EngineContext.Builder.components()` (the registry, so tests can register)

## Worked example (I-112)
`prefabs/enemy/patroller.json`:
```json
{ "version": 1, "components": {
    "Sprite": { "regionId": "tex.player", "origin": [8, 0], "tint": "#99E699" },
    "Patrol": { "speed": 6 } } }
```
Tiled object of class `enemy/patroller`, with the property `Patrol.minX = 6` and the property `leader` (object) set to the player. Code:
```java
public final class DemoComponents implements ComponentSetup {
    public void register(ComponentRegistry r) { r.register(Patrol.class, Patrol::new).register(PlayerControl.class, PlayerControl::new); }
}
// enter():
IntArray spawned = new MapSpawner(context.prefabs(), context.events()).spawn(world(), map);
int extra = context.prefabs().spawn(world(), "enemy/patroller", 12f, 2f, Map.of("Patrol.speed", 3f));
```

## Decisions in I-112
The defaults were approved as proposed. Details settled while implementing:
- **Constructors:** `PrefabLibrary(registry, assets, events)` takes the event bus so it can publish `EntitySpawned`; `MapSpawner(prefabs)` therefore needs no bus.
- **File names in errors:** `Assets` gained `path(id)`, a default method that returns the ID, so prefab errors name the file.
- **Prefab parsing:** a prefab is parsed and validated on its first spawn, then cached. Its JSON is copied before migrations run, so the loaded asset is never changed.
- **Transform:** every spawned entity has a `Transform`. One is added when the prefab has none.
- **Field discovery:** final fields of a supported type other than `Color`, `Vector2` and `Rectangle` are not settable. Errors name them with "(final)", and other unsupported types by their type name. `fields()` lists only settable fields.
- **Keep rules:**
  - `KeepRules` also writes `-keep enum E { *; }` for enums used in component fields.
  - The demo's Gradle task resolves the core project through its own configuration (`emberKeepRulesClasspath`), because Gradle forbids resolving another project's configuration directly.
- **Object references:** they reach only objects in the same map. Tiled colour properties (`#AARRGGBB`) arrive already converted by libGDX.
- **R8 class names:** in the minified build, engine log lines show R8-renamed class names (for example "Entered i3" for the demo scene). The S-04 follow-up (`-keepnames` for `Scene` and `EntitySystem` subclasses, and the mapping file) stays with I-255.
- **Demo:**
  - The player, two patrollers (template `maps/patroller.tx`; one overrides `Patrol.speed`, `minX`, `maxX` and `Sprite.tint`) and the camera (`CameraFollow.target` is an object reference to the player) come from the map. A third patroller is spawned from code.
  - The demo's release build is minified with R8 and signed with the debug key.

## Transitions, loading screen and preloading (I-240)
Status: Ready. Expanded in I-240 and approved under the developer's standing approval for Phases 3 to 6 (2026-11-27). Carrying entities between scenes (`carry`) and scene data files (`*.scene.json`) are not part of I-240; they stay Draft until a demo needs them.

**Transitions.**
- `push`, `pop` and `replace` gain an overload that takes a `Transition`. The plain methods keep their I-070 behaviour: the change happens at the start of the next frame and the asset group loads blocking.
- `Transition.fade(seconds)` (black) or `fade(seconds, color)`:
  - **Fade-out:** the cover goes from clear to opaque over the first half. The outgoing scene is still drawn, but it is frozen: only its `RENDER` systems run, and its timers, tweens and `update` stop.
  - **At full cover:** the outgoing scene exits (`pop`, `replace`), or it stays below the new one (`push`).
  - **Wait for the group:** the incoming scene waits until its asset group has loaded.
  - **Fade-in:** the incoming scene enters and runs normally while the cover fades back out over the second half.
- `Transition.cut()` is a fade of 0 seconds. The switch happens at the next frame, but the group loads in the background behind the loading screen instead of blocking.
- **Timing:** transitions run on real (unscaled) time, so they work while the game is paused.
- **Loading starts early:** the incoming scene's group starts loading when the transition starts, so it loads during the fade-out.

**Loading screen.**
- **When it shows:** while the cover is opaque and the incoming group is still loading, the engine draws the cover colour and, after 0.1 s (so quick loads do not flash), the loading screen.
- **What draws it:** `EngineConfig.loadingScreen` (`LoadingScreen`). The default is `LoadingScreen.bar()`, a progress bar in the middle of the screen; `null` shows only the cover colour.
- **How it is drawn:** in window pixels, with a white pixel region the engine owns, so an implementation needs no textures of its own.

**Input during a transition.**
- While the cover fades out or waits for loading, presses and touches do not reach the outgoing scene's UI. Releases still pass, so nothing sticks.
- Game systems do not run in that time anyway.
- During the fade-in the new scene gets input normally.

**Queued changes.**
- Scene changes requested during a transition (including the plain methods and `restart`) wait until it has finished, then run in order.
- `inTransition()` tells whether a transition is running.

**Preloading.**
- `preload(group)` starts loading a group in the background and returns its `AsyncLoad`. For example, a menu preloads the first level while the player reads it.
- **Who owns the group:** the scene manager holds a preloaded group until a scene that uses it enters (then that scene owns it) or `cancelPreload(group)` releases it.
- **When a group is not unloaded:** while a scene in the stack uses it, while it is preloaded, or when it is the group of the scene that replaces the exiting one. This also stops a `replace` between two scenes with the same group from unloading the group and loading it again.

**Errors.**
- A missing required file fails as in F-008 (at `loadGroup` or in the frame that loads it).
- An `enter` that throws during a transition ends the transition and propagates, as in I-070.
- `pop(transition)` follows the same rule as `pop()`.
- A negative or non-finite duration throws `IllegalArgumentException`.

**API sketch** (`ember.app`):
```java
@PublicApi public final class Transition {
    public static Transition cut();
    public static Transition fade(float seconds);                // black
    public static Transition fade(float seconds, Color color);   // the colour is copied
    public float seconds();  public Color color();
}
@PublicApi public interface LoadingScreen {
    /** Window pixels; the batch is begun with a window projection; pixel is a white 1x1 region. */
    void draw(Batch batch, TextureRegion pixel, float width, float height, float progress);
    static LoadingScreen bar();
}
// SceneManager additions
public void push(Scene scene, Transition transition);
public void pop(Transition transition);
public void replace(Scene scene, Transition transition);
public boolean inTransition();
public AsyncLoad preload(String group);
public void cancelPreload(String group);
// EngineConfig
public LoadingScreen loadingScreen = LoadingScreen.bar();
```
**Internal pieces:**
- `SceneManager` exposes package-private `coverAlpha()`, `coverColor()` and `loadingProgress()`, which is -1 when no loading screen shows.
- A new `TransitionOverlay` in `app` draws the cover and the loading screen after the scene UI and before the debug panel and on-screen controls. It is created on first use.
- `UiInputRouter` swallows presses while the cover fades out or waits for loading.

**Demo:**
- Menu to level, level to menu and "Play again" use `fade(0.5)`.
- The level map moves to a `level1` asset group, which the main menu preloads.

**Tests (headless, `SceneManagerTest` with fake assets and a fixed clock):**
- A fade freezes the outgoing scene and switches at full cover; the cover alpha rises and falls.
- A loading group holds the cover until it is done; `loadingProgress` shows only after the delay.
- `cut()` with a loaded group switches in one frame.
- Changes requested during a transition wait.
- `pop` and `push` with transitions.
- A preloaded group is not loaded again and survives `replace`; `cancelPreload` unloads it.
- A same-group `replace` does not unload the group.
- Bad durations are rejected.
- Input routing during a fade.
- `LoadingScreen.bar()` drawing on a recording batch.

## Decisions in I-240
- **The new scene runs during the fade-in.** It runs from the frame it enters, and that frame is fully covered.
- **Order on screen:** the cover is drawn above the scene UI. The debug panel and the on-screen controls stay above it.
- **Reloading the level map:** when the level returns to the menu, the level's group is unloaded and the menu then preloads it again. The menu's preload runs only in its `enter`, after the level has exited. For the demo's single map this costs a few milliseconds. A game that wants to avoid it can preload from the outgoing scene.
- **Checked on the emulator (API 23):**
  - Menu to level and level to menu (from the pause menu, with game time paused) both fade.
  - The preloaded `level1` group is not loaded a second time on Play.

## Acceptance criteria (I-112)
- **Registry and prefabs:**
  - Registry field discovery and every supported field type, from JSON and from Tiled values.
  - Errors for unknown component, unknown field and wrong type name the file and the field. For maps they name the map, the object name and ID, and the property.
  - Migration of a v1 prefab to v2 in a test.
- **Placement:**
  - A Tiled template object spawns with the template's class and the instance override.
  - Object references become valid `EntityRef`s; point, rectangle and polyline references give shapes.
  - A Tiled 1.9 `class=` object fails with a clear error.
- **Demo:**
  - The patrollers, the player spawn and the camera (`CameraFollow.target` as an object reference to the player) come from the map's `entities` layer, using a template.
  - Checked by you on desktop, and on the emulator in the R8-minified release build.

## Non-goals
- Nested prefabs and prefab variants (defer until a demo needs them).
- Saving runtime scene state (saves are F-013).

## Acceptance criteria
- Scene enter/exit lifecycle; stack push/pop; no leaks after exit (entities, subscriptions, timers, asset group references).
- Transition with loading screen; asset group loaded before `enter`.
- Prefab + override instantiation from Tiled and from code; unknown component/field errors name file and field.
- Object references between spawned entities resolve.
- Migration of a v1 prefab to v2 in a test.
- Prefab and registry work in an R8-minified Android build.
