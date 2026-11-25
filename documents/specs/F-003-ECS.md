# F-003 — Entity Component System

- Status: Ready (API approved in I-040, 2026-11-26; see "Decisions in I-040")
- Phase: 1
- Module / package: `ember-core` / `ember.ecs`
- Depends on: F-002 (phases), F-019 (events)
- Tasks: I-040
- Decision: ADR-004

## Goal
A small, predictable ECS that is easy to serialize, inspect and test, with safe entity references and interpolation-ready transforms.

## Scope
- `World` per scene: entities, component stores, systems.
- Entity handles with generation counters; `EntityRef` for stored references.
- Plain-object components; one store per component type.
- Systems with families and phases; ordered registration.
- Queued structural changes applied between systems.
- Built-in `Transform` component with previous/current state.
- Entity tags and names for lookup and dev tools.

## Non-goals
- Archetypes, bitset families, code generation, multithreaded systems.
- Hierarchical transforms (parent/child) — deferred until a demo needs them.
- Network replication.

## User-facing behaviour
- Games create entities from code or prefabs (F-010), add components, and register systems.
- Destroying an entity during a system's iteration is safe; it disappears before the next system runs.
- Holding an `EntityRef` to a destroyed entity is safe: `isAlive()` returns false.

## API sketch
```java
@PublicApi
public final class World {
    public int create();                                       // returns entity handle
    public void destroy(int entity);                           // queued
    public boolean isAlive(int entity);
    public <T> T add(int entity, Class<T> type);               // creates via no-arg ctor, queued
    public <T> T add(int entity, T component);                 // queued
    public <T> void remove(int entity, Class<T> type);         // queued
    public <T> T get(int entity, Class<T> type);               // null if absent
    public <T> boolean has(int entity, Class<T> type);
    public EntityRef ref(int entity);
    public Family family(Class<?>... required);                // cached
    public void addSystem(EntitySystem system);
    public void setName(int entity, String name);  public int findByName(String name);
}

@PublicApi
public final class EntityRef {                                 // value type: index + generation
    public boolean isAlive(World world);
    public int entity(World world);                            // -1 if stale
}

@PublicApi
public abstract class EntitySystem {
    // Phase is dev.ember2d.core.Phase { INPUT, FIXED, UPDATE, LATE, RENDER } (moved to ember.core in I-020: rule 1)
    protected EntitySystem(Phase phase, Family family);
    protected abstract void process(int entity, float delta);
    protected void begin() {}  protected void end() {}
    public boolean runsWhenPaused() { return false; }
    public boolean enabled = true;
}

@PublicApi
public final class Transform {
    public final Vector2 position = new Vector2(), previous = new Vector2();
    public float rotation, previousRotation;
    public final Vector2 scale = new Vector2(1, 1);
    public void interpolated(float alpha, Vector2 out);       // used by render systems
}
```
`Family` builder also supports `exclude(Class<?>...)`.

## Data model
- Entity handle: 32-bit int = 20-bit index + 12-bit generation (≈1M live entities, 4096 reuses per slot before wrap).
- Component stores: `ObjectMap<Class<?>, ComponentStore>`; each store is an array indexed by entity index.
- Components must have a public no-arg constructor and public or bean fields (for JSON prefabs and the inspector).

## Dependencies
`ember.core`, `ember.event`; libGDX `utils` collections and `math`.

## Lifecycle/resource ownership
- Each scene owns one `World`; it is cleared on scene exit.
- Components holding disposable resources must not own them; they reference assets by ID or by handles owned by `Assets`.
- Before each `FIXED` step, the ECS copies `position → previous` and `rotation → previousRotation` for all transforms.
- Structural change queue is flushed after each system and at the end of each phase.
- Persistent entities (e.g. the player across levels) are copied to the next world explicitly via `SceneManager.carry(entity)` (F-010).

## Error handling
- `get` on a dead entity returns null and logs once in dev mode.
- Adding a component type without a no-arg constructor throws with the class name.
- Registering two systems of the same class throws.

## Performance considerations
- Target: 5,000 entities with 3 systems at 60 fps on the reference machine, measured by the benchmark sample before any optimisation.
- Families are cached and updated incrementally on structural changes.

## Worked example
```java
World w = scene.world();
int player = w.create();
w.add(player, Transform.class).position.set(4, 2);
w.add(player, new Velocity());
w.addSystem(new EntitySystem(Phase.FIXED, w.family(Transform.class, Velocity.class)) {
    protected void process(int e, float dt) {
        Transform t = w.get(e, Transform.class); Velocity v = w.get(e, Velocity.class);
        t.position.mulAdd(v.value, dt);
    }
});
```

## Acceptance criteria
- Add/remove/get and family membership correct after flushes.
- Destroy during iteration is deferred; no system sees a half-destroyed entity.
- Stale `EntityRef` reports not alive after handle reuse.
- System order: by phase, then registration order.
- Interpolated position equals `previous + (position - previous) * alpha`.
- Scene exit leaves no entities, systems or subscriptions behind.
- Benchmark sample exists and its numbers are recorded.

## Tests
Unit tests for all criteria; benchmark as a JMH or simple timed test in a separate source set.

## Decisions in I-040
- **No reflection in `ember.ecs`** (rule 10): `add(entity, Class)` creates components through factories registered with `world.registerComponent(Type.class, Type::new)`; `Transform` is pre-registered; an unregistered type throws with the fix in the message. `add(entity, instance)` always works. The component registry in `ember.data` (I-130) will register prefab components automatically. This replaces "adding a type without a no-arg constructor throws".
- **Structural changes** (destroy, add, remove) apply immediately when no system is running, and are queued while a system runs, applied after that system and at the end of the phase. `create()` is always immediate. A queued destroy keeps the entity alive (and complete) until the system ends.
- **Pause and deltas**: `FIXED` gets the fixed step; `INPUT`, `UPDATE`, `LATE` the scaled delta; `RENDER` the unscaled delta. While paused, `UPDATE`/`LATE` systems are skipped unless `runsWhenPaused()`; those always get the unscaled delta. `INPUT` and `RENDER` systems keep running while paused.
- **Who runs a World**: `World.runPhase(phase, time)` is public; the scene manager runs the current scene's world every phase (wired in I-070).
- **Additions to the sketch**: `World(boolean devMode)`, `registerComponent`, `entityCount()`, `name(entity)`, tags (`tag`, `untag`, `hasTag`, `findByTag`), `clear()`, `World.NONE`; `EntitySystem.world()`, `phase()`, `family()`; `Family.entities()`/`size()`; `Transform.interpolatedRotation(alpha)` and `teleport(x, y)`.
- Handles: 20-bit index + 12-bit generation; index 0xFFFFF is never used, so no handle equals `NONE` (-1). Generations wrap after 4,096 reuses of one slot.
- An `EntityRef` is only meaningful for the world that created it.

## Benchmark (I-040)
`gradlew :ember-core:benchmarkEcs` (source set `ember-core/src/benchmark`, compiled by `check`, not run in CI): 5,000 entities with `Transform`, velocity and lifetime components; 3 systems (`FIXED` movement, `UPDATE` bounds bounce, `LATE` interpolation read plus destroy-on-expiry with respawn, about 10,000 entities replaced per run); 60 Hz frames through the real game loop; 2,000 warm-up and 6,000 measured frames.

| Date | Machine | Mean | Median | p99 | Max |
| --- | --- | --- | --- | --- | --- |
| 2026-11-26 | Developer desktop, Windows 11, Temurin 21.0.4 (run 1) | 0.192 ms | 0.179 ms | 0.386 ms | 2.093 ms |
| 2026-11-26 | same (run 2) | 0.244 ms | 0.241 ms | 0.340 ms | 0.445 ms |

About 1.5% of the 16.7 ms frame budget at 60 fps; no optimisation needed. A mid-range Android phone is measured later (TEST_PLAN performance checks).

## Risks/open questions
- Parent/child transforms will likely be wanted for weapons and attachments in Phase 4; revisit then.
