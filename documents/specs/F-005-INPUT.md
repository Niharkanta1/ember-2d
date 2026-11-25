# F-005 — Input Actions

- Status: Draft
- Phase: 2 (keyboard/mouse, touch actions, contexts), 3 (on-screen controls via F-022), 6 (gamepad, rebinding UI)
- Module / package: `ember-core` / `ember.input`
- Depends on: F-002, F-013 (bindings stored in settings)
- Tasks: I-100, I-530

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

## Non-goals
- Gesture recognition beyond F-022's list, input recording/replay.

## API sketch
```java
@PublicApi public interface Input {
    boolean pressed(String action); boolean justPressed(String action); boolean justReleased(String action);
    float axis(String axis); Vector2 axis2(String axis, Vector2 out);
    Vector2 pointerWorld(Vector2 out);
    void pushContext(String ctx); void popContext();
}
```
Bindings file (`input.json`):
```json
{ "version": 1, "actions": { "jump": ["SPACE", "Z"], "fire": ["MOUSE_LEFT"] },
  "axes": { "move": { "left": ["A","LEFT"], "right": ["D","RIGHT"], "up": ["W","UP"], "down": ["S","DOWN"] } } }
```

## Acceptance criteria
- Edge/hold semantics correct across frames and fixed steps, including multiple fixed steps in one frame and zero fixed steps in a frame.
- Unknown key names and unknown actions fail validation with file and name.
- UI context blocks gameplay actions it binds.
- Settings overrides applied at startup.
- Gamepad connect/disconnect handled (Phase 6).

## Risks/open questions
- Diagonal normalisation for 2D axes: default normalised; kits can opt out.
