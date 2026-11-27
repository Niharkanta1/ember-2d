# Collision

Package `ember.collision` (`Collision`, `Collider`, `Actor`, `Solid`, `CollisionSystem`, `CollisionDebugSystem`, `MoveResult`, `RayHit`, `Direction`, `TileGrid`, `TriggerEntered`, `TriggerExited`, `ActorSquished`). Spec F-006, ADR-005.

Ember2D uses **kinematic** collision: axis-aligned boxes, exact contacts, no physics simulation. It is the right tool for platformers, top-down games and RPGs. Rigid bodies are planned as an optional Box2D module (`ember-box2d`, F-006B), which is not built yet.

## 1. Bodies
| Component | Fields | Meaning |
| --- | --- | --- |
| `Collider` | `box` (x, y, width, height relative to the position), `layer` (0–31), `mask` (layers it meets, default all), `trigger` | the shape |
| `Actor` | runtime contact info (`hitX`, `hitY`, `hitEntityX`, `hitEntityY`), `dropThroughTicks` | moves only through `Collision`; actors never block each other |
| `Solid` | `oneWay` | blocks actors; one-way blocks only from above |

A box `(-0.4375, 0, 0.875, 1.375)` is a body 0.875 wide and 1.375 tall, standing on its position: the feet anchor that every demo uses.

## 2. Setting up
```java
Collision collision = new Collision(world, context.events());
collision.addTileGrid(map.collision(), 0);                  // solid tiles on layer 0
// ... movement systems ...
world.addSystem(new CollisionSystem(world, collision));     // FIXED, after all movement: spatial hash + triggers
world.addSystem(new CollisionDebugSystem(world, collision, context.debug(), context.renderer()));   // F3 view
```
`setCellSize(size)` tunes the spatial hash (default fits tile-sized bodies).

## 3. Moving actors
```java
MoveResult r = collision.moveAndSlide(player, vx * delta, vy * delta);
if (r.hitY) vy = 0f;
boolean grounded = collision.isTouching(player, Direction.DOWN);
MoveResult hit = collision.moveAndCollide(dart, dx, dy);   // stops at the first contact
```
- `moveAndSlide` moves along x, then y, and stops each axis at the exact contact. Nothing tunnels at any speed.
- **One-way solids** (tiles with `oneWay`, or `Solid.oneWay`) block only actors coming from above. To drop through one, set `Actor.dropThroughTicks`; the platformer does this on down + jump.
- The kits' movement systems (`PlatformerSystem`, `TopDownSystem`, `PatrollerSystem`) do this for you.

### Moving solids
```java
collision.moveSolid(platform, dx, dy);
```
Actors standing on it ride along, and actors in the way are pushed. An actor that can't be pushed out publishes `ActorSquished(actor, solid)`. kit-platformer's `MovingPlatformSystem` uses this.

## 4. Triggers
A collider with `trigger = true` never blocks. `CollisionSystem` queues `TriggerEntered(trigger, actor)` and `TriggerExited(trigger, actor)`. "Still inside" is a query: `collision.inside(trigger, out)`.

kit-common's `TriggerZone` adds filters (tag, team, `once`) and its own `ZoneEntered` and `ZoneExited` events. Most games use that ([kit-common](Kit-Common)).

## 5. Queries
```java
IntArray out = new IntArray();
collision.overlapBox(area, mask, out);                 // entities overlapping a rectangle
collision.overlapPoint(x, y, mask, out);
int closest = collision.nearest(x, y, radius, mask);
RayHit hit = new RayHit();
if (collision.raycast(fromX, fromY, toX, toY, mask, hit)) { ... }   // tiles included
collision.raycastAll(fromX, fromY, toX, toY, mask, out);
Rectangle b = collision.bounds(entity, new Rectangle());
```
The platformer's `LevelSystem` checks enemies and spikes with `overlapBox` every step, because actors do not trigger each other and spikes should hurt again when invulnerability ends.

## 6. Layers and masks
- `Collider.layer` is a bit index (0–31) and `mask` is a bit set of the layers it meets.
- Tile grids are added on a layer (`addTileGrid(grid, 0)`).
- kit-bullets' wall bullets raycast against a wall mask (`bullets.setWallMask(1, collision)`, which means layer 0 only). So the shooter puts its actors on **layer 1**, or bullets would treat every actor as a wall.

## 7. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| Falling through the floor | tiles lack `solid`, or the grid wasn't added | set `solid` in the tileset; `addTileGrid` |
| Triggers never fire | no `CollisionSystem`, or it runs before movement | add it after all movement systems |
| Bullets stop at enemies without hurting them | actors on the wall layer | move actors to another layer |
| Two actors overlap | expected: actors never block actors | make one a `Solid`, or resolve with a query |
| A pushed actor vanishes | it was squished | listen to `ActorSquished` |
