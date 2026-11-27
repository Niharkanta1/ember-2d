# F-024 — Bullet Kit (Projectiles and Bullet Hell)

- Status: Ready (I-325, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Design (I-325)")
- Phase: 4 (with the shooter)
- Module / package: `kit-bullets`
- Depends on: F-002, F-004, F-006 (spatial grid), F-008, F-019, F-021 (damage)
- Tasks: I-325
- Decision: ADR-020

## Goal
Thousands of bullets with predictable patterns and cheap collision, usable by top-down shooters, bullet-hell games and any other game that fires projectiles.

## Scope
**Runtime**
- `BulletWorld` per scene: bullets stored in parallel arrays (no per-bullet objects or entities).
- Bullet data: position, velocity, acceleration, angular velocity, speed curve, lifetime, radius, sprite (atlas region index or animation), owner team, damage, flags (destroy on hit, piercing, grazeable).
- Update in the `fixed` phase; removal of off-screen/expired bullets with swap-remove.
- Rendering in one pass from a single atlas, with rotation to velocity optional.
- Capacity set per scene (default 4,096); exceeding it drops the oldest bullets and logs once.

**Collision**
- Circle vs circle only.
- Enemy bullets vs player hitbox (small circle separate from the sprite) and graze radius → `Grazed` event.
- Player bullets vs enemy hurtboxes via a uniform grid rebuilt each fixed step.
- Bullet vs solid tiles: optional per bullet type (top-down shooter yes, shoot-'em-up usually no).
- Hits published as queued `BulletHit` events; damage applied by `kit-common`.
- Bullet clear (on bomb or player death) with optional conversion to score pickups.

**Patterns (data, hot-reloadable)**
- Emitters: single, ring (n bullets), arc (spread), spiral (rotating ring), aimed (at target), random spread, wave/sine.
- Sequencing: repeat, interval, delay, angle step per repeat, speed step, nested emitters (bullets that spawn bullets).
- Pattern files in JSON, referenced by asset ID; enemies and bosses run patterns from a timeline or a state machine (F-002).

```json
{ "version": 1, "id": "boss.spiral",
  "emit": { "type": "ring", "count": 12, "speed": 90, "bullet": "orb_red",
            "repeat": 40, "interval": 0.1, "angleStep": 7 } }
```

**Shoot-'em-up support**
- Scrolling camera mode and parallax backgrounds (through F-004 camera and layers), player focus mode (slow movement, visible hitbox), lives/bombs helpers in the demo rather than the kit.

## Design (I-325)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.bullets`; it depends on `ember-core` and `kit-common` only.

**Units:** world units (tiles), seconds and degrees. Angle 0 points right, and angles grow counter-clockwise, so 90 is up and 270 is down. (The example above uses pixel speeds; patterns use world units.)

**Bullet types** come from a JSON asset (type `json`), parsed once by `BulletTypes.parse(json, file)`:
```json
{ "version": 1, "types": {
    "orb_red":  { "region": "tex.bullets/orb_red", "radius": 0.15, "damage": 1, "lifetime": 8 },
    "pellet":   { "region": "tex.pellet", "radius": 0.1, "walls": true, "rotate": true },
    "laser":    { "region": "tex.laser", "radius": 0.12, "pierce": true, "graze": false } } }
```
- **Fields:** `region` (required, any `assets.region` ID), `radius` 0.15, `damage` 1, `lifetime` 10, `walls` false (dies on solid tiles and walls), `pierce` false (survives hits), `graze` true, `rotate` false (drawn rotated to its velocity), `scale` 1.
- **Errors:** unknown fields and bad values are reported together, naming the file and the type.

**`BulletWorld(capacity, types)`:** the bullets as parallel arrays, with no object per bullet.
- **What each bullet holds:** position, velocity, acceleration along its velocity, turn rate (degrees per second), age and lifetime, type, team, damage, flags (`grazed`), and an optional child pattern with its delay.
- **Spawning:** `spawn(type, x, y, angle, speed, team)` returns the slot. Setters then adjust the bullet just spawned (`accel`, `turn`, `child`).
- **When full:** the capacity (default 4096) is never exceeded. When it is full, the oldest bullet (the largest age) is replaced, and a warning is logged once.
- **Removal:** `update(dt)` moves bullets, turns their velocity and ages them. Removal is a swap-remove, so slot numbers are not stable across steps.
- **Culling:** `setCullArea(rect)` removes bullets outside the rectangle (the demo passes the camera view plus a margin).
- **Clearing:** `clear(team, listener)` removes all bullets of a team (-1 for all) and reports each position to the listener, for score pickups.

**Patterns** are JSON assets, cached and hot-reloaded like prefabs (through `assets.generation()`):
```json
{ "version": 1,
  "emit": [
    { "type": "spiral", "bullet": "orb_red", "count": 6, "speed": 4, "repeat": 60, "interval": 0.08, "angleStep": 11 },
    { "type": "aimed", "bullet": "pellet", "count": 3, "spread": 20, "speed": 7, "repeat": 5, "interval": 0.6,
      "delay": 1, "child": { "type": "ring", "bullet": "orb_red", "count": 8, "speed": 3 }, "childAfter": 0.8 } ] }
```
- **Emitters:** `emit` is one emitter or a list, and they run together. Each has a `type`:
  - `single`: one bullet at `angle`.
  - `ring`: `count` bullets spread evenly from `angle`.
  - `spiral`: a ring that turns by `angleStep` every volley; any emitter can use `angleStep`.
  - `arc`: `count` bullets across `spread` degrees, centred on `angle`.
  - `aimed`: like `arc`, but centred on the direction to the target.
  - `random`: `count` bullets at random angles within `spread` of `angle`, from a seeded random generator, so runs are deterministic.
  - `wave`: `count` bullets, with the angle swinging by `amplitude` degrees at `frequency` volleys per cycle.
- **Fields and defaults:** `bullet` (required), `count` 1, `speed` 5, `angle` 270 (down), `spread` 30, `repeat` 1, `interval` 0.1, `delay` 0, `angleStep` 0, `speedStep` 0, `accel` 0, `turn` 0, `amplitude` 30, `frequency` 8, `offset` 0 (spawn distance from the origin along the angle), `child` (an emitter each bullet runs at its own position after `childAfter` seconds, then the bullet is removed), `childAfter` 1.
- **Errors:** unknown types, fields and bullet types are reported together, naming the file.

**`BulletSystem(world, bullets, patterns, events, health, time)`** in `FIXED` (bullets step by `time.fixedDelta()`):
- **Pattern runners:**
  - `run(pattern, origin, target, team)` starts a pattern. `origin` is an entity whose `Transform` the emitters follow; when it dies the runner stops. `target` (optional) is what `aimed` emitters aim at.
  - `runAt(pattern, x, y, target, team)` starts one at a fixed point.
  - Both return a handle; `stop(handle)` and `stopAll(origin)` stop runners, and `running(handle)` asks about one.
  - Runners are pooled, so starting one makes no new objects after warm-up.
- **Each step:**
  1. the pattern runners emit;
  2. the bullets move;
  3. wall bullets are checked with a raycast from their last position against tiles and colliders on the wall mask (`setWallMask`, default layer 0);
  4. bullets are checked against targets.
- **Targets** are entities with `BulletTarget` (`radius` 0.25, `grazeRadius` 0), `Transform` and optionally a `Team` (kit-common).
  - **Team filter:** a bullet hits only targets of another team.
  - **Hit test:** circle against circle, touching counts: the distance is at most the sum of the radii.
  - **Grid:** targets go into a uniform grid (cell size 2), rebuilt every step, so many enemies cost little.
- **A hit:**
  - It queues `BulletHit(target, type, damage, x, y, team)` and calls `health.damage(target, damage, NONE, 0, 0)` when a `HealthSystem` was given.
  - It removes the bullet unless it pierces. A piercing bullet hits each target once.
- **Graze:** a bullet within a target's graze radius (but not hitting) queues `Grazed(target, x, y)` once per bullet.
- **Clearing:** `clear(team, toPickups)` removes bullets and, with `toPickups`, queues `BulletCleared(x, y)` for each one.

**Rendering:**
- **The renderer:** `BulletRenderer(bullets, assets, layer)` is a `LayerContent` added to the `SpriteRenderSystem`, and draws every bullet on its render layer in one pass.
- **Regions and rotation:** each type's region is looked up once and cached. With `rotate`, a bullet is drawn rotated to its velocity.
- **Interpolation:** bullets are drawn at their positions from the last fixed step. Bullets are small and fast, so interpolation is left out; this is listed as a risk.

**No allocation:** after warm-up, moving, emitting, colliding and drawing make no new objects. Events are the exception: hits, grazes and clears allocate their event records, and they are rare compared with bullet steps. A test measures the thread's allocated bytes over many steps with 3,000 bullets.

**Components:** `BulletsKit.register(registry)` registers `BulletTarget`.

**API sketch:**
```java
public final class BulletTypes { public static BulletTypes parse(JsonValue json, String file); public int index(String name); public BulletType get(int index); public int size(); }
public final class BulletType { public final String name, region; public final float radius, lifetime, scale; public final int damage; public final boolean walls, pierce, graze, rotate; }
public final class BulletWorld {
    public BulletWorld(int capacity, BulletTypes types);
    public int spawn(int type, float x, float y, float angleDeg, float speed, int team);
    public void setAccel(int slot, float accel); public void setTurn(int slot, float degPerSecond);
    public void setChild(int slot, Emitter child, float after);
    public void update(float dt); public void setCullArea(Rectangle area);
    public int size(); public int capacity(); public float x(int i); public float y(int i); public float vx(int i); public float vy(int i);
    public int type(int i); public int team(int i);
    public void remove(int slot); public int clear(int team, BulletClearListener listener);
}
public final class BulletPatterns { public BulletPatterns(Assets assets, BulletTypes types); public Pattern get(String id); }
public final class Pattern { public String id(); public List<Emitter> emitters(); }
public final class Emitter { /* parsed, read-only fields */ }
public final class BulletSystem extends EntitySystem {
    public BulletSystem(World world, BulletWorld bullets, BulletPatterns patterns, EventBus events, HealthSystem health, Time time);
    public int runEmitter(Emitter emitter, int origin, float x, float y, int target, int team);
    public int run(String pattern, int origin, int target, int team);  public int runAt(String pattern, float x, float y, int target, int team);
    public void stop(int handle); public void stopAll(int origin); public boolean running(int handle);
    public int clear(int team, boolean toPickups); public void setWallMask(int mask, Collision collision);
}
public final class BulletTarget { public float radius = 0.25f; public float grazeRadius; }
public record BulletHit(int target, int type, int damage, float x, float y, int team) { }
public record Grazed(int target, float x, float y) { }
public record BulletCleared(float x, float y) { }
public interface BulletClearListener { void cleared(float x, float y); }
public final class BulletRenderer implements LayerContent { public BulletRenderer(BulletWorld bullets, Assets assets, String layer, float pixelsPerUnit); }
```

**Tests (headless, deterministic at a fixed step):**
- Each emitter type gives the expected angles and positions.
- `repeat`, `interval`, `delay`, `angleStep` and `speedStep` work.
- A nested `child` spawns at the bullet's position and removes it.
- Acceleration and turning work.
- Hits and grazes are right at the exact edge.
- Teams are filtered, and piercing bullets hit each target once.
- Wall bullets stop at tiles.
- Clearing works with pickups.
- Capacity replaces the oldest bullet.
- Culling works.
- Pattern and type errors are reported.
- No allocation over 600 steps with 3,000 bullets.

## Decisions in I-325
- **Constructors:** `BulletSystem` takes the engine `Time`, because `EntitySystem.begin()` gets no delta and the bullets must step even when there are no targets. `BulletRenderer` takes pixels per unit, to size regions in world units.
- **Target offset:** `BulletTarget` gained `offsetX` and `offsetY`, so the hit circle can sit at a sprite's centre while the `Transform` is at its feet.
- **`runEmitter`:** `BulletSystem.runEmitter(emitter, ...)` runs a single emitter. Child emitters use it, and weapons (I-320) will too.
- **Exact trigonometry:** angles use `Math` sine and cosine, not libGDX's lookup tables, which were off by about 0.01 degrees.
- **Measured (desktop, JUnit):** 3,000 turning bullets take about 85 µs per fixed step, with 80 bytes allocated over 600 steps; the test allows 16 KB.
- **Not checked:** the 60 fps target on Android is checked with the shooter demo (I-340).

## Non-goals
- BulletML or other external pattern languages. A visual pattern editor (hot reload + dev tools instead). Physics-based bullets (use Box2D separately).

## Acceptance criteria
- 3,000 active bullets at 60 fps on the reference desktop and on a mid-range Android phone; no per-bullet allocation after warm-up.
- Each emitter type produces the expected positions in unit tests (deterministic at fixed step).
- Hitbox and graze detection correct at the edges.
- Bullet clear works; nested emitters work.
- `demo-shooter` includes a bullet-hell boss stage using at least three pattern types.
