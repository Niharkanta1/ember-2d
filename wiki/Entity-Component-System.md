# Entity Component System

Package `ember.ecs` (`World`, `EntitySystem`, `Family`, `Transform`, `EntityRef`). Spec F-003, ADR-004.

## 1. The model
- **Entity:** an `int` handle with a generation counter, so a stale handle is detected.
- **Component:** a plain Java object with public fields. An entity has at most one component of each type.
- **System:** runs over the entities of one **family** (the entities that have certain components) in one **phase**.
- **World:** holds the entities, components and systems of one scene. Every scene has its own world.

There are no archetypes, bitsets or bytecode tricks. Each component type has one store, which keeps the ECS simple and fast enough for the demos. Bullets are the one exception: kit-bullets keeps them outside the ECS in plain arrays.

## 2. Entities and components
```java
World world = world();                          // inside a scene
int e = world.create();
Transform t = world.add(e, Transform.class);    // created by a registered factory
t.teleport(4f, 2f);
Patrol p = world.add(e, new Patrol());          // or add an instance
Patrol same = world.get(e, Patrol.class);       // null if absent
boolean has = world.has(e, Patrol.class);
world.remove(e, Patrol.class);
world.destroy(e);
world.isAlive(e);
```
- `add(entity, Type.class)` needs a factory. `Transform` is registered already. Register yours with `world.registerComponent(Patrol.class, Patrol::new)`, or through your game's `ComponentSetup`, which the prefab library and maps use too ([Prefabs and Data](Prefabs-and-Data)).
- Adding an instance always works. The RPG demo adds its long-lived `Inventory` object to each area's player this way.

### Names and tags
```java
world.setName(e, "player");    int player = world.findByName("player");   // World.NONE (-1) if none
world.tag(e, "player");        world.hasTag(e, "player");   world.findByTag("enemy", intArray);
```
Entities placed in Tiled get their object name automatically.

### `EntityRef`: references that survive
A handle may be reused after its entity is destroyed. Store references across frames as `EntityRef`:
```java
follow.target = world.ref(player);
int target = follow.target.entity(world);   // World.NONE once the player is destroyed
```
Prefab and Tiled object-reference properties fill `EntityRef` fields.

## 3. `Transform`
`Transform` holds:
- `position` (world units) and `rotation` (degrees);
- `scale`;
- the previous position and rotation, used for interpolation.

The engine copies current to previous before each fixed step. Sprites are drawn between the two using `time.alpha()`. `teleport(x, y)` sets both, so the entity does not slide visibly.

## 4. Systems
```java
public final class PatrolSystem extends EntitySystem {
    public PatrolSystem(World world) {
        super(Phase.FIXED, world.family(Transform.class, Patrol.class));
    }

    @Override
    protected void process(int entity, float delta) {
        Transform transform = world().get(entity, Transform.class);
        Patrol patrol = world().get(entity, Patrol.class);
        transform.position.x += patrol.direction * patrol.speed * delta;
    }

    @Override
    protected void begin() { }   // before the first entity of a run
    @Override
    protected void end() { }     // after the last one
}

world.addSystem(new PatrolSystem(world));
```
- **Families:** `world.family(A.class, B.class)` holds the entities that have both; `.exclude(C.class)` drops those with `C`. Families are cached and kept up to date. Call `family.entities()` and `family.size()` to use one directly, as the demos do for "all spawners" or "all doors".
- **Order:** by phase, then by the order you add systems. A world takes **one system per class**; use two classes if you need the same logic twice.
- **Skipping:** `system.enabled = false` skips a system.
- **Changing entities inside a system:** create, destroy, add and remove are queued and applied after the system. Outside systems they apply immediately.

## 5. Writing components
```java
/** Walks between two points (data: "Patrol": { "speed": 2.5, "range": 3 }). */
public final class Patrol {
    public float speed = 2f;   // settable from prefabs and Tiled
    public float range = 3f;
    public int direction = 1;  // runtime state: also public, documented as runtime
}
```
- **Fields:** public, non-static, with defaults. Settable from data: `int`, `long`, `float`, `double`, `boolean`, `String`, enums, `Color`, `Vector2`, `Rectangle` (also when `final`), `float[]` and `EntityRef`.
- **Logic:** keep it in systems, not in components.
- **Android:** register every component that data creates, so R8 keeps it in release builds.

## 6. Testing systems headless
```java
World world = new World(false);
TimeState time = new TimeState(1f / 60f);        // dev.ember2d.core.internal, for tests
world.addSystem(new PatrolSystem(world));
int e = world.create();
world.add(e, new Transform());
world.add(e, new Patrol());
world.runPhase(Phase.FIXED, time);
```
The engine's and kits' tests follow this pattern. For example `ParticlesTest` and `CommonKitTest` drive worlds without a window.

## 7. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| `Component type … is not registered in this World` | `add(e, Type.class)` without a factory | register it, or add an instance |
| `get` returns null with a "dead entity" warning | a raw handle outlived its entity | keep an `EntityRef` |
| "A system of class X is already registered" | two systems of one class | make two classes |
| An entity you destroyed still acts in the same system run | changes inside a system apply after it | check `isAlive`, or act in a later system |
| A crash after an event handler destroyed an entity | outside systems, destroy is immediate | check the entity again after code that can kill it |
