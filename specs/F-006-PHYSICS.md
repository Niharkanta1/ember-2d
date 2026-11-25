# F-006 — Physics: Kinematic Collision

- Status: Draft
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

## Acceptance criteria
- Exact stop at contact; slide along walls; no tunnelling through 1-tile walls at the max speed configured for demos.
- No snagging when sliding across adjacent tiles.
- One-way: pass from below, land from above, drop-through works.
- Moving platform carries a standing actor and pushes a blocking actor.
- Trigger enter/stay/exit correct, including actor destroyed while inside.
- Layer/mask filtering; ray and overlap queries return correct entities in order.
- 500 actors + 1 tile grid within the frame budget on the reference machine (measured).

## Risks/open questions
- Sub-pixel positions: store float positions, collide on float boxes; pixel-snap only in rendering. Confirm in S-02.
