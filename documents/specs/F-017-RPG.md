# F-017 — RPG Package

- Status: Interaction Ready (I-400, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Interaction (I-400)"); items and inventory Ready (I-410, approved 2026-11-27; see "Items and inventory (I-410)"); dialogue, persistence and travel: Draft
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

## Interaction (I-400)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.topdown.rpg`.

**Components** (registered with `RpgKit.register(registry)`):
- `Interactable`:
  - `prompt` ("Talk"): text for the prompt UI.
  - `action` (""): a game-defined ID, such as `talk:elder` or `open:chest_1`.
  - `range` (1.2 world units).
  - `offsetX`, `offsetY`: the point measured to, relative to the `Transform`.
  - `enabled` (true).
- `Interactor` (usually on the player):
  - `inputAction` ("interact").
  - `cone` (120 degrees around the facing).
  - runtime `target` (the focused entity, or `World.NONE`).

**`InteractionSystem(world, input, events)`, in `UPDATE`:**
- **Choosing a target:** for each `Interactor` with a `Transform`, the target is the enabled `Interactable` whose point is within its `range` and inside the cone around the interactor's facing (`TopDownBody.facingX`/`Y`; without a body, every direction counts). Among those, the nearest wins, with the angle as a tie-break: score = distance × (1 + angle / 180).
- **Touching distance:** a candidate closer than 0.3 counts in every direction.
- **Focus events:** when the target changes, `InteractionFocus(interactor, target, prompt)` is published; the target is `World.NONE` and the prompt empty when nothing is in reach.
- **Interacting:** pressing `inputAction` (`justPressed`) with a target publishes `Interacted(interactor, target, action)`. `interact(interactor)` does the same from code, for a touch button or a cutscene. It returns false without a target.
- **Locked bodies:** while the interactor's `TopDownBody` is `locked` (dialogue, cutscenes), it has no target and presses are ignored.

**Tests (headless):**
- With several candidates, the one in range and in the facing cone wins.
- The nearest wins over a farther one; touching distance ignores the facing.
- Disabled interactables are ignored.
- Focus events are published only on change.
- A press and `interact()` publish `Interacted`.
- A locked body neither focuses nor interacts.

## Decisions in I-400
- **Scoring:** the rule is used as specified. A candidate slightly off to the side but much nearer still wins, for example 0.76 at 23° (score 0.86) over 1.10 straight ahead (score 1.14).
- **Not checked on a device:** no demo uses interaction yet. The RPG demo (I-450) will.

## Items and inventory (I-410)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.topdown.rpg`.

**Item definitions:** one JSON asset (type `json`), parsed by `ItemCatalog.parse(json, file)`:
```json
{ "version": 1, "items": {
    "potion": { "name": "Potion", "icon": "atlas.rpg/potion", "stack": 9, "tags": ["consumable"] },
    "key.cellar": { "name": "Cellar key", "icon": "atlas.rpg/key", "tags": ["key"] } } }
```
- **Fields:** `name` (required), `icon` (a region ID, optional), `stack` (default 1, at least 1), `tags` (default none).
- **Errors:** unknown fields and bad values are reported together, naming the file and the item.

**`Inventory` component:**
- **Fields:** `capacity` (12 slots). The contents are runtime state.
- **Adding:** `add(catalog, item, count)` first fills existing stacks of the item up to its stack size, then uses empty slots. It returns how many fitted (the rest did not).
- **Removing:** `remove(item, count)` is all-or-nothing and takes from the last stacks first. Empty slots close up.
- **Queries:** `count(item)`, `has(item, count)`, `hasTag(catalog, tag)`, `size()`, `item(slot)`, `count(slot)`, `clear()`.
- **Unknown items:** `add` throws `IllegalArgumentException` for an item the catalog does not list.
- **Saving:** `save(SaveData out)` writes the slots. `load(SaveData in, catalog)` replaces them; items the catalog no longer lists are logged and skipped, and stacks are clamped to the current stack size.

**Pickups:**
- **The component:** `ItemPickup` (`item`, `count` 1) on an entity with a `TriggerZone`.
- **`ItemPickupSystem(world, catalog, events)`:**
  - It listens to kit-common `ZoneEntered`. When the entity has an `Inventory`, it adds the items and publishes `ItemPickedUp(entity, pickup, item, count)`.
  - When everything fitted, the pickup is despawned. Otherwise its `count` drops by what was taken, and it stays.
  - Nothing is published when nothing fitted.

**Registration:** `RpgKit.register` adds `Inventory` and `ItemPickup`.

**Tests:**
- The catalog parses and reports errors.
- Stacking fills existing stacks, then new slots, and capacity limits what is added.
- Removal is all-or-nothing.
- Counts, tags and empty slots closing up.
- Save and load round-trip, skipping unknown items and clamping stacks.
- Pickups: a full pickup despawns, a partial one stays with the rest, and an entity without an inventory is ignored.

## Non-goals
Quest system (needs its own spec), combat stats/levelling, equipment, shops, cutscene editor (cutscenes use Ink + `kit-novel` tags).

## Acceptance criteria
- Interaction range and selection correct with several candidates.
- Inventory add/remove/stack/capacity rules.
- Dialogue with branching choice runs; external function sets a world flag; state survives save/load.
- Opened chest stays open after leaving and re-entering the scene and after save/load.
- `demo-rpg` uses only public APIs.
