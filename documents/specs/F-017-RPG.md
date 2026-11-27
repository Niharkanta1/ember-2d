# F-017 — RPG Package

- Status: Interaction Ready (I-400, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Interaction (I-400)"); items, dialogue, persistence and travel: Draft
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

## Non-goals
Quest system (needs its own spec), combat stats/levelling, equipment, shops, cutscene editor (cutscenes use Ink + `kit-novel` tags).

## Acceptance criteria
- Interaction range and selection correct with several candidates.
- Inventory add/remove/stack/capacity rules.
- Dialogue with branching choice runs; external function sets a world flag; state survives save/load.
- Opened chest stays open after leaving and re-entering the scene and after save/load.
- `demo-rpg` uses only public APIs.
