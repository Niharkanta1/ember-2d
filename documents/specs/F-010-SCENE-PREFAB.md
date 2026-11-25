# F-010 — Scenes, Data and Prefabs

- Status: Draft
- Phase: 1 (scene lifecycle), 2 (JSON prefabs and Tiled spawning), 3 (transitions, preloading)
- Module / package: `ember-core` / `ember.scene`, `ember.data`
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
