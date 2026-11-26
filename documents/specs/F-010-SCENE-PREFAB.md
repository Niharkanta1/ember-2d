# F-010 — Scenes, Data and Prefabs

- Status: Scene lifecycle Ready (expanded and approved in I-070, 2026-11-26; see "Scene lifecycle (I-070)"); component registry, prefabs and Tiled placement Ready (I-112, approved 2026-11-26; see "Prefabs and placement (I-112)" and "Decisions in I-112"); transitions and loading screens (I-240), carrying entities, scene data files: Draft
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
