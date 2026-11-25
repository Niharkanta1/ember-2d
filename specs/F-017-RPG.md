# F-017 — RPG Package

- Status: Draft
- Phase: 5
- Module / package: `kit-topdown` (`rpg` package)
- Depends on: F-016 (movement), F-021 (triggers, `DialogueRunner`), F-012 (dialogue box), F-013 (world flags); optional F-023 for cutscenes
- Tasks: S-03, I-400, I-410, I-420, I-450
- Decision: ADR-017 (Ink)

## Goal
Foundations for top-down RPGs: talk, pick up, open, remember.

## Scope
- **Interaction:** `Interactable` component (prompt text, range, action ID); the player's interaction system picks the nearest interactable in facing direction within range; publishes `Interacted`.
- **Items and inventory:** `ItemDef` (JSON: ID, name, icon, stack size, tags); `Inventory` component (slots, add/remove/stack, capacity); pickup via trigger.
- **Dialogue:** Ink stories through the shared `DialogueRunner` in `kit-common` (F-021, ADR-017); external functions bound to RPG actions (give item, set flag, start quest step); story state stored in the save slot. Presented with the F-012 dialogue box. Longer cutscenes can use `kit-novel` staging (F-023).
- **World persistence:** doors, chests and NPC states read/write world flags (F-013).
- **Scene travel:** door objects in Tiled with `targetScene` and `targetSpawn` properties.

## Non-goals
Quest system (needs its own spec), combat stats/levelling, equipment, shops, cutscene editor (cutscenes use Ink + `kit-novel` tags).

## Acceptance criteria
- Interaction range and selection correct with several candidates.
- Inventory add/remove/stack/capacity rules.
- Dialogue with branching choice runs; external function sets a world flag; state survives save/load.
- Opened chest stays open after leaving and re-entering the scene and after save/load.
- `demo-rpg` uses only public APIs.
