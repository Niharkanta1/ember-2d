# F-005 — Input Actions

- Status: Ready for the I-100 part (keyboard, mouse, contexts, back action), approved 2026-11-26; see "Decisions in I-100". Touch (I-105, F-022), settings overrides (F-013), gamepad and rebinding UI (I-530): Draft.
- Phase: 2 (keyboard/mouse, touch actions, contexts), 3 (on-screen controls via F-022), 6 (gamepad, rebinding UI)
- Module / package: `ember-core` / `ember.input`; wiring in `ember.app`; focus handling in `ember-desktop`
- Depends on: F-002, F-008 (the bindings file is a manifest asset), F-019 (context and events), F-013 (bindings stored in settings, later)
- Tasks: I-100, I-105, I-530

## Goal
Gameplay code reads named actions and axes, never raw keys.

## Scope
- Actions (digital) and axes (1D, 2D) defined in project data.
- Keyboard and mouse bindings; mouse position in world coordinates (for aiming).
- States: `pressed`, `justPressed`, `justReleased`, `heldTime`.
- **Fixed-step safety:** a `justPressed` that happens between fixed steps is visible to the next fixed step (edge latching), so jumps are not lost at high frame rates.
- Contexts: `ui` and `gameplay` first; the top active context consumes the actions it binds.
- Bindings loaded from defaults and overridden by the settings store (F-013).
- Gamepad (gdx-controllers) and a rebinding UI in Phase 6.
- Touch actions, multi-touch pointers and on-screen controls are specified in F-022; they feed the same action/axis model, so gameplay code does not know whether input came from keys, mouse, touch or gamepad.
- Back button (Android) maps to the `back` action.

**In I-100** (from S-01 and S-02):
- A **capture hook**: an in-game UI (ImGui dev tools, later Scene2D) stops new key or mouse presses from reaching actions.
- **No stuck keys:** held inputs are released when the app is suspended or, on desktop, when the window loses focus.

**Not in I-100:**
- Settings overrides wait for F-013; there is no settings store yet.
- Touch is I-105; gamepad and rebinding are I-530.
- The default handler for `back` (pause menu or pop scene) is F-022.

## Non-goals
- Gesture recognition beyond F-022's list, input recording/replay.
- Text entry (UI widgets read characters themselves).
- Mouse wheel and pointer deltas as axes. These can be added later as binding names without API changes.
- Key repeat.

## User-facing behaviour
- **Bindings file:** a game declares its bindings in one JSON file listed in the asset manifest (group `global`) and names its asset ID in `EngineConfig.inputBindings`. The engine loads and validates the file at boot, before the first scene.
- **Reading input:** systems and scenes read `context.input()` in any phase.
- **Fixed-step edges:**
  - In `FIXED`, `justPressed` / `justReleased` are true in the **first fixed step after** the edge, even if that step comes one or more frames later (frames with zero fixed steps).
  - With several fixed steps in one frame, only the first sees the edge.
- **Frame edges:** in `INPUT`, `UPDATE`, `LATE` and `RENDER`, the edges are true in the frame in which the event arrived.
- **Taps are never lost:** a press and release inside one frame give `justPressed` and `justReleased` both true, with `pressed` false.
- **Contexts:**
  - Every action and axis belongs to exactly one context.
  - Contexts form a stack. `gameplay` is always at the bottom and cannot be popped.
  - `pushContext("ui")` puts `ui` on top. A key or mouse button bound in a context higher up is **consumed**: lower contexts do not see it, so it reads as not pressed there.
  - Actions in contexts that are not on the stack read as not pressed and axes read 0.
  - Changing the stack publishes `InputContextChanged` (immediate).
- **2D axes:** diagonals are normalised by default (`"normalize": false` opts out). Opposite directions held together cancel to 0.
- **Back button:** when any context binds `BACK`, the engine catches the Android back key so it does not close the app, and `BACK` drives that action.
- **Capture hook:** while the capture hook reports keyboard (or pointer) capture, new key (or mouse-button) presses are ignored. Releases are always applied, so nothing stays stuck.
- **Release all:** on suspend (`AppSuspended`) and on desktop window focus loss, every held input is released. Matching `justReleased` edges appear in the next frame. The game is not paused on focus loss; that stays the game's choice.

## API sketch
```java
package dev.ember2d.input;

@PublicApi
public interface Input {
    boolean pressed(String action);
    boolean justPressed(String action);     // frame view, or first-fixed-step view inside FIXED
    boolean justReleased(String action);
    float heldTime(String action);          // game seconds held (scaled, not while paused); 0 when up
    float axis(String axis);                // 1D axis: -1..1
    Vector2 axis2(String axis, Vector2 out);// 2D axis: x right, y up; length <= 1 when normalised
    Vector2 pointerWorld(Vector2 out);      // mouse position in world units (renderer camera)
    Vector2 pointerScreen(Vector2 out);     // mouse position in window pixels, y down (libGDX convention)
    void pushContext(String context);       // unknown context -> IllegalArgumentException
    void popContext();                      // popping gameplay -> IllegalStateException
    String topContext();
    boolean hasAction(String name);         // action or axis defined in the bindings
    void setCapture(InputCapture capture);  // null = nothing captures
}

@PublicApi
public interface InputCapture {
    boolean capturesKeyboard();
    boolean capturesPointer();
}

@PublicApi
public record InputContextChanged(String top) { }

/** Parsed, validated bindings; immutable. */
@PublicApi
public final class InputBindings {
    public static InputBindings parse(JsonValue json, String source);   // InputBindingsException on error
    public static InputBindings empty();
    public Set<String> contexts();
    public Set<String> actions();
    public Set<String> axes();
}

@PublicApi
public final class InputBindingsException extends RuntimeException { }
```

Internal (`dev.ember2d.input.internal`):
- `DefaultInput` implements `Input`. The engine drives it with `beginFrame(gameDelta)`, `beginFixedStep()`, `endFixedStep()`, `endFixedPhase()` and `releaseAll()`.
- `GdxInputSource` is the libGDX `InputProcessor`. It records key and button events in order, as int codes in a pooled queue.
- `InputNames` maps names to and from libGDX key and button codes.
- Unproject: `Input` cannot see `ember.render` (ARCHITECTURE: `ember.input` depends only on `ember.core`/`ember.event`). `ember.app` therefore passes in a small `ScreenToWorld` function that delegates to `Renderer.screenToWorld`.

Wiring (`ember.app`):
- `EngineContext.input()` accessor and `EngineContext.Builder.input(...)` for fakes.
- `EmberGame` sets the libGDX input processor, loads the bindings and registers suspend handling.
- `EnginePhaseRunner` calls `beginFrame` at the start of `INPUT`, and `beginFixedStep`/`endFixedStep` around each fixed step's systems.
- New `EngineConfig.inputBindings` (asset ID, default `null` = no bindings).

Desktop (`ember-desktop`):
- `DesktopWindow.focusListener(EmberGame)` returns an `Lwjgl3WindowListener` that releases inputs on focus loss.
- The demo launcher installs it with `configuration.setWindowListener(...)`.

## Data model
Bindings file (`input.json`):
```json
{ "version": 1,
  "contexts": {
    "gameplay": {
      "actions": { "jump": ["SPACE", "Z"], "fire": ["MOUSE_LEFT"], "pause": ["ESCAPE", "P", "BACK"] },
      "axes": {
        "move":  { "left": ["A","LEFT"], "right": ["D","RIGHT"], "up": ["W","UP"], "down": ["S","DOWN"] },
        "steer": { "negative": ["Q"], "positive": ["E"] } } },
    "ui": {
      "actions": { "ui_back": ["ESCAPE", "BACK"], "ui_confirm": ["ENTER", "SPACE"] },
      "axes": { "ui_nav": { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"], "down": ["DOWN"], "normalize": false } } } } }
```
- A 1D axis has `negative`/`positive`; a 2D axis has `left`/`right`/`up`/`down`, with optional `normalize`.
- **Key names** are libGDX's key names in upper case, with spaces and `-` turned into `_`: `SPACE`, `A`, `LEFT`, `L_SHIFT`, `ESCAPE`, `ENTER`, `1`, `NUMPAD_1`, `F1`, `BACK`, and so on (built from `Input.Keys.toString`, so no reflection). Mouse buttons are `MOUSE_LEFT`, `MOUSE_RIGHT`, `MOUSE_MIDDLE`, `MOUSE_BACK` and `MOUSE_FORWARD`.
- **Names are global:** names (actions plus axes) are unique across all contexts, so a query never needs a context.
- **Contexts:** `gameplay` must exist; other contexts are free-form names.

## Dependencies
- `ember.core` and `ember.event`.
- libGDX `Input`, `InputProcessor`, `utils` (`JsonValue`, `IntArray`) and `math.Vector2`.
- `ember-desktop`: libGDX LWJGL3 backend (`Lwjgl3WindowListener`).
- Nothing new in the dependency register.

## Lifecycle/resource ownership
- **Ownership:** `EmberGame` owns `DefaultInput` and `GdxInputSource`. Input is engine state, not a game service.
- **Order:** bindings load after the global asset group and before the first scene.
- **Processor:** the input processor is set at boot. If the game wants other processors (Scene2D in F-012), it gets an `InputMultiplexer` hook then; that is not part of I-100.
- **Shutdown:** the processor is cleared.
- **No GL or native resources.**

## Error handling
- **Bindings file:** problems throw `InputBindingsException` at boot, naming the file and the path inside it (`input.json: contexts.gameplay.actions.jump[1]: unknown key name "SPCE"`). The engine's boot failure path reports it. Problems include:
  - unknown key or button names
  - a missing `gameplay` context
  - a name used twice
  - an unknown axis direction field
  - a wrong value type
  - an unsupported `version`
- **Configuration:** an `EngineConfig.inputBindings` ID that is not in the manifest fails `EngineConfig`/boot validation with the ID.
- **Runtime queries:** querying an undefined action or axis throws `IllegalArgumentException` naming it and listing the defined ones. It is a programmer error, and failing fast catches typos.
- **Context stack:** `pushContext` of an unknown context and `popContext` on `gameplay` throw.

## Performance considerations
- **Allocation:** zero allocation per frame. Names resolve through an `ObjectIntMap` to an index; per-index state lives in primitive arrays; events are queued in an `IntArray`.
- **Lookup cost:** query cost is one hash lookup. Hot code can keep using string names; an interned handle API is not planned.
- **Per-frame work:** proportional to the number of events plus the number of actions.

## Worked example
```java
public final class PlayerControlSystem extends EntitySystem {   // runs in FIXED
    private final Vector2 move = new Vector2();
    @Override protected void process(EntityRef e, EngineContext ctx) {
        Input in = ctx.input();
        in.axis2("move", move);
        body(e).vx = move.x * RUN_SPEED;
        if (in.justPressed("jump") && body(e).grounded) body(e).vy = JUMP_SPEED;   // never lost between steps
    }
}
// Opening a pause menu: arrows and ESCAPE now belong to the menu, gameplay sees them as released.
ctx.input().pushContext("ui");
```

## Acceptance criteria
- Edge/hold semantics correct across frames and fixed steps, including multiple fixed steps in one frame and zero fixed steps in a frame.
- Unknown key names and unknown actions fail validation with file and name.
- UI context blocks gameplay actions it binds.
- Settings overrides applied at startup. *(Moved to F-013's task: no settings store exists yet.)*
- Gamepad connect/disconnect handled (Phase 6).
- **I-100 additions:**
  - A tap inside one frame is seen.
  - The capture hook blocks new presses but not releases.
  - Suspend and focus loss release held inputs.
  - The Android back key is caught when bound.
  - `pointerWorld` matches `Renderer.screenToWorld`.
  - Zero allocation per frame in steady state.

## Tests
- **Unit (`DefaultInput`, driven by a fake event feed and explicit loop calls):**
  - frame and fixed edges with 0, 1 and 3 fixed steps per frame
  - tap within a frame
  - `heldTime` with pause and time scale
  - 1D/2D axes, cancel and normalise
  - context push/pop, consumption and the event
  - capture hook
  - release all
  - unknown queries
- **Unit (`InputBindings`):**
  - a valid file
  - every validation error, with its message path
  - key-name table round trip and no duplicate names
- **App (headless):**
  - `context.input()` present
  - phase wiring: `justPressed` in `FIXED` versus `UPDATE` through a real `GameLoop` with a fake clock
  - `AppSuspended` releases
  - bad bindings ID fails boot
- **Architecture:** the existing ArchUnit rules cover the new package; the Android API guard runs on the build.
- **Demo:** `demo-platformer` gets `input.json`:
  - `move` moves the placeholder sprite
  - `pause` toggles game pause
  - `ESCAPE` opens a small overlay scene that pushes the `ui` context, which visibly blocks movement
  - developer check on desktop; Android back on the emulator

## Decisions in I-100
Approved defaults: fixed-step edge latching, one context per action, consumption by physical input, game-time
`heldTime`, fail-fast unknown names, libGDX key names, capture hook, release on suspend and focus loss, settings
overrides moved to F-013, F3 debug key left as a raw key.

Additions and changes made while implementing:
- **`Input.releaseAll()` is public:** the desktop focus listener (in `ember-desktop`) needs it, and games can use it, for example when a cutscene starts.
- **Paused game time:**
  - While paused, no edges are kept for the fixed step, because none runs.
  - A key pressed during a pause is not replayed as `justPressed` in the first fixed step after unpausing.
  - The frame edges (UPDATE) still show it.
- **Inputs held across a context change** count as up for their new owner until they are released and pressed again.
  - Opening a menu with ESCAPE therefore does not also trigger the menu's own ESCAPE action.
  - Closing a menu while holding an arrow does not move the player until the arrow is pressed again.
  - The old owner sees a `justReleased`.
- **Mouse buttons:**
  - Only pointer 0 drives mouse-button bindings; other touch pointers are left for I-105.
  - A cancelled touch counts as a release.
- **Test hooks:** tests set bindings with `EngineContext.Builder.inputBindings(...)`. There is no `Builder.input(fake)`, because the engine must drive its own `DefaultInput`; tests feed events through `DefaultInput.source()`.
- **Config checks:** `EngineConfig.inputBindings` must name a manifest entry of type `json`. Otherwise boot fails with `EngineConfigException`.
- **Demo:** the menu is a second input context inside `DemoScene` (Escape opens it; a cursor moves with the same keys), not an overlay scene. `SceneManager` runs and draws only the top scene, so an overlay would hide the game.
- **Desktop:** `DesktopWindow.focusListener(game)` is installed by the demo launcher with `setWindowListener`.

## Risks/open questions
- Diagonal normalisation for 2D axes: default normalised; kits can opt out (`"normalize": false`).
- **Input processor ownership:** when Scene2D arrives (F-012), the processor becomes a multiplexer. The capture hook keeps that change small.
- **Debug key:** the F3 debug toggle still reads `Gdx.input` directly. Moving it onto actions is not part of I-100.
- **Focus regained:** losing focus can still drop fixed steps (S-01). I-100 only releases inputs; no accumulator reset on focus regained.
