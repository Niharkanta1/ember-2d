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

## RPG demo (I-450)
Status: Done. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Module `demos/demo-rpg` (`core`, `desktop`, `android`); data from `assets-src/demo-rpg/make_assets.py`, sounds from `sound/make_sounds.py`, stories in `assets-src/demo-rpg/ink`.

**What it shows:**
- **Areas:** Millbrook (the town) and the cellar, two Tiled maps from the `rpg-rpg` art pack. Travel zones (`Travel` component: area and arrival marker) lead between them with a fade.
- **Conversation:** the elder Grik (an `Interactable` whose `action` names a knot in `ink/town.ink`). The story calls `has`, `give`, `take`, `flag` and `set_flag`, which are bound to the inventory and the world flags. A sticky choice gives the cellar key.
- **The door and the chest:** their knots set the flags `door.cellar` and `chest.cellar`. The scene listens for `FlagChanged`: the door plays its opening, loses its `Solid` and stops being interactable; the chest turns dull and offers "Search" (`chest_empty`). On entering an area both are shown as their flags say, so they stay open after travel and after loading.
- **The cutscene:** the first visit to the cellar plays `ink/cellar.ink` with `NovelDirector` and `NovelView` over the game world (no background), with a choice, a shake, a sound, a slide and a flash. The flag `cutscene.cellar` keeps it from playing again.
- **Saving:** slot 0 holds the area, the position, the inventory, the flags and the town story's state. The game saves on arriving in an area, from the pause menu, and when Android puts the app in the background (`setSuspendSave`). The menu offers Continue when the slot has a save.
- **HUD:** the carried items (icon and count) at the top left, the interaction prompt ("E: Talk" / "A: Talk") at the bottom.

**Ink build step:** `gradlew :demos:demo-rpg:core:compileInk` compiles `assets-src/demo-rpg/ink/*.ink` into `assets/ink/*.ink.json` with inklecate from the Gradle property `ember.inklecate`. Without the property the task is skipped. The compiled stories are committed, so building needs no inklecate.

**Tests (`RpgDataTest`):**
- The quest runs through the story functions: refusing, accepting, the locked and the open door, the chest, handing over the tonic, the slime.
- A saved play-through loads its flags, items, story state and place.
- The cutscene uses only known characters and tags (dev mode, both choices).
- Every map object is a prefab with registered components; every travel arrives at a marker that exists; every interaction names a knot.

## Decisions in I-450
- **`ember.ui.DialogueBox` (F-012):** the dialogue box widget planned for Phase 5 arrived with its first user. It has a name plate, typewriter text, and a confirm action or click that completes the line first, then advances. Choices sit inside the box. A press in the frame a line appears is ignored, so the press that starts a conversation does not also skip its first line. `NovelView` uses the same widget.
- **Lookahead fix in `DialogueRunner.bind` (F-021):** blade-ink may call an external function while looking ahead past a line, then call it again for real. Game functions with side effects ran twice (the elder took two tonics). `bind` now registers functions as not lookahead-safe. `DialogueRunnerTest` covers a function between two lines.
- **`NovelView.setCharacterArea(bottom, height)` (F-023):** full-screen characters (85 % of the height) are too large over a game's world. The RPG cutscene stands them on the dialogue box at half the height.
- **Art pack:** `rpg-rpg` has dungeon tiles only, no outdoor tiles, so the town is a paved square with walled buildings, built from the same tile sheet as the cellar.
- **The elder and the slime** are the pack's goblin and slime, as friendly characters. There is no combat (not in scope).
- **Inventory** lives in the play-through (`Game`) and is added to each area's player entity, so it is the same object across scenes.
- **Checked on the emulator** (API 23, debug build):
  - The menu, a new game, the town, talking to the elder through both lines and the choice, the key in the HUD.
  - Opening the door, travelling to the cellar, the cutscene with its choice and the backlog, talking to the slime, opening the chest.
  - Saving from the pause menu, killing the app, Continue: the cellar at the saved place, the chest still open, no cutscene again.
  - Back up the stairs: the door still open. Handing the tonic to the elder.
  - This was the first Android run of blade-ink; no errors were logged.
- **Found on the emulator and fixed:**
  - The dialogue box was built before the scene's skin was set and drew as a black panel.
  - Choices overflowed the fixed-height box; the box now grows.
  - The new-game confirmation was too long for `ConfirmDialog` (it does not wrap) and promised to keep the save, which arriving in the town overwrites.
- **Not checked:** a release (R8) build on the emulator, the desktop build by hand (it is built and its tests run), and a real phone.

## Non-goals
Quest system (needs its own spec), combat stats/levelling, equipment, shops, cutscene editor (cutscenes use Ink + `kit-novel` tags).

## Acceptance criteria
- Interaction range and selection correct with several candidates.
- Inventory add/remove/stack/capacity rules.
- Dialogue with branching choice runs; external function sets a world flag; state survives save/load.
- Opened chest stays open after leaving and re-entering the scene and after save/load.
- `demo-rpg` uses only public APIs.
