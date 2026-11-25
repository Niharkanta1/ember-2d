# F-016 — Top-down Movement and Shooter Package

- Status: Draft
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
