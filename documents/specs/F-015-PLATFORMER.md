# F-015 — Platformer Kit

- Status: Demo level 1 (I-170) Ready: expanded and approved 2026-11-26 (see "Demo level 1 (I-170)"); kit extraction (I-200) Ready: expanded and approved 2026-11-26 (see "Kit extraction (I-200)")
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

## Kit extraction (I-200)
Status: Ready (expanded for I-200 and approved 2026-11-26; see "Decisions in I-200"). Phase 3 starts by moving level 1's movement code from the demo into `kit-platformer`, and adding the optional mechanics behind flags.

**What moves into the kit** (package `dev.ember2d.kit.platformer`, public API, data + components + systems, no base classes):

| Demo class (I-170) | Kit class | Notes |
| --- | --- | --- |
| `PlayerControl` | `PlatformerBody` | Same tuning fields and defaults, plus action names and the optional mechanics below |
| `PlayerControlSystem` | `PlatformerSystem` (`FIXED`) | Same movement and the optional mechanics. It writes the state machine parameters only when the definition declares them. |
| `Jumped`, `Landed` | `Jumped(entity, kind)`, `Landed(entity)`, new `Dashed(entity, direction)` | `kind`: `GROUND`, `COYOTE`, `AIR`, `WALL` |
| `Patrol`, `PatrolSystem` | `Patroller`, `PatrollerSystem` | Walks between limits or a `range` and turns at walls; new `turnAtLedges` (off by default) |
| `MovingPlatform`, `MovingPlatformSystem` | `MovingPlatform`, `MovingPlatformSystem` | New `vertical` flag; its current velocity can be read, for momentum |

- **Registration:** `PlatformerKit.register(ComponentRegistry)` registers the kit components; the demo's `DemoComponents` calls it.
- **Stays in the demo:** the level rules (`LevelSystem`, `Enemy`, `Hazard`, `Checkpoint`, `Goal`, `LevelCompleted`). Damage and health belong to `kit-common` (I-300), and goals are game-specific.
- **Prefab and map data:** the demo's prefabs use the new component names (`PlatformerBody`, `Patroller`). Level 1 plays exactly as before: all optional mechanics are off in its prefab.
- **Input:**
  - Action names come from the body: `moveAxis` = `move`, `jumpAction` = `jump`, `dashAction` = `dash`.
  - An action missing from the bindings counts as never pressed. The system checks this once and throws nothing, so a game without a dash binding simply has no dash.
- **No engine changes.**
  - Walls use `Collision.isTouching(LEFT/RIGHT)`, ladders use `overlapBox`, and the platform under the player comes from `Actor.hitEntityY`.
  - If the kit needed an engine change, that would mean the engine API is wrong (ARCHITECTURE, genre rule 4).

**Optional mechanics** (fields on `PlatformerBody`; each is independent and off by default):

| Mechanic | Switch | Tuning (defaults) | Rule |
| --- | --- | --- | --- |
| Double / multi jump | `airJumps` > 0 | `airJumpSpeed` 14 | Jumps in the air after coyote time, up to `airJumps` times, refilled on landing. The jump cut applies to them too. |
| Dash | `dash` = true | `dashSpeed` 20, `dashTicks` 10, `dashCooldownTicks` 30, `airDashes` 1 | A horizontal burst in the input direction (or the facing direction), with no gravity while dashing. Allowed on the ground, and `airDashes` times per time in the air. Publishes `Dashed`. |
| Wall slide | `wallSlide` = true | `wallSlideSpeed` 3 | In the air, while falling, touching a wall and pushing towards it: the fall speed is capped. |
| Wall jump | `wallJump` = true | `wallJumpX` 9, `wallJumpY` 15, `wallJumpLockTicks` 8 | Jumping while touching a wall in the air pushes away from it. Input towards the wall is ignored for the lock ticks, so you cannot climb a single wall. |
| Ladders | `ladders` = true | `climbSpeed` 5 | Entities with a `Ladder` component (a trigger collider) can be climbed. Pressing up or down while overlapping starts climbing, with no gravity. Jumping, or leaving the ladder, ends it. |
| Platform momentum | `inheritPlatformVelocity` = true | — | Jumping off a `MovingPlatform` adds its velocity to the jump. |

- **State machine parameters** (written only if the definition declares them):
  - bools: `grounded`, `wallSliding`, `dashing`, `climbing`
  - floats: `speedX`, `velY`
  - triggers: `airJump`, `wallJump`
- **Ticks:** every window counts fixed ticks, like coyote time and the jump buffer, so behaviour is exact and the same at any frame rate.
- **Demo bindings:** `dash` is bound to X and Left Shift. There is no touch button for it in I-200: on-screen controls are static, and the full controls come with I-225.

## API sketch (I-200)
```java
package dev.ember2d.kit.platformer;
@PublicApi public final class PlatformerKit { public static void register(ComponentRegistry registry); }
@PublicApi public final class PlatformerBody {   // component; all public fields settable from prefabs and Tiled
    public String moveAxis = "move", jumpAction = "jump", dashAction = "dash";
    public float maxSpeed = 8, groundAccel = 60, groundDecel = 70, airAccel = 35, airDecel = 20;
    public float gravity = 40, fallMultiplier = 1.6f, maxFall = 25, jumpSpeed = 16, jumpCut = 0.45f;
    public int coyoteTicks = 6, bufferTicks = 6;
    public int airJumps; public float airJumpSpeed = 14;
    public boolean dash; public float dashSpeed = 20; public int dashTicks = 10, dashCooldownTicks = 30, airDashes = 1;
    public boolean wallSlide; public float wallSlideSpeed = 3;
    public boolean wallJump; public float wallJumpX = 9, wallJumpY = 15; public int wallJumpLockTicks = 8;
    public boolean ladders; public float climbSpeed = 5;
    public boolean inheritPlatformVelocity;
    public float vx, vy; public boolean locked;   // runtime; locked = input ignored (game rules: hurt, cutscene)
    public boolean grounded(); public boolean dashing(); public boolean wallSliding(); public boolean climbing();
    public int facing();                           // +1 right, -1 left
}
@PublicApi public final class PlatformerSystem extends EntitySystem {   // FIXED; family Transform + PlatformerBody + Actor
    public PlatformerSystem(World world, Collision collision, Input input, EventBus events);
}
@PublicApi public final class Ladder { }   // component: a trigger collider that can be climbed
@PublicApi public final class Patroller { public float speed = 6, minX, maxX, range, gravity = 40; public float direction = 1;
    public boolean artFacesLeft, turnAtLedges; }
@PublicApi public final class PatrollerSystem extends EntitySystem { public PatrollerSystem(World w, Collision c); }
@PublicApi public final class MovingPlatform { public float speed = 2, distance = 6; public boolean vertical;
    public float velocityX(); public float velocityY(); }
@PublicApi public final class MovingPlatformSystem extends EntitySystem { public MovingPlatformSystem(World w, Collision c); }
@PublicApi public record Jumped(int entity, JumpKind kind) { }
@PublicApi public enum JumpKind { GROUND, COYOTE, AIR, WALL }
@PublicApi public record Landed(int entity) { }
@PublicApi public record Dashed(int entity, int direction) { }
```

## Tests (I-200)
- **Kit (headless, engine collision on a small grid, scripted input):**
  - the level 1 movement tests move to the kit and keep passing (acceleration, jump cut, coyote 6/7, buffer 6/7, 60 vs 144 Hz)
  - double jump: `airJumps` 1 allows exactly one air jump, refilled on landing; 0 allows none
  - dash: distance and duration, no gravity while dashing, cooldown, air dash count, the `Dashed` event, no dash without the binding
  - wall slide: the fall is capped only while pushing into a wall
  - wall jump: pushes away, and the input lock stops climbing a single wall
  - ladders: climbing up and down without gravity; leaving by jumping and at the top
  - platform momentum: the platform's velocity is added only with the flag
  - patroller: `range`, walls, `turnAtLedges`
  - moving platform: horizontal and vertical paths, carrying riders
  - with every optional flag off, the movement is exactly I-170's (the same positions, tick by tick, as a recorded run)
  - the kit's architecture rules (already in place)
- **Demo:** the `LevelSystem` tests stay in the demo, now using the kit's body; `LevelMapTest` is unchanged.
- **Manual:**
  - You play level 1 on desktop; it should feel the same.
  - In the dev tools inspector you can switch on `airJumps`, `dash`, `wallSlide` and `wallJump` on the player and try them live.
  - I check the emulator.

## Decisions in I-200
- **Equivalence with I-170 is checked bit for bit.**
  - Before the move, a scripted 420-tick run of the demo's movement was recorded: position and velocity as raw float bits, on a grid with a gap, a step and a one-way plank (`kit-platformer/src/test/resources/i170-trace.csv`).
  - The kit's test replays it with every option off and compares each tick exactly; it passes.
  - The recorder was a temporary demo test and was not committed.
- **Dash timing:** the last dash tick still moves at dash speed, and the speed drops to at most `maxSpeed` after that tick's move. So a dash covers exactly `dashSpeed × dashTicks / 60`. The first version dropped the speed one tick early (found by the dash distance test).
- **Order of jump rules:** climbing, then ground and coyote time, then a wall jump, then an air jump. A press in the air with air jumps left uses one at once, so it is not buffered for the landing.
- **Momentum:** the platform under the player is `Actor.hitEntityY` from the previous step. On the jump tick, ground deceleration still applies (the body was grounded), so jumping off a 3 u/s platform with no input leaves 3 − 70/60 u/s.
- **State machine parameters:** the kit reads `AnimationStateMachine.parameterTypes()` once the definition is loaded and again every 60 ticks (so a hot-reloaded definition is picked up), and writes only declared parameters. Nothing is allocated per tick otherwise.
- **The stomp bounce moved to the enemy:** `Enemy.stompBounce` in the demo (default 12), since stomping is a level rule, not movement.
- **Ladders:** pressing down while standing on the ground does not start climbing (so the ground at a ladder's foot is not a ladder). Moving sideways while climbing is at half the running speed.
- **Demo:**
  - `PlayerControl`, `PlayerControlSystem`, `Jumped`, `Landed`, `Patrol`, `PatrolSystem`, `MovingPlatform` and `MovingPlatformSystem` are deleted from the demo.
  - The player prefab uses `PlatformerBody` and the pig uses `Patroller`; the moving platform keeps the name `MovingPlatform` (now the kit's).
  - `input.json` binds `dash` to X and Left Shift.
  - The demo's movement tests moved to the kit (`PlatformerSystemTest`); the level rule tests are now `LevelRulesTest`.
- **Tests:**
  - `PlatformerSystemTest` has 16 tests: the trace, the I-170 movement tests, and air jumps, dash (ground, cooldown, air, no binding), wall slide, wall jump, ladders, momentum, patroller (range, wall, ledges) and vertical platform.
  - Leaving a ladder at its top is covered only through "not on a ladder ends climbing"; there is no separate test.
- **Checks:**
  - Emulator R8 release: level 1 starts, and the touch jump looks exactly as in I-170. The generated keep rules include the kit components.
  - Desktop dev run: level 1 loads with the dev tools.
  - Not checked by me: the optional mechanics in play on desktop (switched on through the inspector).
