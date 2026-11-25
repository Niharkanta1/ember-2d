# F-002 — Time, Game Loop, Timers and Tweens

- Status: `Time` and game loop Ready (approved in I-020, 2026-11-26; see "Decisions in I-020"). Timers, tweens and `StateMachine`: expanded, API awaiting approval in I-025
- Phase: 1
- Module / package: `ember-core` / `ember.core`
- Depends on: F-001
- Tasks: I-020, I-025

## Goal
Centralise frame timing and fixed-step scheduling so movement is frame-rate independent and rendering is smooth on any refresh rate; provide timers, tweens and a small state-machine helper used by every genre.

## Scope
- `Time`: frame delta, fixed delta, elapsed time, time scale, game pause, interpolation alpha.
- Fixed-step accumulator with bounded catch-up and delta clamping.
- Phase scheduling hooks used by the ECS (F-003): `input`, `fixed` (0..N), `update`, `late`, `render`.
- Injectable clock for tests.
- `Timers`, `Tweens`, `StateMachine<S>` helpers.

## Non-goals
- Cross-machine deterministic simulation or replays.
- Variable fixed step.
- Coroutines or async scripting.

## User-facing behaviour
- Movement written in `fixed` systems uses `time.fixedDelta()` and runs at a stable rate.
- Rendering interpolates between the previous and current fixed state using `time.alpha()`.
- Setting `time.setPaused(true)` stops `fixed` and `update` phases for scaled systems; `render` and UI keep running.
- Systems that must run during pause (pause menu, dev tools) declare `runsWhenPaused = true` and use `time.unscaledDelta()`.

## API sketch
```java
@PublicApi
public interface Time {
    float delta();            // scaled frame delta, 0 when paused
    float unscaledDelta();    // clamped real frame delta
    float fixedDelta();       // constant, e.g. 1/60
    float alpha();            // accumulator / fixedDelta, in [0,1)
    double elapsed();         // scaled game time
    double unscaledElapsed();
    long fixedTick();         // number of fixed steps since boot
    float timeScale();  void setTimeScale(float scale); // 0..10
    boolean paused();   void setPaused(boolean paused);
}

public final class GameLoop {                    // internal; driven by EmberGame
    public GameLoop(Clock clock, TimeState time, PhaseRunner runner, float maxFrameDelta, int maxCatchUp);
    public void frame();                         // one libGDX render() call
    public void resetAccumulator();              // called on app resume
}

@PublicApi
public final class Timers {
    public TimerHandle after(float seconds, Runnable action);
    public TimerHandle every(float seconds, Runnable action);
    public void cancel(TimerHandle handle);
}

@PublicApi
public final class Tweens {
    public TweenHandle to(FloatGetter get, FloatSetter set, float target, float seconds, Interpolation ease);
    public void cancel(TweenHandle handle);
}

@PublicApi
public final class StateMachine<S extends Enum<S>> {
    public StateMachine(S initial);
    public void onEnter(S state, Runnable r);  public void onExit(S state, Runnable r);
    public void change(S next);  public S current();  public float timeInState();
    public void update(float delta);
}
```
`Interpolation` is libGDX's `com.badlogic.gdx.math.Interpolation` (ADR-013).

## Data model
Defaults from `EngineConfig`: `fixedStep = 1/60`, `maxCatchUpSteps = 5`, `maxFrameDelta = 0.25 s`.

## Dependencies
`ember.core`; libGDX math.

## Lifecycle/resource ownership
`Time`, `Timers` and `Tweens` are owned by `EngineContext`. Scene-scoped timers and tweens are cancelled when their scene exits.

## Error handling
- `setTimeScale` outside 0..10 throws `IllegalArgumentException`.
- If catch-up is capped, the dropped time is discarded and a debug counter (`droppedFixedSteps`) increments; a warning is logged at most once per second.

## Performance considerations
No allocation per frame. Timers and tweens are pooled.

## Worked example
Frame algorithm:
```
raw = clock.delta(); d = min(raw, maxFrameDelta)
time.unscaledDelta = d; time.delta = paused ? 0 : d * scale
runPhase(input)
acc += time.delta; steps = 0
while (acc >= fixed && steps < maxCatchUp) { runPhase(fixed); acc -= fixed; steps++; fixedTick++ }
if (steps == maxCatchUp && acc >= fixed) { dropped++; acc = acc % fixed }
alpha = acc / fixed
runPhase(update); runPhase(late); runPhase(render)
```

## Acceptance criteria
- With a fake clock at 1/60 s per frame, exactly 1 fixed step per frame.
- At 1/144 s per frame, fixed steps occur on the right frames and `alpha` progresses correctly.
- A 1 s hitch yields at most 5 fixed steps and a dropped-step count.
- Pause stops scaled phases but not `render`; `runsWhenPaused` systems still run.
- Timers fire on the correct tick; cancelled timers never fire; scene exit cancels scene timers.
- Tweens reach their target exactly at the end time.
- `StateMachine` calls exit then enter on change and reports time in state.

## Tests
Unit tests with a fake `Clock` for all criteria; no window required.

## Decisions in I-020
- `Phase` is a public enum in `ember.core` (`INPUT, FIXED, UPDATE, LATE, RENDER`), because the loop in `ember.core` must name phases and `ember.core` may not depend on `ember.ecs` (ARCHITECTURE.md, rule 1). F-003 uses it.
- `Time` gains `droppedFixedSteps()` (debug counter for the overlay, F-014). It counts whole fixed steps discarded when a frame needs more than `maxCatchUpSteps`; a warning is logged at most once per second of unscaled time.
- The loop is internal (`ember.core.internal.GameLoop`, `Clock`, `TimeState`, `PhaseRunner`). The accumulator counts whole nanoseconds, so step timing is exact; `Time` reports seconds as floats. Frame deltas come from `Gdx.graphics.getDeltaTime()` (`GdxClock`); tests use a fake clock.
- Frame delta clamp: `GameLoop.MAX_FRAME_DELTA_NANOS` = 0.25 s; negative deltas count as 0.
- On app resume the accumulator is reset and the next frame's delta is ignored, so a resumed game never runs a catch-up burst.
- While paused (or at time scale 0) `UPDATE` and `LATE` still run with `delta() == 0`; skipping systems that are not `runsWhenPaused` is done by the ECS (I-040).
- `EngineContext.time()` exposes the engine's `Time`; `EmberGame.render()` drives the loop.

## Risks/open questions
- Should timers run on `fixed` or `update`? Default: `update` with scaled delta; a `fixed` variant can be added if a kit needs it.
