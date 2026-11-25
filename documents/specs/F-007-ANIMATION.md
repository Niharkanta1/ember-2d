# F-007 — Animation and Animation State Machine

- Status: Draft
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
