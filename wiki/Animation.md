# Animation

Package `ember.animation` (`ClipSet`, `Clip`, `PlayMode`, `Animator`, `AnimationSystem`, `AnimationLibrary`, `AnimationStateMachine`, `AsmDefinition`, `AsmLibrary`, `AsmSystem`, `AnimationEvent`, `AnimationStateEntered`, `AnimationStateExited`). Spec F-007.

## 1. Clip sets
A clip set (`"type": "clips"` in the manifest) names the frames of each clip. From the RPG demo (`anim/rpg.clips.json`):
```json
{ "version": 1, "defaults": { "frameDuration": 0.1 },
  "clips": {
    "knight_idle": { "sheet": "tex.knight_idle", "frameWidth": 16, "frameHeight": 16, "frames": [0, 5] },
    "knight_run":  { "sheet": "tex.knight_run",  "frameWidth": 16, "frameHeight": 16, "frames": [0, 5], "frameDuration": 0.08 },
    "door_closed": { "sheet": "tex.door", "frameWidth": 32, "frameHeight": 32, "frames": [0, 0] },
    "door_open":   { "sheet": "tex.door", "frameWidth": 32, "frameHeight": 32, "frames": [0, 13], "frameDuration": 0.05, "mode": "once" },
    "door_opened": { "sheet": "tex.door", "frameWidth": 32, "frameHeight": 32, "frames": [13, 13] } } }
```
- **Frame sources:**
  - a sprite sheet: `sheet` (a texture ID) with `frameWidth`/`frameHeight`; `frames` is a first-last range, in a strip or a grid;
  - an atlas sequence: `"atlas": "atlas.hero", "regions": "hero/run"`;
  - an Aseprite JSON export: `"aseprite": "ase.hero", "texture": "tex.hero", "tag": "run"`, using its own durations and trimmed frames.
- **`mode`:** `loop` (default), `once` or `pingpong`.
- **`events`:** maps a frame index to an event name, `{ "2": "step", "8": "step" }`.

## 2. `Animator`
```json
"Animator": { "clipSet": "anim.rpg", "clip": "knight_idle" }
```
```java
Animator a = world.get(e, Animator.class);
a.play("door_open");                 // restart a clip
a.playIfNotPlaying("knight_run");    // keep playing if already on it
a.isFinished();                      // a "once" clip reached its end
a.speed = 1.5f;
int frame = a.frame();
```
- **The system:** `AnimationSystem(world, clips, events)` (`UPDATE`) advances animators in game time and writes the frame into the `Sprite`.
- **Frame events:** arrive as queued `AnimationEvent(entity, clip, name)`.
- **Facing:** use `Sprite.flipX`. The top-down and platformer bodies flip it for you.

The RPG demo switches between idle and run in a few lines:
```java
TopDownBody body = world.get(player, TopDownBody.class);
world.get(player, Animator.class).playIfNotPlaying(body.moving && !body.locked ? "knight_run" : "knight_idle");
```

## 3. The animation state machine
For characters with many states, code sets **parameters** and a JSON state machine (`"type": "asm"`) picks the clip. From the platformer's frog:
```json
{ "version": 1, "clipSet": "anim.frog",
  "parameters": { "grounded": "bool", "speedX": "float", "velY": "float", "hit": "trigger" },
  "initial": "idle",
  "states": {
    "idle": { "clip": "idle" },
    "run":  { "clip": "run", "speed": 0.125, "speedParam": "speedX" },
    "jump": { "clip": "jump" },
    "fall": { "clip": "fall" },
    "hit":  { "clip": "hit", "exitOnFinish": true } },
  "transitions": [
    { "from": "*",    "to": "hit",  "when": "hit", "priority": 10 },
    { "from": "idle", "to": "run",  "when": "grounded && speedX > 0.5" },
    { "from": "run",  "to": "idle", "when": "grounded && speedX <= 0.5" },
    { "from": "*",    "to": "jump", "when": "!grounded && velY > 0" },
    { "from": "*",    "to": "fall", "when": "!grounded && velY <= 0" },
    { "from": "fall", "to": "idle", "when": "grounded" } ] }
```
```java
AnimationStateMachine asm = world.get(player, AnimationStateMachine.class);   // prefab: "AnimationStateMachine": { "definition": "asm.frog" }
asm.setBool("grounded", grounded);
asm.setFloat("speedX", Math.abs(vx));
asm.trigger("hit");
```
- **Conditions:** terms joined by `&&`: a bool or trigger name, `!bool`, or `param OP number` with `>`, `>=`, `<`, `<=`, `==`, `!=`. Two transitions make an "or".
- **Choosing a transition:** the valid transitions from the current state and from `*` (never into the current state); the highest `priority` wins, then file order. `minTime` and `exitTime` delay a transition.
- **States:** `exitOnFinish` returns to `initial` when the clip ends. `speedParam` scales playback by a parameter.
- **Triggers** clear after every evaluation.
- **Events:** state changes publish `AnimationStateEntered` and `AnimationStateExited`.

kit-platformer's `PlatformerSystem` writes the parameters `grounded`, `speedX`, `velY`, `wallSliding`, `dashing` and `climbing`, and the triggers `airJump` and `wallJump`, when your definition declares them.

### System order
```java
AnimationLibrary clips = new AnimationLibrary(context.assets());
world.addSystem(new AsmSystem(world, new AsmLibrary(context.assets(), clips), context.events()));   // first
world.addSystem(new AnimationSystem(world, clips, context.events()));                               // then
```

## 4. Hot reload and tools
- **Generate clip sets** instead of writing them: `./gradlew makeClips -Pgame=<game> -Pfrom=gfx/hero` (a folder of sprite strips) or `-Pfrom=anim/hero.json` (an Aseprite export, one clip per tag). See [Working Without an Editor](Working-Without-an-Editor#5-animation-clips).
- Saving a clip set or state machine in a dev run switches animators on their next update.
- The dev tools' **Animation** panel shows the state, the time in state and the last transition. You can change parameters and fire triggers there.

## 5. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| The animation never changes | `AnimationSystem` before `AsmSystem`, or a parameter never set | order them; set parameters every step |
| `State machine "…" has no parameter "x"` | not declared | add it under `parameters` |
| The first frames show a white shape | some sheets start with a flash frame (Pixel Adventure "Hit") | start the clip at a later frame |
| A sprite shows the whole sheet | `regionId` names the sheet and no `Animator` sets a frame | add an `Animator`, or use an atlas region |
