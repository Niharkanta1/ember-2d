# F-010 — Scenes, Data and Prefabs

- Status: Scene lifecycle Ready (expanded and approved in I-070, 2026-11-26; see "Scene lifecycle (I-070)"); transitions and loading screens (I-240), carrying entities, scene data files, prefabs and data (Phase 2): Draft
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
