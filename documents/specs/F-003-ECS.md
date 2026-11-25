# F-003 — Entity Component System

- Status: Draft — expanded to template depth; becomes Ready when the API sketch is approved in I-040
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

## Risks/open questions
- Parent/child transforms will likely be wanted for weapons and attachments in Phase 4; revisit then.
