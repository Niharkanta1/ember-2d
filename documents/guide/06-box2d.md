# 06 — Rigid bodies with Box2D

This chapter adds rigid-body physics (crates that tumble, balls that bounce, physics puzzles) to a game with the optional `ember-box2d` module (F-006B, I-500). The example is the platformer demo's **Crates** scene (main menu → Crates).

> Status: draft for the developer's review at the Phase 6 gate. Not approved yet.

## 1. When to use it
Ember2D's own collision (`ember.collision`, chapter 02) moves characters precisely: platformer heroes, top-down players, enemies, bullets. Use Box2D only for things that should behave physically: stacks, piles, ragdoll props, rolling and bouncing objects.

The two systems are separate (ADR-005). A Box2D crate does not block a kinematic `Actor`, and an actor does not push crates. To connect them, give the actor a Box2D **sensor** body, which reports overlaps but pushes nothing.

## 2. Add the module
| File | Change |
| --- | --- |
| `core/build.gradle` | `api project(':ember-box2d')` |
| `desktop/build.gradle` | `runtimeOnly variantOf(libs.gdx.box2d.platform) { classifier('natives-desktop') }` |
| `android/build.gradle` | in the natives loop: `gdxNatives variantOf(libs.gdx.box2d.platform) { classifier("natives-${abi}") }` |
| `android/proguard-rules.pro` | `-keep class com.badlogic.gdx.physics.box2d.** { *; }` (Box2D's native code calls back by name) |
| your `ComponentSetup` | `Box2dKit.register(registry);` (registers `RigidBody`) |

The platformer demo's build files show all of these.

## 3. Units
One Box2D metre is **one world unit**, which is one tile at the default 16 pixels per unit. That is the size Box2D is tuned for, so there is no conversion factor. A 20-pixel crate is 1.25 units.

## 4. The scene
```java
// enter()
Box2dSystem physics = new Box2dSystem(world, context.events(), context.time(), 0f, -20f);   // gravity
physics.addTileGrid(map.collision());        // the Tiled map's solid tiles become static boxes
world.addSystem(physics);                     // FIXED phase
world.addSystem(sprites);
world.addSystem(new Box2dDebugSystem(world, physics, context.debug()));   // F3 shows the bodies

// exit()
physics.dispose();                            // destroys the Box2D world and every body
```
- **Gravity:** units per second²; the Crates scene uses −20 for a snappier fall than Earth's −9.8.
- **Tiles:** solid tiles become static boxes, one per run of solid cells in a row. One-way tiles are not supported in Box2D.

## 5. Bodies
A body is a `RigidBody` component next to a `Transform`. The system makes the Box2D body on the next fixed step, at the entity's position and rotation:
```java
int e = world.create();
world.add(e, new Transform()).teleport(x, y);
Sprite sprite = world.add(e, new Sprite());
sprite.region = crate;
sprite.origin.set(14, 12);                    // the image centre: bodies rotate around their position
RigidBody body = world.add(e, new RigidBody());
body.width = 20f / 16f;
body.height = 20f / 16f;
body.friction = 0.6f;
```
Or in a prefab:
```json
"RigidBody": { "type": "DYNAMIC", "shape": "CIRCLE", "radius": 0.5, "restitution": 0.6 }
```
**Fields** (read once, when the body is made):

| Fields | Meaning |
| --- | --- |
| `type` | `STATIC`, `KINEMATIC` or `DYNAMIC` |
| `shape` | `BOX` or `CIRCLE` |
| `width`, `height`, `radius`, `offset` | the shape's size and its centre relative to the position |
| `density`, `friction`, `restitution` | the material |
| `sensor` | reports overlaps, pushes nothing |
| `fixedRotation`, `bullet` | never rotate; continuous collision for fast bodies |
| `category`, `mask`, `group` | collision filtering |
| `velocityX`, `velocityY`, `angularVelocity` | the start velocity |

**Methods** (on a body that exists):
- `applyImpulse(x, y)`, `applyForce(x, y)`, `applyTorque(t)`;
- `setVelocity(x, y)`, `velocity(out)`, `setAngularVelocity(deg)`;
- `teleport(x, y, degrees)`;
- `isAwake()`, `hasBody()`.

The system writes dynamic and kinematic bodies back into `Transform` (position and rotation in degrees), so sprites, particles and cameras follow them and interpolate smoothly.

**Removing a body:** destroy the entity, or remove its `RigidBody`.

## 6. Contacts and sensors
```java
context.events().on(ContactBegan.class, e -> {
    if (e.involves(ball)) audio.play("sfx.bounce");
});
context.events().on(TriggerEntered.class, e -> { /* a sensor body: e.trigger() overlaps e.actor() */ });
```
- **Solid bodies:** `ContactBegan` and `ContactEnded`.
- **Sensors:** the same `TriggerEntered` and `TriggerExited` events as kinematic triggers, so code written for triggers works with both.
- **Delivery:** events are delivered after the fixed step, and never name an entity that has already been removed.
- **Tile bodies:** they send no events.

## 7. Filtering
```java
static final int CRATES = 0b01, GHOSTS = 0b10;
crate.category = CRATES;
ghost.category = GHOSTS;
ghost.mask = ~CRATES & 0xFFFF;   // ghosts pass through crates
```

## 8. The Crates scene
`demos/demo-platformer/.../CratesScene.java`:
- a Tiled arena (`maps/crates.tmx`) with stacks of 7, 5 and 5 crates;
- a click or tap on open space drops a crate (up to 80);
- **Reset** (R, or Y on a pad) rebuilds the stacks;
- **Back** returns to the menu.

Stacks come to rest and sleep (dark green with F3). The scene was checked on desktop and in the minified (R8) release APK on the Android emulator.

## 9. Checklist
- [ ] Natives on desktop and Android, and the R8 keep rule.
- [ ] `Box2dKit.register` in your `ComponentSetup`.
- [ ] `physics.dispose()` in `exit()`.
- [ ] Sprites of rotating bodies have their origin at the centre.
- [ ] Shapes match the visible art, not the image size (art often has transparent padding).

Reference: spec `specs/F-006B-BOX2D.md`.
