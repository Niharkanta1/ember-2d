# F-006 — Physics: Kinematic Collision

- Status: Ready (I-120, approved 2026-11-26; see "Design (I-120)" and "Decisions in I-120")
- Phase: 2
- Module / package: `ember-core` / `ember.collision`
- Depends on: F-002, F-003, F-019
- Tasks: S-02, I-120
- Decision: ADR-005 (supersedes the original Box2D-based F-006; Box2D is now F-006B)

## Goal
Precise, predictable collision and movement shared by every genre kit, without a rigid-body solver.

## Scope
- **Colliders:** AABB (required), circle (optional), with offset; `layer`, `mask`, `trigger` flag.
- **Actors:** moved only through the collision API; never overlap solids.
- **Solids:** static boxes, tile grids (from F-009), and moving solids.
- **Movement:** `moveAndCollide(actor, dx, dy)` moves axis by axis and stops at contact, returning hit info; `moveAndSlide` keeps the remaining movement along the contact surface.
- **One-way solids:** block only from one side (default: from above); actors can request a drop-through for N ticks.
- **Riders:** moving solids carry actors standing on them and push actors in their path.
- **Contact queries:** `isTouching(actor, direction)` (e.g. down = grounded), last-move hits.
- **Spatial queries:** overlap box/circle, point, ray (first hit and all hits), nearest-in-radius; backed by a spatial hash.
- **Triggers:** enter/stay/exit computed each fixed step and published as queued events (F-019).
- **Units:** world units; `pixelsPerUnit` from `EngineConfig`; one `Units` helper.
- **Debug:** collider and ray drawing via the debug hook.

## Non-goals
- Rotation of colliders, polygons, slopes (slopes are a possible later extension), joints, mass, restitution, continuous collision for fast bodies (use rays or sub-steps instead). For these, use `ember-box2d` (F-006B).

## API sketch
```java
@PublicApi public final class Collider { public final Rectangle box = new Rectangle(); public int layer, mask = ~0; public boolean trigger; }
@PublicApi public final class Solid { public boolean oneWay; public final Vector2 oneWayNormal = new Vector2(0, 1); }
@PublicApi public final class Actor { public int dropThroughTicks; }
@PublicApi public interface Collision {
    MoveResult moveAndCollide(int actor, float dx, float dy);
    MoveResult moveAndSlide(int actor, float dx, float dy);
    void moveSolid(int solid, float dx, float dy);           // carries riders, pushes actors
    boolean isTouching(int actor, Direction dir);
    int overlapBox(Rectangle area, int mask, IntArray out);
    RayHit raycast(Vector2 from, Vector2 to, int mask);
    void addTileGrid(TileGrid grid);                         // from F-009
}
@PublicApi public record MoveResult(boolean hitX, boolean hitY, int otherX, int otherY, Vector2 normal) {}
```

## Design (I-120)
Status: Ready (approved 2026-11-26). Built on spike S-02 (`spikes/S-02-collision-tiled.md`): float positions, contact tolerance `EPS` = 1e-4 world units, axis by axis, steps of at most half a cell; tiles come from a grid lookup, and boxes are used only for solids that are not tiles.

**Components (`ember.collision`).** Positions come from `Transform.position`. A collider's world box is `position + box.x/y` with size `box.width/height`, so a box `(-0.375, 0, 0.75, 1.5)` is a 0.75 × 1.5 body standing on its position (its feet).
- `Collider`: `box` (offset and size), `layer` (bit index 0–31, default 0), `mask` (bit set of layers it collides with or detects, default all), `trigger` (true: an area that reports actors entering and leaving, never blocks).
- `Actor`: a body moved only through the collision API.
  - `dropThroughTicks`: one-way solids are ignored while above 0; counts down per move.
  - Read-only contacts of the last move: `hitX`, `hitY`, `hitEntityX`, `hitEntityY` (the solid, or `World.NONE` for tiles).
  - Actors never block each other.
- `Solid`: a static or moving box that blocks actors. `oneWay`: blocks only from above. Move it only with `moveSolid` so it carries riders and pushes actors.
- **Tile grids:** `TileGrid`s from F-009 added with `addTileGrid(grid, layer)`. Solid tiles block from every side, one-way tiles from above.

**`Collision` (the service, one per scene world).**
- **Setup:** `new Collision(world, events)` in the scene's `enter`.
- **`CollisionSystem`** (`FIXED`) is registered **after** the game's movement systems. Each step it:
  1. refreshes the spatial hash for entities created, destroyed or moved outside the API;
  2. updates triggers.
- **First use:** the hash is also built on first use, so moves in the first step already see everything spawned in `enter`.
- **Spatial hash:** a uniform grid with cells of 2 world units (`Collision.setCellSize`, before first use). It indexes the box colliders: solids, actors and triggers. Tiles use their grid directly.
- **`moveAndSlide(actor, dx, dy)`:** moves x then y; a blocked axis stops at exact contact and the other axis continues. This is the platformer move.
- **`moveAndCollide(actor, dx, dy)`:** moves along the vector in steps of at most half a cell. At the first contact on either axis it stops entirely (projectiles, dashes).
- **Results:** both return the shared `MoveResult` (`hitX`, `hitY`, `entityX`, `entityY`, `normalX`, `normalY`). It is valid until the next move; nothing is allocated per move. The same contacts are stored on the `Actor`.
- **`isTouching(actor, Direction)`:** probes `EPS` in that direction; `DOWN` means grounded. One-way solids count only for `DOWN`.
- **`moveSolid(solid, dx, dy)`:** moves the solid in steps of at most half a cell.
  - Actors **riding** it (standing on its top, overlapping horizontally) move with it.
  - Actors it would overlap are **pushed** with `moveAndSlide`.
  - An actor that cannot be pushed out (blocked by another solid) is **squished**: it stays where it is and a queued `ActorSquished(actor, solid)` event lets the game decide (kill, respawn).
  - One-way solids carry riders but push only upwards.
- **Triggers:**
  - Each step, `CollisionSystem` compares each trigger's overlapping actors (filtered by the trigger's mask) with the last step.
  - It publishes queued `TriggerEntered(trigger, actor)` and `TriggerExited(trigger, actor)`.
  - An actor destroyed inside a trigger, or a destroyed trigger, gives `TriggerExited` on the next step.
  - "Stay" is a query, `inside(trigger, IntArray out)`, not an event per step.
- **Queries** (filtered by mask; results sorted by entity for determinism):
  - `overlapBox(area, mask, out)` and `overlapPoint(x, y, mask, out)`: colliders including triggers
  - `nearest(x, y, radius, mask)`
  - `raycast(fromX, fromY, toX, toY, mask, RayHit out)`: the first hit, including tiles (grid walk); returns true on a hit
  - `raycastAll(..., IntArray out)`: entities sorted by distance
- **Debug:** `CollisionDebugSystem` (`RENDER`) draws, through the debug overlay (F3), while it is visible:
  - solid tiles near the camera, solids, actors, triggers
  - each actor's last contacts
- **Excluded from I-120:** circle colliders, slopes, rotated colliders (F-006 non-goals stay). Named layers come with project data later; prefabs use the bit index.

## API sketch (I-120)
```java
package dev.ember2d.collision;

@PublicApi public final class Collider { public final Rectangle box = new Rectangle(); public int layer; public int mask = ~0; public boolean trigger; }
@PublicApi public final class Actor { public int dropThroughTicks; public boolean hitX, hitY; public int hitEntityX = World.NONE, hitEntityY = World.NONE; }
@PublicApi public final class Solid { public boolean oneWay; }
@PublicApi public enum Direction { LEFT, RIGHT, UP, DOWN }
@PublicApi public final class MoveResult { public boolean hitX, hitY; public int entityX, entityY; public float normalX, normalY; }
@PublicApi public final class RayHit { public int entity; public boolean tile; public float x, y, distance, normalX, normalY; }
@PublicApi public record TriggerEntered(int trigger, int actor) { }
@PublicApi public record TriggerExited(int trigger, int actor) { }
@PublicApi public record ActorSquished(int actor, int solid) { }

@PublicApi public final class Collision {
    public static final float EPS = 1e-4f;
    public Collision(World world, EventBus events);
    public void setCellSize(float size);
    public void addTileGrid(TileGrid grid, int layer);
    public MoveResult moveAndSlide(int actor, float dx, float dy);
    public MoveResult moveAndCollide(int actor, float dx, float dy);
    public void moveSolid(int solid, float dx, float dy);
    public boolean isTouching(int actor, Direction direction);
    public int overlapBox(Rectangle area, int mask, IntArray out);        // returns count
    public int overlapPoint(float x, float y, int mask, IntArray out);
    public int nearest(float x, float y, float radius, int mask);         // World.NONE if none
    public boolean raycast(float fromX, float fromY, float toX, float toY, int mask, RayHit out);
    public int raycastAll(float fromX, float fromY, float toX, float toY, int mask, IntArray out);
    public int inside(int trigger, IntArray out);
    public Rectangle bounds(int entity, Rectangle out);                   // collider box in world units
}
@PublicApi public final class CollisionSystem extends EntitySystem { public CollisionSystem(World world, Collision collision); }   // FIXED
@PublicApi public final class CollisionDebugSystem extends EntitySystem { public CollisionDebugSystem(World w, Collision c, DebugOverlay d, Renderer r); }   // RENDER
```
`ember.collision` may depend on `core`, `event`, `ecs` (ARCHITECTURE tier 3). For the debug view it also needs `debug` and `render`, which are tier-3 siblings; this needs an allowed-dependency entry, `collision -> debug, render`, in the ArchUnit rules (see the decisions).

**Prefabs:** `Collider`, `Actor` and `Solid` are registered as engine components next to `Transform` and `Sprite`, so they are usable in JSON prefabs and Tiled.

## Decisions in I-120
The defaults were approved as proposed, including the architecture change (`ember.collision` may use `ember.debug` and `ember.render`; ARCHITECTURE rule 3 and the ArchUnit map are updated). Details settled while implementing:
- **Exact sweeps:**
  - Each axis of a move is swept exactly against tile columns or rows and solid boxes; it does not test the target box in sub-steps as S-02 did. Nothing tunnels at any speed, and `moveAndSlide` needs no sub-steps.
  - `moveAndCollide` and `moveSolid` still advance in steps of at most half a cell (half the smallest tile cell, at most half the hash cell), so they stop or carry at the right point along the path.
- **Two spatial hashes:** one for solids, one for everything else (actors, triggers).
  - Sweeps search only the solids hash. With one shared hash, the 500-actor benchmark took 1.4 ms per step, because each sweep also returned dozens of actors; with two it takes 0.18 ms.
  - Queries merge both hashes, sorted by entity.
- **Rays and contacts:**
  - Rays ignore triggers and one-way tiles and solids.
  - `raycastAll` returns entities only (no tiles).
  - `isTouching(UP / LEFT / RIGHT)` never counts one-way solids or tiles.
- **Moving solids:** they are not blocked by tiles or other solids; the game keeps them on their paths. Being carried or pushed does not count down `Actor.dropThroughTicks`.
- **Prefabs:** `Collider`, `Actor` and `Solid` are registered engine components, next to `Transform` and `Sprite`.
- **R8 merged system classes:** in the minified build, R8 merged look-alike `EntitySystem` subclasses (the demo's patrol, player and platform systems) into one class, and `World.addSystem` then rejected the second "duplicate" at start-up.
  - **Fix:** `KeepRules` now always writes `-keepnames` for every `EntitySystem` and `Scene` subclass.
  - **Side effect:** engine log lines in release builds show the real class names. This closes S-04 follow-up 3 early (the mapping file stays with I-255).
- **Demo:**
  - `PlayerControl` and `Patrol` became platformer bodies with gravity. The tap-to-move from I-105 is replaced by `jump` (Space, Z, Up, W, tap).
  - New `MovingPlatform` with its `MovingPlatformSystem`, and the prefabs `platform/moving` and `zone/tint`, placed in the map.
  - A squish respawns the player, and `CollisionDebugSystem` is on F3.

## Worked example (I-120)
```java
// enter():
Collision collision = new Collision(world(), ctx.events());
collision.addTileGrid(map.collision(), 0);
world().addSystem(new PlayerSystem(world(), collision, ctx.input()));   // FIXED: gravity, input, moveAndSlide
world().addSystem(new CollisionSystem(world(), collision));             // after movement: hash + triggers
ctx.events().on(TriggerEntered.class, e -> LOG.info("entered " + world().name(e.trigger())));

// PlayerSystem.process (FIXED):
body.vy -= GRAVITY * dt;
MoveResult r = collision.moveAndSlide(player, body.vx * dt, body.vy * dt);
if (r.hitY) body.vy = 0f;
if (input.justPressed("jump") && collision.isTouching(player, Direction.DOWN)) body.vy = JUMP_SPEED;
```

## Tests (I-120)
- **The S-02 scenarios, now on `Collision`:**
  - seams in a run of 20 tiles, and wall slide
  - exact contact with floor, wall and ceiling
  - no tunnelling at 10 cells per move
  - one-way from below, from above, drop-through, and sideways
  - moving platform carries a rider and pushes a blocker
  - a squish publishes an event
- **New:**
  - `moveAndCollide` stops both axes
  - solid boxes alongside the tile grid
  - layer and mask filtering
  - `isTouching` in each direction
- **Triggers:** enter, exit, actor destroyed inside, trigger destroyed, mask.
- **Queries:**
  - overlap box and point, sorted by entity
  - ray: first hit on a box or a tile, with distance and normal, and `raycastAll` order
  - `nearest`
- **Hash:** entities created and destroyed between steps are picked up.
- **Performance:** 500 actors with gravity on a tile grid plus 20 solid boxes; measure ms per fixed step and fail above 2 ms on the desktop.
- **Demo (desktop check by you, Android emulator by me):** the demo becomes a small platformer.
  - The player has gravity, walks with `move`, and jumps with a new `jump` action (Space, Up, W, tap). It stands on tiles and one-way planks, and drops through with Down + jump.
  - A moving platform (a prefab placed in Tiled) carries the player.
  - The patrollers are actors that turn at walls.
  - A Tiled trigger zone logs enter and exit and tints the player while inside.
  - F3 shows the colliders.

## Acceptance criteria
- Exact stop at contact; slide along walls; no tunnelling through 1-tile walls at the max speed configured for demos.
- No snagging when sliding across adjacent tiles.
- One-way: pass from below, land from above, drop-through works.
- Moving platform carries a standing actor and pushes a blocking actor.
- Trigger enter/stay/exit correct, including actor destroyed while inside.
- Layer/mask filtering; ray and overlap queries return correct entities in order.
- 500 actors + 1 tile grid within the frame budget on the reference machine (measured).

## Risks/open questions
- Sub-pixel positions: store float positions, collide on float boxes; pixel-snap only in rendering. Confirmed by S-02.
- **Pushes and rider order:** pushing actors that stand on each other, and several moving solids carrying the same actor in one step, are resolved one by one in system order. The result is order-dependent but deterministic. Revisit if the demos show problems.
