# Time, Timers and Tweens

Package `ember.core` (`Time`, `Phase`, `Timers`, `TimerHandle`, `Tweens`, `TweenHandle`, `StateMachine`). Spec F-002.

## 1. `Time`
Read time from `context.time()`. Systems never measure time themselves.

| Method | Meaning |
| --- | --- |
| `delta()` | scaled frame delta; 0 while paused |
| `unscaledDelta()` | real frame delta |
| `fixedDelta()` | the fixed step (1/60 s by default) |
| `alpha()` | 0..1, how far the frame is between two fixed steps (render interpolation) |
| `elapsed()`, `unscaledElapsed()` | seconds since start (game time, real time) |
| `fixedTick()` | number of fixed steps run |
| `timeScale()`, `setTimeScale(0..10)` | slow motion or fast forward |
| `paused()`, `setPaused(boolean)` | game pause |
| `step()` | run one fixed step while paused (dev tools) |
| `droppedFixedSteps()` | steps dropped because frames were too slow |

Inside an `EntitySystem`, use the `delta` argument of `process`. In `FIXED` it is the fixed step, and in `UPDATE` and `LATE` the scaled frame delta.

### Phases
`Phase.INPUT`, `FIXED`, `UPDATE`, `LATE`, `RENDER`. See [Frame and Data Flow](Frame-and-Data-Flow) for when each runs.

### Pause and systems that must keep running
While paused, `UPDATE` and `LATE` systems are skipped. A system that must run anyway (a paused-screen animation) overrides:
```java
@Override
public boolean runsWhenPaused() {
    return true;   // gets the real delta in UPDATE/LATE
}
```
The UI and audio fades run on real time without this.

## 2. Timers
```java
TimerHandle blink = timers().every(0.5f, () -> sprite.tint.a = 1f - sprite.tint.a);
timers().after(2f, () -> context.scenes().replace(new TitleScene()));
blink.cancel();
```
- `timers()` inside a scene belongs to the scene and is cancelled on exit. `context.timers()` is global.
- Timers run in game time, so they freeze while paused and follow the time scale.

## 3. Tweens
```java
tweens().to(() -> sprite.tint.a, a -> sprite.tint.a = a, 0f, 0.5f, Interpolation.fade)
        .then(() -> world().destroy(enemy));
```
A tween animates any float through a getter and a setter (`FloatGetter`, `FloatSetter`) to a target over a duration, with a libGDX `Interpolation`. `then` chains an action. A `TweenHandle` can cancel it.

## 4. `StateMachine<S>`
A small helper for behaviour or flow:
```java
enum Guard { IDLE, CHASE, RETURN }

StateMachine<Guard> brain = new StateMachine<>(Guard.IDLE);
brain.onEnter(Guard.CHASE, () -> patrol.speed = 9f);
brain.onExit(Guard.CHASE, () -> patrol.speed = 4f);
brain.change(Guard.CHASE);         // exit actions of IDLE, then enter actions of CHASE
brain.update(delta);               // advances timeInState()
if (brain.timeInState() > 3f) brain.change(Guard.RETURN);
```
For sprite animation states, use the JSON [animation state machine](Animation) instead.

## 5. Best practices
- **Movement in `FIXED`:** put movement and anything that affects collision in `FIXED` systems. Their `delta` is constant, so behaviour does not depend on frame rate.
- **Teleporting:** use `transform.teleport(x, y)` when placing an entity, so it doesn't visibly slide from its old position.
- **Durations:** use timers and tweens for anything with a duration (blinks, delays, fades), not counters in `update`.
- **Slow motion:** `setTimeScale(0.3f)` for a short moment on a big hit is cheap, and everything game-timed follows it.

## 6. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| Speed depends on frame rate | moving a fixed amount per frame | multiply by `delta`; move in `FIXED` |
| Nothing moves in the pause menu's background | expected: `UPDATE`/`LATE` skip while paused | `runsWhenPaused()` for the few systems that should run |
| "Dropped N fixed step(s)" warnings | frames slower than the catch-up limit | profile with F3; reduce work per step |
| A timer fires after the scene changed | it was a global `context.timers()` timer | use the scene's `timers()` |
