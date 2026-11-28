# F-006B — Optional Box2D Module

- Status: Ready (expanded and approved in I-500, 2026-11-28; the developer approved the gdx-box2d download the same day)
- Phase: 6 (only when a demo or game needs rigid bodies)
- Module / package: `ember-box2d` / `dev.ember2d.box2d`
- Depends on: F-003 (ECS, fixed step), F-019 (events), F-002 (time), F-014 (debug overlay)
- Tasks: I-500
- Decision: ADR-005, ADR-013

## Goal
Let games that want rigid-body physics (crates, ragdoll props, physics puzzles) use Box2D inside Ember2D's ECS and fixed step, without affecting games that don't.

## Scope
**Library:** the classic libGDX Box2D extension (v2), `com.badlogicgames.gdx:gdx-box2d:1.14.2`. Its natives are `gdx-box2d-platform` for desktop and the three Android ABIs. The Box2D v3 bindings stay out until they leave incubation.

**Units:** one Box2D metre is one world unit (one tile at the default 16 pixels per unit). That is the size range Box2D is tuned for (0.1–10 m), so no conversion factor exists. `pixelsPerUnit` already turns world units into pixels for drawing.

**`RigidBody`** (component, prefab- and Tiled-friendly, registered by `Box2dKit.register(registry)`):

| Field | Default | Meaning |
| --- | --- | --- |
| `type` | `DYNAMIC` | `STATIC`, `KINEMATIC` or `DYNAMIC` (`BodyType` enum) |
| `shape` | `BOX` | `BOX` or `CIRCLE` (`BodyShape` enum) |
| `width`, `height` | 1, 1 | box size in world units |
| `radius` | 0.5 | circle radius |
| `offset` | (0, 0) | shape centre relative to the entity's position |
| `density`, `friction`, `restitution` | 1, 0.4, 0 | material |
| `sensor` | false | overlaps only, no response |
| `fixedRotation`, `bullet` | false, false | no rotation; continuous collision for fast bodies |
| `gravityScale`, `linearDamping`, `angularDamping` | 1, 0, 0 | |
| `category`, `mask`, `group` | 1, 0xFFFF, 0 | Box2D collision filter |
| `velocityX`, `velocityY`, `angularVelocity` | 0 | the start velocity (applied once, when the body is made) |

**Methods** (no Box2D types):
- `applyImpulse(x, y)`, `applyForce(x, y)`, `applyTorque(t)`;
- `setVelocity(x, y)`, `velocity(Vector2 out)`, `angularVelocity()`, `setAngularVelocity(w)`;
- `teleport(x, y, degrees)`, which also sets the `Transform` without interpolation;
- `isAwake()`, `hasBody()`.

Field changes after the body exists are **not** applied. Use the methods, or remove and add the component.

**`Box2dSystem`** (FIXED phase, one per world):
- **Constructor:** `new Box2dSystem(world, events, gravityX, gravityY)`; `Box2D.init()` runs once.
- **Every fixed step:**
  1. Make bodies for new entities that have `Transform` + `RigidBody`, placed at the `Transform` (position and rotation in degrees).
  2. Destroy bodies whose entity is gone or lost its `RigidBody`.
  3. Store `Transform.previous` for interpolation.
  4. Step Box2D with `time.fixedDelta()` (velocity 8, position 3 iterations).
  5. Write dynamic and kinematic bodies back into `Transform` (position and rotation). Static bodies are never moved by the step.
- **`dispose()`** destroys the Box2D world. Call it in the scene's `exit()`. Bodies never outlive the system.
- **`bodyCount()`** is for tests and the debug overlay.
- **Contacts** are queued and delivered after the step, like F-006:
  - a sensor overlap → `TriggerEntered(trigger, other)` / `TriggerExited(trigger, other)`, the same events as kinematic triggers;
  - a solid contact → `ContactBegan(a, b)` / `ContactEnded(a, b)` (new, in `dev.ember2d.box2d`).
  - Events for destroyed entities are not sent.

**`Box2dDebugSystem`** (RENDER phase): while the debug overlay is visible, draws every fixture through `DebugOverlay` (boxes as rotated outlines, circles with a radius line): static grey, kinematic blue, dynamic green (sleeping: dark green), sensors yellow.

**Tile collision:** `Box2dSystem.addTileGrid(TileGrid grid)` adds one static box per row run of solid cells, so the tile map's floor and walls stop bodies. One-way tiles are not supported in Box2D (a non-goal).

**ADR-013:** Box2D classes appear only in non-public members (fields, constructors' internals, package-private methods). The module's architecture test runs the public-API rule, which rejects `com.badlogic.gdx.physics.box2d` types in public signatures.

**Build:**
- `ember-box2d` depends on `ember-core` and `gdx-box2d`.
- A game's `core` adds `api project(':ember-box2d')`.
- Desktop gets the desktop natives through `ember-box2d`'s runtime dependencies.
- Android launchers add the `gdx-box2d-platform` natives per ABI to `copyAndroidNatives`.
- `tools/android-api-check` includes `ember-box2d`'s sources.
- R8: `RigidBody` is kept by the generated keep rules; Box2D's JNI callbacks need `-keep class com.badlogic.gdx.physics.box2d.** { *; }` in the game's `proguard-rules.pro`.

**Demo:** a "Crates" scene in the platformer demo (main menu → Crates).
- A Tiled arena (`maps/crates.tmx`) with stacks of 7, 5 and 5 crates; the middle stack is as tall as the 180-pixel view allows. The crate is the Pixel Adventure box image, with a 1.25-unit body matching its 20-pixel art.
- A click or tap on open space drops a crate at the pointer (at most 80).
- **Reset** (or R) rebuilds the stacks; **Back** (Escape, or Back on Android) returns to the menu.
- A panel in the top-right corner shows the crate count, and F3 draws the bodies.

The 10-crate stack criterion is covered by a unit test.

## Non-goals
- A shared abstraction over Box2D and kinematic collision; the two APIs stay separate (ADR-005).
- Kinematic actors and Box2D bodies colliding with each other. To bridge them, give the actor a Box2D sensor (the actor still moves with `Collision`).
- Joints, polygons and chains beyond boxes, circles and tile runs, one-way platforms, ray casts and queries. These are candidates for later, when a game needs them.
- Box2D v3.

## API sketch
```java
package dev.ember2d.box2d;
public enum BodyType { STATIC, KINEMATIC, DYNAMIC }
public enum BodyShape { BOX, CIRCLE }
public final class RigidBody {
    public BodyType type = BodyType.DYNAMIC; public BodyShape shape = BodyShape.BOX;
    public float width = 1f, height = 1f, radius = 0.5f; public final Vector2 offset = new Vector2();
    public float density = 1f, friction = 0.4f, restitution, gravityScale = 1f, linearDamping, angularDamping;
    public boolean sensor, fixedRotation, bullet;
    public int category = 1, mask = 0xFFFF, group;
    public float velocityX, velocityY, angularVelocity;
    public void applyImpulse(float x, float y); public void applyForce(float x, float y); public void applyTorque(float t);
    public void setVelocity(float x, float y); public Vector2 velocity(Vector2 out);
    public float angularVelocity(); public void setAngularVelocity(float w);
    public void teleport(float x, float y, float degrees); public boolean isAwake(); public boolean hasBody();
}
public final class Box2dSystem extends EntitySystem implements Disposable {
    public Box2dSystem(World world, EventBus events, Time time, float gravityX, float gravityY);
    public void addTileGrid(TileGrid grid);
    public int bodyCount();
    public void dispose();
}
public final class Box2dDebugSystem extends EntitySystem {
    public Box2dDebugSystem(World world, Box2dSystem physics, DebugOverlay debug);
}
public record ContactBegan(int a, int b) { }
public record ContactEnded(int a, int b) { }
public final class Box2dKit { public static void register(ComponentRegistry registry); }
```

## Acceptance criteria
- Body lifecycle follows entity lifecycle; no leaked bodies after scene exit (body count 0 after `dispose`, and after destroying all entities).
- Contacts and sensors produce events; filters work.
- A stack of 10 crates comes to rest (all bodies asleep) within 5 s at the 60 Hz fixed step and stays standing.
- `ember-core` builds and runs without this module on the classpath (the engine has no dependency on it).
- The platformer demo's Crates scene runs on desktop and on the Android emulator (release, minified).

## Tests
- Headless with the desktop natives:
  - body creation from `Transform` and fields;
  - removal on destroy and on losing `RigidBody`;
  - `dispose`;
  - gravity moves a dynamic body and writes `Transform` and `previous`;
  - static bodies stay put;
  - sensor → `TriggerEntered`/`Exited`;
  - contact → `ContactBegan`/`Ended`;
  - filters (mask excludes a category);
  - impulse and velocity;
  - the stack test (10 crates asleep within 300 steps, every crate within 0.05 of its column);
  - tile runs.
- Architecture: no Box2D types in the public API; the module rules.
