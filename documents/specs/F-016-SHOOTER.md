# F-016 — Top-down Movement and Shooter Package

- Status: Top-down movement Ready (I-310, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Top-down movement (I-310)"); shooter package Ready (I-320, approved 2026-11-27 under the standing approval; see "Shooter package (I-320)"); AI hooks and demo: Draft
- Phase: 4
- Module / package: `kit-topdown` (movement + `shooter` package)
- Depends on: F-003, F-004 (Y-sort), F-005, F-006, F-021, F-024 (bullets); gdx-ai (optional)
- Tasks: I-310, I-320, I-325, I-330, I-340

## Goal
Reusable top-down movement (shared with the RPG package) and combat foundations for top-down shooters.

## Scope
**Top-down movement (shared):** 8-way movement with acceleration/friction, collide-and-slide against tiles and solids, facing direction, optional knockback. Optional grid/tile-step mode (used by some RPGs).
**Shooter package:**
- Aim abstraction: mouse world position, touch twin-stick (F-022) or right stick (Phase 6) → aim vector.
- `WeaponDef` (JSON): fire rate, bullet pattern or bullet type (F-024), speed, spread, pellets, magazine size, reload time.
- Weapon runtime: cooldown, ammo, reload state; fire mode single or automatic.
- Projectiles: fired through `kit-bullets` (F-024, ADR-020) — no ECS projectile entities; bullets collide with solid tiles; hits are applied through `kit-common` damage.
- Enemies and bosses fire F-024 patterns; `demo-shooter` includes a bullet-hell boss stage.
- Enemy AI hooks: steering (seek/flee/arrive) and pathfinding on the tile grid through gdx-ai, behind a small `kit-topdown` AI adapter.
- Effect hooks: `Fired`, `Hit`, `Reloaded` events.

## Top-down movement (I-310)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.topdown`.

**`TopDownBody` component** (registered with `TopDownKit.register(registry)`, usable in prefabs and Tiled):

| Field | Default | Meaning |
| --- | --- | --- |
| `maxSpeed` | 6 | tiles per second |
| `acceleration` | 60 | towards the input direction |
| `friction` | 50 | slowing down with no input |
| `knockbackDecay` | 30 | how fast knockback velocity fades |
| `knockbackScale` | 1 | multiplies the knockback of `kit-common` `Damaged` events (0 ignores them) |
| `flipSprite` | true | face left and right by flipping the `Sprite` |
| `gridStep` | false | move tile by tile (`stepsPerSecond` 6) instead of freely |
| runtime `vx`, `vy`, `knockbackX`, `knockbackY`, `facingX` (1), `facingY` (0), `moving`, `locked` | | |

**`TopDownSystem(world, collision, input, events)`** in `FIXED`, for entities with `TopDownBody`, `Actor` and `Transform`:
- **Input:** reads the `move` 2D axis (`input.axis2`). A raw length above 1 is clamped to 1, so diagonals are as fast as straight lines.
- **Free movement:**
  - **Speed:** velocity moves towards `direction * maxSpeed` by `acceleration` per second, or towards 0 by `friction` with no input.
  - **Knockback:** knockback velocity is added and fades by `knockbackDecay`.
  - **Collision:** the body moves with `collision.moveAndSlide`, so it slides along walls and solids. A blocked axis zeroes that velocity.
- **Grid steps:**
  - **Starting a step:** with no step running and input held, the dominant axis picks a step of one tile (`tileSize`, default 1 world unit).
  - **Moving:** the step moves at `stepsPerSecond` with `moveAndCollide`. A step that is blocked ends where it stopped.
  - **Holding:** holding the input chains steps.
  - **Event:** `StepFinished(entity, tileX, tileY)` is published after each step.
- **Facing:** the facing follows the last non-zero input (`facingX`, `facingY`, unit length on the dominant axis in grid mode, normalised in free mode). With `flipSprite`, a horizontal facing sets `Sprite.flipX` (left).
- **`locked`:** a locked body ignores input (cutscenes, dialogue) but still slides and takes knockback.
- **Knockback from `kit-common`:** the system subscribes to `Damaged` and adds `knockback * knockbackScale` to the target's body, so genre code does not have to.
- **Manual knockback:** `knockback(entity, x, y)` adds knockback directly.

**Scrolling camera (F-004, `ember.camera`):** for shoot-'em-up stages.
- **The component:** `CameraScroll` with `speedX`, `speedY` (units per second), `bounds` (the scroll stops at its edge; null means no end), `clampTag` (default `player`) and `margin` (0.5).
- **`CameraScrollSystem(world, rig, events)`, in `LATE`:**
  - It moves the camera by the speed times the scaled delta, stopping at `bounds`.
  - It then keeps every entity tagged `clampTag` inside the view minus `margin`, by moving its `Transform`, both the current and the previous position, so interpolation does not show a jump back.
  - It publishes `ScrollFinished` once when the scroll stops at `bounds`.
- **One camera driver:** a camera entity uses either `CameraFollow` or `CameraScroll`, not both. The scroll system starts from the rig's current position.

**Y-sort:** already in the renderer (`SortMode.Y_SORT`, I-060). The demo uses it for its world layer, and I-310 adds no change there.

**Tests (headless):**
- Diagonal input is not faster than straight input.
- Sliding along a wall keeps the parallel speed.
- Acceleration and friction reach `maxSpeed` and stop.
- Knockback from a `Damaged` event pushes the body and fades.
- `locked` ignores input.
- Grid steps move exactly one tile, chain while held and stop at walls, with `StepFinished` published.
- The facing and the flip follow the input.
- The scrolling camera moves at its speed, stops at its bounds with `ScrollFinished` published once, and clamps tagged entities into the view.

## Decisions in I-310
- **Grid step size:** a field on the body (`tileSize`, default 1), because `Collision` does not expose its tile grids.
- **Scrolling camera constructor:** `CameraScrollSystem` takes the event bus (for `ScrollFinished`) instead of `Time`, because systems get their delta from the world.
- **Registration:** `CameraScroll` is registered by games, like `CameraFollow`. It is not pre-registered.
- **Friction:** it slows the body along its velocity, so a diagonal stop stays diagonal.
- **Not checked on a device:** no demo uses this yet. The shooter demo (I-340) will.

## Shooter package (I-320)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.topdown.shooter`. It was built after I-325, because weapons fire through `kit-bullets`.

**Aiming (`Aim` component, `AimSystem`, in `UPDATE`):**
- **Where the aim comes from,** in this order:
  1. the 2D axis `Aim.axis` (default `aim`) when it is not zero: the touch aim stick, or keys bound to it;
  2. the mouse, when `Aim.mouse` is true: the direction from the entity to `input.pointerWorld`;
  3. otherwise the aim stays where it was. For a new entity that is its `TopDownBody` facing.
- **Runtime fields:** `aimX` and `aimY` (unit length) and `angle` (degrees).
- **Platform choice:** games set `mouse` on desktop and leave it off on touch screens, so the last touch does not become the aim.

**Twin-stick touch controls (F-022 extension, `ember.input`):**
- **Configuring it:** the bindings file's `touchControls` section can add an aim stick:
  ```json
  "aimStick": { "axis": "aim", "x": 120, "y": 120, "radius": 56, "deadZone": 0.2, "fire": "fire", "fireAt": 0.5 }
  ```
- **Behaviour:**
  - It is a floating stick on the right half of the screen. A touch that lands on a button still goes to the button.
  - It drives the 2D axis, and pushed past `fireAt` of its radius it holds the `fire` action (a new virtual input, `InputNames.AIM_FIRE`).
  - It works only while its axis's context is on top, like the other controls.
  - The overlay draws a faint idle base at (`x`, `y`) dp from the bottom right, and the base and knob while it is held.
- **Validation:** the axis must be 2D and differ from the movement stick's axis, and `fire` must be a known action.
- **Layout editor:** it cannot move the aim stick yet. The layout file keeps its name, `aimStick`, for later.

**Weapons:**
- **`WeaponDef`:** a JSON asset (type `json`), loaded and cached by `WeaponLibrary` (hot-reloadable like patterns):
  ```json
  { "version": 1, "fireRate": 8, "auto": true, "magazine": 24, "reload": 1.2, "muzzle": 0.6,
    "emit": { "type": "arc", "bullet": "pellet", "count": 3, "spread": 12, "speed": 14 } }
  ```
- **`WeaponDef` fields:**
  - `fireRate`: shots per second, required, above 0.
  - `auto` (false): keep firing while the trigger is held.
  - `magazine` (0 means unlimited).
  - `reload`: seconds (1).
  - `muzzle`: spawn distance along the aim (0.5).
  - `emit`: one `kit-bullets` emitter, required. Its angle is replaced by the aim angle, and one volley is fired per shot; `repeat` and `delay` are ignored for weapons.
- **`Weapon` component:**
  - **Fields:** `weapon` (asset ID), `fireAction` and `reloadAction`. With an action name set, the system reads the trigger from input; empty means AI or code sets `trigger`.
  - **Runtime fields:** `trigger`, `cooldown`, `ammo` (-1 means full), `reloadLeft` and `reloading`.
- **`WeaponSystem(world, bullets, weapons, events, input)`, in `FIXED`:**
  - **Timers:** it counts down the cooldown and the reload; a finished reload fills the magazine and publishes `Reloaded`.
  - **Firing:** a pulled trigger fires when the cooldown is over and no reload runs. With `auto` off, firing again needs a new pull.
  - **What a shot does:** it calls `BulletSystem.fire(emitter, x, y, angle, team)` at the muzzle, with the shooter's `Team`. It uses one ammo, sets the cooldown to `1 / fireRate` and publishes `Fired`.
  - **Reloading:** an empty magazine starts a reload by itself, publishing `ReloadStarted`. The reload action starts one when the magazine is not full; `reload(entity)` does the same from code.
- **No per-shot allocation:** `Fired`, `ReloadStarted` and `Reloaded` are reused, mutable event objects. They are published synchronously and are valid only inside the handler. Hits are `kit-bullets` `BulletHit` events.

**Engine and kit additions:**
- `BulletSystem.fire(Emitter, x, y, angle, target, team)` fires one volley of an emitter with its angle replaced.
- `BulletPatterns.parseEmitter(JsonValue, where, types)` parses an emitter on its own; it is public for weapon files.

**Registration:** `ShooterKit.register(registry)` registers `Aim` and `Weapon`.

**Tests (headless):**
- The fire rate and automatic mode are right.
- Single shots need a new pull.
- Ammo runs down, the reload starts by itself, takes its time and fills the magazine; a manual reload works.
- A shot spawns its bullets at the muzzle along the aim, with the shooter's team.
- No allocation over many shots after warm-up.
- The aim comes from the axis, the mouse or the facing.
- Aim stick parsing errors are reported.
- The aim stick drives its axis and holds fire past the threshold.
- The aim stick yields to buttons and to the left half.

## Decisions in I-320
- **Event names:** the reused event objects are nested classes, `ShotEvents.Fired`, `ShotEvents.ReloadStarted` and `ShotEvents.Reloaded`, so their mutable, handler-only nature is visible at the call site.
- **Input in fixed steps:** `WeaponSystem` reads the trigger with `input.pressed(fireAction)`. Edges inside `FIXED` follow the input's fixed-step rules (F-005).
- **Allocation test:** the "no allocation" test pulls the trigger directly, because the test's proxy-based fake `Input` allocates an argument array on every call. The real input does not.
- **Not checked on a device:** the aim stick on a touch screen is checked with the shooter demo (I-340).

## Non-goals (deferred)
Weapon switching UI, burst mode, hitscan, melee combos, a visual bullet pattern editor (patterns are JSON, F-024).

## Acceptance criteria
- Movement normalised diagonally; slides along walls.
- Fire rate and automatic mode correct; reload and ammo state correct.
- Weapons fire through `kit-bullets` with no per-shot allocation.
- Hits apply damage once per bullet and respect invulnerability.
- Playable with touch twin-stick controls on Android.
- Enemy follows a path around walls.
- `demo-shooter` uses only public APIs.
