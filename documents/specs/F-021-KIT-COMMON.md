# F-021 — Common Gameplay Kit

- Status: Draft
- Phase: 4 (some parts may be pulled into Phase 3 by the platformer demo); `DialogueRunner` in Phase 5
- Module / package: `kit-common` (shared kit)
- Depends on: F-003, F-006, F-019, F-002, F-013; blade-ink (dialogue)
- Tasks: S-03, I-300, I-420
- Decisions: ADR-003 (shared kits), ADR-017 (Ink)

## Goal
Genre-neutral gameplay building blocks used by every genre kit, so no mechanic is written twice and no genre kit depends on another.

## Scope
- **Health and damage:** `Health` (current, max), `Damage` events, invulnerability window, death event (`Died`), optional knockback vector.
- **Hit/hurt boxes:** `Hitbox` (deals damage, team/layer, active window) and `Hurtbox` (receives damage); resolution via `Collision` overlap each fixed step; one hit per hitbox activation per target.
- **Triggers:** generic trigger zones with filters (tag/layer) and once/repeat behaviour; used for pickups, checkpoints, doors, level exits.
- **Pools:** typed entity pools for effects and frequently spawned entities (prefab-based), with automatic return on destroy. (Bullets use `kit-bullets`, F-024, not entity pools.)
- **Dialogue runner:** `DialogueRunner` over blade-ink — load an Ink story by asset ID, continue, current line, speaker, tags, choices, choose, variables, external function binding, state save/restore into the save slot. Used by the RPG package (F-017) and `kit-novel` (F-023).
- **Spawners:** spawn a prefab at intervals, on trigger, or in waves; cap on live count.
- **Lifetime:** destroy after N seconds.
- **Team/faction:** simple team ID used by hitboxes.

## Non-goals
Stats systems, buffs/debuffs, AI (kits own AI hooks), inventory (RPG package).

## Acceptance criteria
- Damage applies once per activation; invulnerability blocks repeats for its window.
- Death event fires exactly once.
- Trigger once/repeat and filters correct.
- Pool reuses entities without allocation after warm-up.
- Spawner respects interval and live cap.
- Dialogue: branching story runs, tags and external functions work, state round-trips through a save.
- No dependency on any genre kit (`kit-platformer`, `kit-topdown`, `kit-novel`) or on `kit-bullets`.
