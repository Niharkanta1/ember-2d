# F-016 — Top-down Movement and Shooter Package

- Status: Top-down movement Ready (I-310, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Top-down movement (I-310)"); shooter package, AI hooks and demo: Draft
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
