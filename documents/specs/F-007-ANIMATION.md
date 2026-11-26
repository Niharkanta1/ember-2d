# F-007 — Animation and Animation State Machine

- Status: clips, animator and frame events Ready (I-130, approved 2026-11-26; see "Clips and animator (I-130)" and "Decisions in I-130"). State machine Ready (I-135, approved 2026-11-26; see "State machine (I-135)" and "Decisions in I-135").
- Phase: 2 (clips, events, state machine), 6 (optional Spine adapter)
- Module / package: `ember-core` / `ember.animation`; optional `ember-spine`
- Depends on: F-002, F-003, F-004, F-008, F-019
- Tasks: I-130, I-135, I-560
- Decision: ADR-021 (Spine optional)

## Goal
Frame-based sprite animation from atlases or Aseprite exports, frame events for gameplay timing, and a small data-driven state machine that picks the right animation from game parameters — usable by every genre.

## Supported animation types
| Type | Where | Typical use |
| --- | --- | --- |
| Frame-by-frame sprite clips | `ember.animation` (this spec) | Characters, enemies, props, VN expressions |
| Animation state machine | `ember.animation` (this spec) | Choosing clips from parameters (grounded, speed, attack) |
| Property tweens | `ember.core` (F-002) | Movement, fades, scale/squash, UI, VN slide-ins |
| Animated tiles | Tiled + libGDX (F-009) | Water, torches, conveyor belts |
| Particles | `ember.particles` (F-014) | Dust, sparks, explosions |
| Skeletal (Spine) | `ember-spine`, optional (ADR-021) | Smooth character rigs, VN characters; requires a Spine license |

## Scope
**Clips**
- Clip: frames (region IDs), per-frame durations, play mode (loop, once, ping-pong), optional per-frame offset.
- Import from texture atlas region sequences and from Aseprite JSON export (frame tags → clips, durations, tag direction → play mode, optional slices for hitboxes/pivots).
- Frame events: named events on frames (from clip data or Aseprite tag user data), published as `AnimationEvent` (F-019).

**Animator**
- `Animator` component: clip set, current clip, speed, time, `play`, `playIfNotPlaying`, `isFinished`, flip from facing.
- Animation system runs in `update` and writes the `Sprite` region.

**Animation state machine (ASM)**
- Defined in JSON, referenced by asset ID, hot-reloadable.
- **Parameters:** `bool`, `float`, `int`, `trigger` (auto-resets after it fires a transition).
- **States:** each plays a clip (or, with `ember-spine`, a Spine animation); options `speed`, `speedParam` (scale playback by a parameter), `exitOnFinish`.
- **Transitions:** `from` (state or `*` for any state), `to`, `when` (conditions joined by `&&`: comparisons `> >= < <= == !=` on parameters, bare bool/trigger names, `!` negation), `priority`, `minTime` (minimum time in the current state), `exitTime` (only after the clip reaches a normalised time).
- Evaluation once per `update` after game systems set parameters; highest-priority valid transition wins; at most one transition per frame.
- **Layers:** not in the first version (one state machine per entity). Overlays such as a hit flash use shaders or tweens.
- Events: `AnimationStateEntered`, `AnimationStateExited` published as queued events.
- Dev tools (F-018) show current state, parameters and the last transition per entity.

## Clips and animator (I-130)
Status: Ready (approved 2026-11-26).

**Clip sets.**
- **The file:** a clip set is a JSON file listed in the manifest with a new type, `clips` (for example `{ "id": "anim.player", "path": "anim/player.clips.json", "type": "clips" }`). Its textures, atlases and Aseprite exports are ordinary manifest entries in the same group.
- **Sources:** each clip takes its frames from one of three sources.

| Source | Clip entry | Frames |
| --- | --- | --- |
| Atlas sequence | `"atlas": "atlas.player", "regions": "player/run"` | the atlas regions `player/run` with indexes 0..n (TexturePacker's `_0`, `_1`, ...), in index order |
| Aseprite export | `"aseprite": "ase.player", "tag": "run"` | the frames of that frame tag in an Aseprite JSON export (array or hash), with their own durations |
| Sprite sheet (strip or grid) | `"sheet": "tex.frog_run", "frameWidth": 32, "frameHeight": 32, "frames": [0, 11]` | cells of a texture, read left to right and top to bottom; `frames` is a first and last index (default: all) |

- **Per clip:** `frameDuration` (seconds; the default comes from the set's `defaults`; Aseprite clips use the file's durations unless overridden), `mode` (`loop` by default, `once`, `pingpong`), and `events` (`{ "2": "step", "6": "step" }`: frame index to event name).
- **Example:**

```json
{ "version": 1, "defaults": { "frameDuration": 0.08 },
  "clips": {
    "idle": { "sheet": "tex.frog_idle", "frameWidth": 32, "frameHeight": 32, "frameDuration": 0.05 },
    "run":  { "sheet": "tex.frog_run", "frameWidth": 32, "frameHeight": 32, "events": { "2": "step", "8": "step" } },
    "jump": { "aseprite": "ase.frog", "tag": "jump", "mode": "once" },
    "fall": { "atlas": "atlas.frog", "regions": "frog/fall" } } }
```

**Aseprite import.**
- **What is read:** the JSON export (`--format json-array` or `json-hash`) with `frameTags`, and each frame's `duration` (ms).
- **Tag direction:** `forward`, `reverse` (frames reversed), `pingpong`, `pingpong_reverse`. A tag `repeat` of `"1"` means `once`, unless the clip set overrides `mode`.
- **Trimmed frames:** with `spriteSourceSize` and `sourceSize`, frames become regions with an offset, so the character does not jump around.
- **Events:** tag user data in the form `events: 2=step, 6=step` adds frame events (frame index within the tag). The clip set's `events` override them.
- **Slices:** hitbox and pivot slices are not read in I-130.

**Atlas offsets in rendering.** `SpriteRenderSystem` now applies `AtlasRegion` offsets and original sizes (`offsetX/Y`, `originalWidth/Height`), so whitespace-stripped atlas regions and trimmed Aseprite frames draw at the right place. Before, it drew a stripped region at its packed size.

**`Animator` (component).**
- **Data fields:** `clipSet` (asset ID), `clip` (the clip to play) and `speed` (1 = normal) can be set from prefabs and Tiled.
- **Methods:** `play(clip)` restarts a clip, `playIfNotPlaying(clip)`, `isFinished()` (a `once` clip reached its end), `frame()`, `time()`.
- **Flipping:** facing stays on `Sprite.flipX`, so the spec's `Animator.flipX` is dropped.

**`AnimationSystem` (`UPDATE`).**
- **Frames:** advances every animator by the **game** delta times `speed` (paused game, paused animation), and writes the current frame's region into the entity's `Sprite`. Register it after the game systems that call `play`.
- **Changing clips:** setting `clip` directly (from data) starts that clip at its first frame on the next update.
- **Events:** frame events are published as queued `AnimationEvent(entity, clip, name)`, once each time a frame is entered. That includes frames skipped by a long delta, the first frame when a clip starts, and frames passed again when a loop wraps.
- **Ping-pong:** it does not repeat the end frames.
- **Unknown clips:** a missing clip set or clip logs one warning per entity and clip, and the sprite keeps its region.

**`AnimationLibrary`.**
- **Creation:** `new AnimationLibrary(assets)`, created by the scene, as the tile map and prefabs are.
- **Parsing:** a clip set is parsed and validated on first use, then cached.
- **Validation errors** name the file, the clip and the field:
  - unknown asset, region, tag or field
  - wrong value type
  - frame index out of range
  - event frame out of range
  - an empty clip
- **Registry:** `Animator` is registered as an engine component, so prefabs can declare `"Animator": { "clipSet": "anim.player", "clip": "idle" }`.

**Not in I-130:**
- the state machine (I-135)
- hot reload (I-150)
- Aseprite slices, per-frame hitboxes
- Spine (I-560)
- the animation view in dev tools (I-160)

## API sketch (I-130)
```java
package dev.ember2d.animation;

@PublicApi public enum PlayMode { LOOP, ONCE, PINGPONG }
@PublicApi public final class Clip {              // immutable, built by the library
    public String name(); public int frameCount(); public TextureRegion frame(int i); public float duration(int i);
    public float totalDuration(); public PlayMode mode(); public String eventAt(int frame);   // null if none
}
@PublicApi public final class ClipSet { public String id(); public Clip clip(String name); public Set<String> names(); }
@PublicApi public final class AnimationLibrary {
    public AnimationLibrary(Assets assets);
    public ClipSet clipSet(String assetId);       // AnimationException listing problems
}
@PublicApi public final class Animator {
    public String clipSet; public String clip; public float speed = 1f;
    public void play(String clip); public void playIfNotPlaying(String clip);
    public boolean isFinished(); public int frame(); public float time();
}
@PublicApi public final class AnimationSystem extends EntitySystem {   // UPDATE, family Animator + Sprite
    public AnimationSystem(World world, AnimationLibrary library, EventBus events);
}
@PublicApi public record AnimationEvent(int entity, String clip, String name) { }
@PublicApi public final class AnimationException extends RuntimeException { List<String> problems(); }
```
`ember.animation` may use `core`, `event`, `ecs`, `assets`, and `render` for `Sprite`. That last one is a tier-3 sibling, like `collision -> render` in I-120, so it needs an allowed-dependency entry, `animation -> render`, in ARCHITECTURE rule 3 and the ArchUnit map.

## Decisions in I-130
The defaults were approved as proposed, with placeholder demo art. Details settled while implementing:
- **Aseprite texture:** an Aseprite clip names its texture as well (`"aseprite": "ase.hero", "texture": "tex.hero", "tag": "run"`). The export's `meta.image` is a file path, and the asset service finds assets by ID, not by path.
- **Event names:** frame events from an Aseprite tag's user data and from the clip set are merged; for the same frame, the clip set wins.
- **`Animator` fields:** only `clipSet`, `clip` and `speed` are public fields, so they are what prefabs and Tiled can set. Playback state is package-private.
- **Test hook:** `AnimationLibrary` has a package-private `put(ClipSet)` so playback tests can use clips built in memory.
- **Architecture rule:** `animation -> render` is allowed (ARCHITECTURE rule 3 and the ArchUnit map). `Animator` is an engine component with a generated R8 keep rule.
- **Demo:**
  - The placeholder art is generated: `gfx/hero-sheet.png`, a 16×16 grid with idle 2, run 6, jump 1 and fall 1 frames, plus `anim/hero.clips.json` (run has footstep events on frames 1 and 4).
  - `PlayerControlSystem` picks idle, run, jump or fall and flips the sprite to the walking direction; patrollers play run and face their direction.
  - Footsteps are logged at DEBUG.

## Tests (I-130)
- **Playback (no window):**
  - variable frame durations
  - `loop`, `once` (`isFinished`) and `pingpong` sequences, frame by frame
  - `speed`, including 0
  - pause (game delta 0)
  - `play` versus `playIfNotPlaying`
  - changing `clip` from data
- **Events:**
  - once per frame entered
  - frames skipped by a 1-second delta all fire, in order
  - loop wrap
  - `once` clips fire their last frame once
- **Import (headless, real files in `assets-test/anim/`):**
  - an atlas sequence
  - a sprite strip and a 2-row grid
  - an Aseprite json-array and json-hash export with tags, directions, `repeat`, durations, trimmed frames and tag user-data events
  - validation errors with file, clip and field
- **Rendering:** a whitespace-stripped `AtlasRegion` draws at its offset with its original size.
- **Demo:** the player plays idle, run, jump and fall, picked by a few lines in `PlayerControlSystem` (the state machine replaces those in I-135). The patrollers play run. Footstep events are logged in dev mode. You check desktop; I check the emulator.

## State machine (I-135)
Status: Ready (approved 2026-11-26). This adds the decisions the I-135 implementation needs to the "Animation state machine (ASM)" scope and JSON example above.

**Definitions.**
- **Assets:** an ASM definition is a manifest entry of a new type, `asm` (JSON, the format shown in "API sketch" below). Definitions are parsed and validated on first use by an `AsmLibrary` (created by the scene, like the `AnimationLibrary`), then cached.
- **Clips:** a state's `clip` must exist in the definition's `clipSet`, and the clip set is checked at load.
- **Validation errors** name the file and the state, the transition index or the parameter:
  - unknown parameter, state or clip
  - unparseable condition
  - wrong value type
  - comparing a bool
  - a trigger used in a comparison
  - `initial` missing

**Conditions.**
- **Grammar:** `when` is one or more terms joined by `&&`. A term is a bool or trigger name, `!name` (bool only), or `name OP number` with `OP` in `> >= < <= == !=` and `name` a float or int parameter.
- **Not included:** `||`, parentheses and arithmetic. Two transitions express an "or".
- **Comparisons:** done in floats.

**Evaluation (`AsmSystem`, `UPDATE`).** It runs after the game systems that set parameters and before the `AnimationSystem`; the scene registers them in that order. For each entity:
1. **Finished states:** if the state has `exitOnFinish` and its clip is finished, it changes to `initial`.
2. **Candidates:** transitions from the current state and from `*`, in file order.
   - A transition is valid when its condition holds, the time in state is at least `minTime`, and, with `exitTime`, the clip's normalised time (`Animator.time() / clip.totalDuration()`) has reached `exitTime`.
   - A `*` transition never fires into the state that is already current, so `* -> fall when !grounded` does not restart `fall` every frame.
3. **Choice:** the valid transition with the highest `priority` (default 0) wins; ties go to the first in the file. At most one transition per update, plus the change to `initial` from step 1.
4. **Triggers:** they are cleared after every evaluation, whether or not a transition used them. A trigger set in a frame is seen by that frame's evaluation only; one fired now is never acted on seconds later.
5. **Entering a state:**
   - **Clip:** `Animator.play(clip)` (restart).
   - **Speed:** `Animator.speed = state.speed` (default 1), times `|speedParam|` when set. `run` with `speedParam: speedX` plays faster the faster you move, in either direction.
   - **Events:** queued `AnimationStateExited(entity, from)` and `AnimationStateEntered(entity, to)`.
   - **Time in state:** reset.
6. **While a state is active:** its `speedParam` keeps updating `Animator.speed`.

**Component `AnimationStateMachine`.**
- **Fields:**
  - `definition` (asset ID) can be set from prefabs and Tiled.
  - The parameter values and the current state are runtime state.
- **Methods:**
  - `setBool`, `setFloat`, `setInt`, `trigger` throw `IllegalArgumentException` for an unknown parameter or the wrong type.
  - `currentState()`, `timeInState()`, `lastTransition()` (a label for dev tools, I-160).
  - Parameter values set before the definition is loaded are kept and checked on first use.
- **Animator:** the entity needs an `Animator`; the ASM sets its `clipSet` from the definition.

**Reloading a definition.**
- `AsmLibrary.reload(id, json)` and `AnimationLibrary.reload(id, json)` replace a cached definition or clip set at runtime; I-150's file watcher calls them.
- Running machines switch on their next update. They keep the current state if it still exists, otherwise they go to `initial`. Parameter values are kept when the name and type still exist.
- There is no file watching in I-135.

**Not in I-135:** layers, blend trees and cross-fades (F-007 non-goals), the dev-tools panel (I-160), Spine states (I-560), file watching (I-150).

## API sketch (I-135)
```java
package dev.ember2d.animation;
@PublicApi public final class AnimationStateMachine {          // component
    public String definition;                                  // asset ID of an "asm" entry
    public void setBool(String p, boolean v); public void setFloat(String p, float v);
    public void setInt(String p, int v); public void trigger(String p);
    public boolean getBool(String p); public float getFloat(String p); public int getInt(String p);
    public String currentState(); public float timeInState(); public String lastTransition();
}
@PublicApi public final class AsmLibrary {
    public AsmLibrary(Assets assets, AnimationLibrary clips);
    public AsmDefinition definition(String id);                // AnimationException listing problems
    public void reload(String id, JsonValue json);             // for hot reload (I-150)
}
@PublicApi public final class AsmDefinition { /* immutable: parameters, states, transitions; read-only accessors */ }
@PublicApi public final class AsmSystem extends EntitySystem { // UPDATE; family AnimationStateMachine + Animator
    public AsmSystem(World world, AsmLibrary library, EventBus events);
}
@PublicApi public record AnimationStateEntered(int entity, String state) { }
@PublicApi public record AnimationStateExited(int entity, String state) { }
// AnimationLibrary gains: public void reload(String id, JsonValue json)
```

## Decisions in I-135
The defaults were approved as proposed. Details settled while implementing:
- **First update:** a machine enters `initial` in its first update and evaluates transitions in that same update. A hero that starts in the air goes straight to `fall`.
- **`exitTime`:** a finished clip always counts as reached. `exitTime` reads `Animator.time()`, which the `AnimationSystem` advances after the state machine runs, so it lags by one update.
- **`exitOnFinish` in `initial`:** it does nothing there; the machine is already in `initial`.
- **Shared clip sets:** `AsmLibrary(assets, clips)` uses the scene's `AnimationLibrary`, so the state machine and the animator share one cache.
- **Picking up reloads:** the `AnimationSystem` looks up the clip set every update and compares it by identity, so a reloaded set is picked up. A clip that got shorter restarts.
- **Registry:** `AnimationStateMachine` is an engine component; only `definition` is a public field.
- **Demo:**
  - `anim/hero.asm.json` has idle, run (`speed` 0.125 times `speedX`), jump, fall, and a one-frame `land` squash with `exitOnFinish`, fired by a `land` trigger on touchdown.
  - `PlayerControl` gained a runtime `wasGrounded` field to detect the touchdown.
  - The placeholder sheet gained the land frame (index 25).

## Tests (I-135)
- **Definition validation:** every error type in the list above, each naming the file and the transition index or state.
- **Conditions:** bool, `!bool`, every comparison on float and int, several terms joined by `&&`, and a trigger.
- **Evaluation:**
  - priority, and ties going to the first in the file
  - `*` transitions, without re-entering the current state
  - triggers cleared after every evaluation
  - `minTime` and `exitTime`
  - `exitOnFinish` returning to `initial` and re-evaluating in the same update
  - at most one transition per update
  - `speedParam` scaling `Animator.speed` (absolute value)
  - entered and exited events
- **Runtime API:** unknown parameter or wrong type; parameters set before the first update.
- **Reload:** the state is kept when it still exists and falls back to `initial` otherwise; parameters are kept by name and type; a clip set is reloaded.
- **Demo:**
  - `anim/hero.asm.json` drives idle, run, jump and fall, with `run` scaled by speed. The clip choice in `PlayerControlSystem` is replaced by setting `grounded`, `speedX` and `velY`.
  - A `land` trigger plays a one-frame squash (`exitOnFinish`) when the player touches down, which shows triggers and `exitOnFinish`.
  - Desktop is checked by you, and the emulator by me.

## Non-goals
- Blend trees, cross-fading between frame clips, inverse kinematics, root motion, animation layers and masks.
- An animation editor (Aseprite + JSON + hot reload).

## API sketch
```java
@PublicApi public final class Animator { public String clipSet; public String clip; public float speed = 1f; public boolean flipX;
    public void play(String clip); public void playIfNotPlaying(String clip); public boolean isFinished(); }

@PublicApi public final class AnimationStateMachine {   // component
    public String definitionId;
    public void setBool(String p, boolean v); public void setFloat(String p, float v);
    public void setInt(String p, int v); public void trigger(String p);
    public String currentState(); public float timeInState();
}
```

```json
{ "version": 1, "clipSet": "player",
  "parameters": { "grounded": "bool", "speedX": "float", "velY": "float", "attack": "trigger", "hurt": "trigger" },
  "initial": "idle",
  "states": {
    "idle":   { "clip": "idle" },
    "run":    { "clip": "run", "speedParam": "speedX" },
    "jump":   { "clip": "jump" },
    "fall":   { "clip": "fall" },
    "attack": { "clip": "attack", "exitOnFinish": true },
    "hurt":   { "clip": "hurt", "exitOnFinish": true } },
  "transitions": [
    { "from": "*",      "to": "hurt",   "when": "hurt", "priority": 20 },
    { "from": "*",      "to": "attack", "when": "attack", "priority": 10 },
    { "from": "idle",   "to": "run",    "when": "grounded && speedX > 0.1" },
    { "from": "run",    "to": "idle",   "when": "grounded && speedX <= 0.1" },
    { "from": "*",      "to": "jump",   "when": "!grounded && velY > 0" },
    { "from": "*",      "to": "fall",   "when": "!grounded && velY <= 0" },
    { "from": "fall",   "to": "idle",   "when": "grounded" } ] }
```
When a state with `exitOnFinish` ends, the machine returns to `initial` and re-evaluates transitions in the same frame.

## Acceptance criteria
- Frame advancement with variable frame durations; loop, once and ping-pong correct.
- Events fire once on the correct frame, including when a frame is skipped by a long delta.
- Aseprite sample (tags, durations, direction) imports correctly.
- ASM: priority, `*` transitions, triggers reset after use, `minTime`, `exitTime` and `exitOnFinish` behave as specified (unit tests with no window).
- Invalid definitions (unknown parameter, unknown clip, unparseable condition) fail validation with file and transition index.
- Demo player uses the ASM for idle/run/jump/fall/attack/hurt; VN characters use it for expressions.
- Hot reload of a definition applies without restarting.
