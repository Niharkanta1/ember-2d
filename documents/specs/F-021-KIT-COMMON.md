# F-021 — Common Gameplay Kit

- Status: Health, damage, hit and hurt boxes, triggers, pools, spawners, lifetime and teams Ready (I-300, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Common kit (I-300)"); dialogue runner Ready (I-420, approved 2026-11-27; see "Dialogue runner (I-420)")
- Phase: 4 (some parts may be pulled into Phase 3 by the platformer demo); `DialogueRunner` in Phase 5
- Module / package: `kit-common` (shared kit)
- Depends on: F-003, F-006, F-019, F-002, F-013; blade-ink (dialogue)
- Tasks: S-03, I-300, I-420
- Decisions: ADR-003 (shared kits), ADR-017 (Ink)

## Goal
Genre-neutral gameplay building blocks used by every genre kit, so no mechanic is written twice and no genre kit depends on another.

## Scope
- **Health and damage:** `Health` (current, max), `Damage` events, invulnerability window, death event (`Died`), optional knockback vector.
- **Hit/hurt boxes:** `Hitbox` (deals damage, team/layer, active window) and `Hurtbox` (receives damage); resolution via `Collision` overlap each fixed step; one hit per hitbox activation per target.
- **Triggers:** generic trigger zones with filters (tag/layer) and once/repeat behaviour; used for pickups, checkpoints, doors, level exits.
- **Pools:** typed entity pools for effects and frequently spawned entities (prefab-based), with automatic return on destroy. (Bullets use `kit-bullets`, F-024, not entity pools.)
- **Dialogue runner:** `DialogueRunner` over blade-ink — load an Ink story by asset ID, continue, current line, speaker, tags, choices, choose, variables, external function binding, state save/restore into the save slot. Used by the RPG package (F-017) and `kit-novel` (F-023).
- **Spawners:** spawn a prefab at intervals, on trigger, or in waves; cap on live count.
- **Lifetime:** destroy after N seconds.
- **Team/faction:** simple team ID used by hitboxes.

## Common kit (I-300)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.common`; it depends on `ember-core` only (an ArchUnit test checks this).

**Components** (registered with `CommonKit.register(registry)`, so they are usable in prefabs and Tiled):

| Component | Fields (defaults) | Notes |
| --- | --- | --- |
| `Health` | `max` 3, `current` -1 (-1 = start full), `invulnerableSeconds` 0.5, `destroyOnDeath` false; runtime `invulnerableLeft`, `dead` | |
| `Team` | `id` 0 | hit boxes never hurt their own team |
| `Hitbox` | `box` (local rectangle), `damage` 1, `knockback` 0, `active` true, `activeSeconds` 0 (0 = while active), `destroyOnHit` false; runtime `activation` | `restart()` starts a new activation |
| `Hurtbox` | `box` (local rectangle; an empty box uses the entity's `Collider` box) | the entity needs `Health` |
| `Lifetime` | `seconds` 1; runtime `left` | despawned when it runs out |
| `TriggerZone` | `tag` "" (any), `team` -1 (any), `once` false; runtime `fired` | the entity also needs a trigger `Collider` |
| `Spawner` | `prefab`, `interval` 2, `burst` 1, `total` 0 (0 = endless), `maxAlive` 5, `delay` 0, `waitFor` "" (a zone name), `offsetX`/`offsetY` 0 | |

**Damage (`HealthSystem`, phase `FIXED`):**
- **The calls:** `damage(target, amount, source, knockbackX, knockbackY)` returns whether the damage applied. `heal(target, amount)`, `kill(target, source)` and `isInvulnerable(target)` also exist.
- **Applying damage:** damage applies only to a living target that is not invulnerable. It lowers `current`, starts the invulnerability window and publishes `Damaged(target, source, amount, healthLeft, knockbackX, knockbackY)`.
- **Death:** when health reaches 0, `Died(entity, killer)` is published exactly once, and the entity is despawned if `destroyOnDeath` is set.
- **Each fixed step:** the system starts `current` at `max` where it is -1, and counts down the invulnerability windows.
- **Knockback** is only reported; genre kits apply it to movement.

**Hits (`HitSystem`, phase `FIXED`, after movement):**
- **What counts as a hit:** an active hitbox overlapping a hurtbox in world space (the Transform position plus the local boxes) hits it once per activation. A new activation starts when `active` turns true or `restart()` is called.
- **Teams:** a hitbox never hits its own team (`Team` on each entity; no `Team` is team 0) or its own entity.
- **Knockback:** the vector points from the hitbox's centre to the hurtbox's centre, with length `knockback`.
- **Active window:** with `activeSeconds` above 0, the hitbox turns itself off after that long.
- **`destroyOnHit`:** despawns the hitbox entity after its first hit that dealt damage.
- **Cost:** the check is a plain loop over hitboxes against hurtboxes, which is enough for melee, enemies and pickups. Bullets use `kit-bullets` (F-024).

**Triggers (`TriggerZoneSystem`):**
- **Firing:** it listens to the collision `TriggerEntered` and `TriggerExited` events and publishes `ZoneEntered(zone, entity, name)` and `ZoneExited(zone, entity, name)` when the entity passes the zone's filters.
- **Filters:** `tag` must be a world tag on the entity (`world.hasTag`), and `team` must match its `Team`.
- **`once`:** the zone fires only for the first matching entity, then sets `fired`; exits are reported only for zones that have not fired once.
- **Layers:** filtering by layer is the `Collider` mask, as in F-006.

**Lifetime (`LifetimeSystem`, `UPDATE`):** counts down on scaled time and despawns the entity when its time runs out.

**Spawners (`SpawnerSystem`, `UPDATE`):**
- **Timing:** after `delay`, it spawns `burst` instances of `prefab` every `interval` seconds at the spawner's position plus the offset, until `total` instances have spawned.
- **Live cap:** it never has more than `maxAlive` living instances. A burst spawns as many as fit, and with no room it tries again on the next frame.
- **Waiting for a zone:** with `waitFor` set, it waits until a `ZoneEntered` from a zone with that name.
- **Events:** each spawn publishes `Spawned(spawner, entity)`, and `SpawnerFinished(spawner)` follows the last one.
- **What counts as alive:** an instance that is despawned or returned to a pool is no longer alive.

**Pools (`EntityPool`) and `Despawn`:**
- **Creating a pool:** `EntityPool.ofPrefab(world, prefabs, prefab, warm)` creates `warm` entities up front.
- **`obtain(x, y)`:**
  - takes a free entity, or creates one when none is free;
  - resets its stored component instances to the prefab's values with a new engine call, `PrefabLibrary.reset(prefab, components)`, then attaches them again (queued, like any `world.add`, while a system runs);
  - moves it to (x, y).
- **`free(entity)`:** takes every component but its `Pooled` marker off the entity and keeps them for the next `obtain`. The entity stays alive with only the marker, so no system sees it.
- **Despawning:** `Despawn.despawn(world, entity)` returns a pooled entity (one with a `Pooled` component) to its pool and destroys any other entity. Every kit system that removes entities uses it (death, `destroyOnHit`, `Lifetime`), so pooled entities go back automatically.
- **No new entities after warm-up:** a pool creates no new entities or component instances after warm-up. Resetting fields uses `ember.data`'s field setters; for float fields these box values, so "no allocation" covers entities and components, not every byte.

**Engine addition (`ember.data`):**
- `PrefabLibrary.reset(String prefab, List<?> components)` copies every settable field from a cached clean copy (a new instance with the prefab's values) into each given instance whose type is one of the prefab's components. `Transform` and other types are left as they are. It works on detached instances, because `World` queues component changes while a system runs.
- A pooled entity gets back exactly the components it had when it was freed.
- A hot reload of the prefab clears the cached copy.

**API sketch:**
```java
package dev.ember2d.kit.common;
public final class CommonKit { public static void register(ComponentRegistry registry); }
public final class HealthSystem extends EntitySystem {
    public HealthSystem(World world, EventBus events);
    public boolean damage(int target, int amount, int source, float knockbackX, float knockbackY);
    public void heal(int target, int amount);  public void kill(int target, int source);
    public boolean isInvulnerable(int target);
}
public final class HitSystem extends EntitySystem { public HitSystem(World world, HealthSystem health); }
public final class TriggerZoneSystem { public TriggerZoneSystem(World world, EventBus events); }   // no per-entity work
public final class LifetimeSystem extends EntitySystem { public LifetimeSystem(World world); }
public final class SpawnerSystem extends EntitySystem { public SpawnerSystem(World world, PrefabLibrary prefabs, EventBus events); }
public final class EntityPool {
    public EntityPool(World world, IntSupplier create, Consumer<List<Object>> reset, int warm);
    public static EntityPool ofPrefab(World world, PrefabLibrary prefabs, String prefab, int warm);
    public int obtain(float x, float y);  public void free(int entity);
    public int freeCount();  public int inUse();  public boolean owns(int entity);
}
public final class Despawn { public static void despawn(World world, int entity); }
public record Damaged(int target, int source, int amount, int healthLeft, float knockbackX, float knockbackY) { }
public record Died(int entity, int killer) { }
public record ZoneEntered(int zone, int entity, String name) { }
public record ZoneExited(int zone, int entity, String name) { }
public record Spawned(int spawner, int entity) { }
public record SpawnerFinished(int spawner) { }
```
`TriggerZoneSystem` is an `EntitySystem` with an empty family in phase `FIXED`, so it is added like the others and its subscriptions belong to the scene.

**Tests (headless):**
- Damage applies once per activation, and invulnerability blocks repeats for its window.
- Death fires once, and `destroyOnDeath` despawns.
- Teams and `restart()` work.
- `activeSeconds` turns a hitbox off, and `destroyOnHit` despawns it.
- Triggers: tag and team filters, and `once`.
- A spawner keeps its interval, burst, total and live cap, and waits for its zone.
- A pool reuses the same entity IDs and component instances after warm-up, with fields reset to the prefab values.
- `Lifetime` despawns, and a pooled entity goes back to its pool.

## Decisions in I-300
- **Pools reset instances, not entities:** `World` queues component changes while a system runs, so `PrefabLibrary.reset` works on the component instances a pool holds, not through `world.get`. This differs from the first sketch.
- **The `Pooled` marker stays on a free entity:** a free pooled entity keeps only its `Pooled` marker. Without it, `Despawn` would destroy an entity that is waiting in its pool.
- **When an activation is new:** `HitSystem` counts it as new when the hitbox turns on, `restart()` is called, or the entity was away (new, or back from a pool), so a reused entity never carries old targets. A target the activation already tried is not hit again later in that activation, even after the target's invulnerability ends. To hit again, call `restart()`.
- **`TriggerZoneSystem` is an `EntitySystem`:** it has a family but does no per-entity work, so it is added like the other systems and its subscriptions belong to the scene.
- **Not checked on a device:** no demo uses the kit yet. The shooter demo (I-340) will.

## Dialogue runner (I-420)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. It follows the S-03 findings: blade-ink 1.2.1, an `implementation` dependency of `kit-common`, with no Ink type in the API.

**Stories:**
- **Compiling:** Ink sources are compiled by inklecate into `.ink.json` assets. A story is loaded with `DialogueRunner.fromAsset(assets, id)`, where the asset is of type `json`, or with `DialogueRunner.fromJson(text)`.
- **The byte-order mark:** a leading UTF-8 byte-order mark is stripped. inklecate v1.2.1 writes none, but an editor may add one when the file is saved again.
- **Errors:** a broken story fails with `IllegalArgumentException` naming the asset.

**`DialogueRunner` API:**
- **Lines:**
  - `canContinue()`; `next()` advances one line and returns it without its trailing line break.
  - `line()`, `text()`, `speaker()`, `tags()` and `tag(key)` describe the current line.
  - `speaker()` comes from a `speaker:<name>` tag; otherwise from a `Name: text` prefix (a name of 1 to 24 letters, digits and spaces); otherwise it is empty.
  - `text()` is the line without that prefix.
  - `tag(key)` returns the value of `key:value` (null when the line has no such tag).
- **Choices:** `choices()` lists the current choice texts; `choose(index)` picks one. A bad index throws `IndexOutOfBoundsException`.
- **Ending:** `isFinished()` is true when the story can neither continue nor offer choices.
- **Jumping:** `jumpTo(path)` starts a knot or stitch, for example `elder` or `elder.after_quest`. An unknown path throws `IllegalArgumentException`.
- **Variables:** `variable(name)` and `setVariable(name, value)` (int, float, boolean, String). An unknown variable throws `IllegalArgumentException`.
- **External functions:** `bind(name, DialogueFunction)`, where `DialogueFunction` is `Object call(Object... args)`. Ink `EXTERNAL` functions run game code: give an item, set a flag. A bound function replaces the ink fallback.
- **Saving:**
  - `saveState()` and `loadState(json)` move the whole story state (position, variables, visit counts) as a string.
  - `save(SaveData out)` and `load(SaveData in)` store it under the key `ink`.
- **Ink runtime errors:** they become `IllegalStateException` with the Ink message.

**Gradle task for games (`ember.inklecate`):**
- A demo's `inkCompile` task compiles `assets-src/<game>/ink/*.ink` into `assets/ink/<name>.ink.json`.
- It runs only when the Gradle property `ember.inklecate` names the compiler. Otherwise it fails with a message that says how to get inklecate.
- It is not part of `build`, and the compiled files are committed.
- The task arrives with the first demo that has stories (I-450).

**Tests (headless, with the S-03 story compiled by inklecate):**
- Lines, speakers by tag and by prefix, `text()` and tags.
- Choices, and a bad index.
- Variables (read, write, unknown).
- Binding an external function.
- `jumpTo`, and an unknown path.
- The state round-trips through `SaveData`.
- `isFinished`.
- A leading byte-order mark is stripped.

## Decisions in I-420
- **The byte-order mark:** the S-03 claim that inklecate writes a byte-order mark was wrong; a check of the files showed none. The spike report is corrected. Stripping a leading mark stays as a precaution.
- **Choice texts:** they are fixed when the choices appear. A variable changed after that shows up only in later text (the test sets the variable first).
- **`fromAsset`:** it reads the `json` asset through libGDX and serialises it back to text for blade-ink. The round trip keeps the story intact, as the tests show.
- **The Android API guard:** blade-ink was added to its classpath, as gdx-ai was in I-330.
- **External function fallbacks (found in I-440):** blade-ink refuses to run a story with an unbound `EXTERNAL` function, even when the story has an Ink fallback, because fallbacks are off by default. `DialogueRunner` turns them on (`setAllowExternalFunctionFallbacks(true)`), so an unbound function runs its Ink fallback, as in Inky.
- **Not checked on a device:** the first run on the emulator is with the RPG demo (I-450).

## Non-goals
Stats systems, buffs/debuffs, AI (kits own AI hooks), inventory (RPG package).

## Acceptance criteria
- Damage applies once per activation; invulnerability blocks repeats for its window.
- Death event fires exactly once.
- Trigger once/repeat and filters correct.
- Pool reuses entities without allocation after warm-up.
- Spawner respects interval and live cap.
- Dialogue: branching story runs, tags and external functions work, state round-trips through a save.
- No dependency on any genre kit (`kit-platformer`, `kit-topdown`, `kit-novel`) or on `kit-bullets`.
