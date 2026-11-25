# F-024 — Bullet Kit (Projectiles and Bullet Hell)

- Status: Draft
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

## Non-goals
- BulletML or other external pattern languages. A visual pattern editor (hot reload + dev tools instead). Physics-based bullets (use Box2D separately).

## Acceptance criteria
- 3,000 active bullets at 60 fps on the reference desktop and on a mid-range Android phone; no per-bullet allocation after warm-up.
- Each emitter type produces the expected positions in unit tests (deterministic at fixed step).
- Hitbox and graze detection correct at the edges.
- Bullet clear works; nested emitters work.
- `demo-shooter` includes a bullet-hell boss stage using at least three pattern types.
