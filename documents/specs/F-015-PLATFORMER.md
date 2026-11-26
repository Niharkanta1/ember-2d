# F-015 — Platformer Kit

- Status: Demo level 1 (I-170) Ready: expanded and approved 2026-11-26 (see "Demo level 1 (I-170)"); kit extraction (I-200): Draft
- Phase: 2 (inside `demo-platformer`), 3 (extracted to `kit-platformer`)
- Module / package: `kit-platformer`
- Depends on: F-003, F-005, F-006, F-007 (animation state machine), F-009 (Tiled placement), F-002 (timers), F-021 (optional), F-022 (touch)
- Tasks: I-170, I-200

## Goal
Responsive, configurable platformer movement built on kinematic collision, usable by registering components and systems.

## Scope
**Core (Phase 2):** horizontal acceleration/deceleration (ground and air), max speed, gravity, max fall speed, jump with variable height (cut on release), jump buffering, coyote time, grounded detection via `Collision.isTouching(DOWN)`, one-way platform drop-through.
**Optional (Phase 3, each behind a flag):** double/multi jump, dash, wall slide, wall jump, ladders, moving-platform momentum inheritance.
- `PlatformerBody` component holds tuning values (data-driven, hot-reloadable).
- The kit writes animation parameters (`grounded`, `speedX`, `velY`, `wallSliding`, `dashing`) to the entity's `AnimationStateMachine` (F-007) each update; clip choice lives in the ASM JSON, not in kit code. The kit emits events (`Jumped`, `Landed`, `Dashed`) for sound/particles.
- Input via action names configured on the component (`move`, `jump`, `dash`); works with keyboard, gamepad and on-screen touch controls (F-022).
- Enemies, hazards, pickups and moving platforms are placed in Tiled as prefab objects (F-009).

## Non-goals
- Slopes (until collision supports them), swimming, combat (use `kit-common`).

## Acceptance criteria
- Grounded/airborne transitions correct at tile edges.
- Jump buffer and coyote time windows honoured to the tick.
- Variable jump height: releasing early yields a lower apex.
- Each optional mechanic can be toggled without affecting others.
- Demo level validates feel at 60 Hz and 144 Hz, and with on-screen controls on an Android phone.
- Kit contains no code the demo needed engine changes for.

## Demo level 1 (I-170)
Status: Ready (expanded for I-170 and approved 2026-11-26; see "Decisions in I-170"). The Phase 2 core lives in `demo-platformer` (ROADMAP): player code stays in the demo until `kit-platformer` (I-200). **No engine changes:** it is built only on the public API.

**Art (needs your OK).**
- The Pixel Adventure pack in `assets/platformer-pixel-adventure` (untracked, added by you): the Ninja Frog player, the AngryPig enemy, spikes, the checkpoint and end flags, a moving platform, terrain tiles and a background tile.
- Only the files the level uses are copied into `demos/demo-platformer/core/assets/` (about 20 PNGs, under 100 KB), with the pack's licence note next to them.
- **You confirm the licence allows that.** The `assets/` folder itself stays untracked and unchanged.
- The SFX are not used: audio is Phase 3, F-011.
- The placeholder test level, its tileset and the placeholder hero sheet are removed; the level replaces them.

**Player movement** (`PlayerControl`, all tuning values are public fields, so they can be set in the prefab, hot-reloaded and edited in the dev tools inspector):

| Mechanic | Rule (fixed step, 60 Hz) |
| --- | --- |
| Run | Acceleration towards `maxSpeed` (8 u/s): ground 60 u/s², air 35 u/s². Deceleration with no input: ground 70 u/s², air 20 u/s². |
| Gravity | 40 u/s² up to `maxFall` 25 u/s; 1.6x while falling, for a snappier arc. |
| Jump | `jumpSpeed` 16 u/s. |
| Variable height | Releasing jump while rising multiplies vertical speed by `jumpCut` 0.45, once per jump. |
| Coyote time | A jump still starts up to `coyoteTicks` (6 ticks = 100 ms) after walking off a ledge. |
| Jump buffer | A jump pressed up to `bufferTicks` (6) before landing starts on the landing tick. |
| Drop-through | Down + jump on a one-way platform, as now. |
| Animation | Writes `grounded`, `speedX`, `velY` and the `hit` trigger to the state machine; the clips (idle, run, jump, fall, hit, from the pack's strips) are chosen in `anim/frog.asm.json`. |
| Events | `Jumped` and `Landed`, published for later sound and particles. |

**Level objects** (all prefabs placed as Tiled objects; nothing hard-coded in the scene):
- **Enemies** (`enemy/pig`): patrol between bounds (Tiled properties, as the patroller does now).
  - **Landing on one from above** (player falling, feet above the enemy's middle) defeats it: it plays its hit clip and is removed, and the player bounces up (`stompBounce` 12 u/s).
  - **Touching one any other way** hurts the player.
- **Hazards** (`hazard/spikes`): trigger colliders; touching one hurts the player.
- **Hurt:**
  - the player plays the hit clip for 0.4 s with input ignored, then respawns at the last checkpoint (or the start) with zero speed
  - a death counter goes up
  - a short invulnerability of 1 s blinks the sprite
- **Checkpoint** (`level/checkpoint`): touching it makes it the respawn point; the flag plays its "out" clip.
- **Goal** (`level/goal`): touching it ends the level.
  - The player stops, and a text above the goal reads "Level complete! 1:23 - 2 deaths" (time and deaths).
  - After 4 s the scene restarts (`SceneManager.restart`).
- **Kept from the test level:** a moving platform (the pack's brown platform), one-way platforms, the sign with its text from a Tiled property, and the pause and menu contexts.
- **Removed:** the glow zone (its role as a trigger demo goes to checkpoints and hazards), the code-spawned patroller and the "ride me!" label (the label moves to the sign text).

**The level** (`maps/level1.tmx`, about 140 × 18 tiles of 16 px):
- **Layers:** a background tile image with parallax, ground terrain (solid), one-way planks, decoration in front, and an `entities` object layer.
- **A left-to-right route in four parts:**
  1. a flat start with the sign
  2. gaps and one-way platforms that need the variable jump and coyote time
  3. a mid-level checkpoint followed by spikes and two pigs
  4. the moving platform over a spike pit, then the goal
- **Built from a script once** (a Python generator in the scratchpad, not committed), so the result is a normal Tiled map you edit in Tiled from then on. Tile properties (`solid`, `oneWay`) live in `maps/terrain.tsx`.

**Not in I-170:**
- HUD (time and deaths only appear at the goal; the UI layer is I-210)
- lives and game over
- fruit pickups
- double jump, wall jump, dash (Phase 3 options)
- sound
- more levels
- moving-platform momentum when jumping off

## Tests (I-170)
- **Headless movement tests in the demo** (`demos/demo-platformer/core` tests, using the engine's collision on a small test grid):
  - acceleration reaches max speed, and deceleration stops the player
  - a jump released early peaks lower than a held jump
  - coyote: a jump 6 ticks after leaving a ledge works, 7 ticks after does not
  - buffer: a jump pressed 6 ticks before landing fires on landing, 7 ticks before does not
  - a stomp defeats the enemy and bounces; side contact hurts
  - a hazard hurts; respawn goes to the last checkpoint
  - the goal shows the message and restarts after 4 s
  - fixed-step results are the same at 60 Hz and 144 Hz render rates (the fixed step is used)
- **Map check (headless):** `level1.tmx` loads, every object's prefab exists, there is exactly one player, one goal and at least one checkpoint, and the start, checkpoint and goal stand on solid ground.
- **Manual:**
  - You play it on desktop (feel at your monitor's rate), move an enemy in Tiled and save (hot reload), and open the collider view with F3 or the dev tools.
  - I check the emulator with the on-screen controls (screenshots).

## Decisions in I-170
- **Hazards hurt by overlap, every fixed step, not on trigger entry.** On entry only, a player who landed on spikes while still blinking could stand there safely afterwards (found on the emulator).
  - Checkpoints and the goal still use `TriggerEntered`.
  - Enemies use a box query each step, since actors do not trigger each other.
- **The goal locks the player** on the step it is touched, not one step later.
- **`Patrol` changes:**
  - `range` (distance each side of where the enemy was placed): moving an enemy in Tiled needs no new limits.
  - `artFacesLeft`: the pig art faces left.
- **The sign is the pack's start arrow (`level/start`).** Its text is a multi-line Tiled property written as element text. libGDX's XML reader does not decode `&#10;` in an attribute; it showed "#10" on screen.
- **The map has no decoration layer,** only the ground layer. The terrain set has no separate decoration tiles that fit; you can add a layer in Tiled.
- **Kept and removed:**
  - The menu cursor now uses the frog's jump frame (the placeholder texture is gone).
  - The squish handler only logs (respawn is the level system's job).
  - Footstep frame events stay on the run clip.
- **Copied art:**
  - 17 PNGs (64 KB): the frog (idle, run, jump, fall, hit), the pig (walk, hit), spikes, platform, start, checkpoint (empty, out, idle), goal (idle, pressed), terrain and background.
  - `assets/ART-CREDITS.txt` names the pack, Pixel Frog, its page and the licence confirmation.
- **Removed:** the test level, its tileset, sky, icons and template, the placeholder textures, the hero clips and state machine, and the patroller, glow zone and sign prefabs.
- **Tests:**
  - `PlatformerMovementTest` (10) uses the engine's collision on a 40 × 10 tile grid, with scripted input.
  - `LevelMapTest` reads the TMX, the TSX and the manifest without libGDX.
  - The coyote and buffer windows are checked at 6 (works) and 7 (does not) ticks.
  - 60 Hz and 144 Hz give identical positions after 60 fixed steps.
- **Checks:**
  - Emulator, R8 release: start area, checkpoint and spikes, pig, spike pit and platform, and goal; the touch jump works. The later parts were viewed by moving the start temporarily, which was not committed.
  - Not checked: a full play-through to the goal on a device, and the feel at 144 Hz on a real monitor.
