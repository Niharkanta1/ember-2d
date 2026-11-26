# F-007 — Animation and Animation State Machine

- Status: clips, animator and frame events Ready (I-130, approved 2026-11-26; see "Clips and animator (I-130)" and "Decisions in I-130"). The state machine (I-135): Draft.
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
