# F-022 — Android Platform and Touch Input

- Status: Platform launcher part Ready (I-015, 2026-11-25); back action done in I-100 (F-005); touch input Ready (I-105, approved 2026-11-26, see "Decisions in I-105"); basic on-screen controls Ready (I-122, approved 2026-11-26; see "Basic on-screen controls (I-122)" and "Decisions in I-122"); context loss (I-060), screen handling and on-screen controls: Draft
- Phase: 1 (Android launcher, smoke build), 2 (touch actions), 3 (on-screen controls, Android demo build)
- Module / package: `ember-android`; `ember-core` / `ember.input`, `ember.render`, `ember.ui`
- Depends on: F-001, F-004, F-005, F-012, F-013, F-020
- Tasks: S-04, I-015, I-105, I-225, I-255
- Decisions: ADR-019, ADR-015

## Goal
Every Ember2D game can ship on Android with the same game code: touch controls, correct lifecycle and screen handling, and a reliable build.

## Scope
**Platform (`ember-android`)**
- Android launcher (`AndroidApplication`) configured from `EngineConfig`; immersive full-screen; orientation per game (landscape default, portrait for novels).
- App lifecycle: Android `onPause`/`onResume` map to F-001 `suspend`/`resume` (game pause state unchanged, accumulator reset, audio paused).
- **Graphics context loss:** on resume after context loss, recreate the pixel-perfect `FrameBuffer`, shaders and any GL objects not managed by `AssetManager`; managed textures reload automatically. A `ContextRestored` event is published.
- Back button: mapped to an input action (`back`), default handler opens the pause menu or pops the scene.
- Storage: saves and settings use libGDX local storage (app-private).
- Build: APK for testing, AAB for release (F-020); R8 enabled with keep rules generated from the component registry; minimum API level chosen in I-015.

**Screen handling (`ember.render`, `ember.ui`)**
- Safe-area insets (notches, cut-outs, gesture bars) exposed through `Renderer.safeArea()`; UI layouts respect it.
- Pixel-perfect mode with aspect ratios from 4:3 to 21:9: integer scaling with letterbox, or "fill" mode that extends the visible world area horizontally (per game setting).
- UI scale setting with a sensible default from screen density.

**Touch input (`ember.input`)**
- Touch actions: tap, double tap, long press, swipe (direction), drag; available as actions like keyboard input.
- Pointer helpers: touch positions in world and UI coordinates, multi-touch (up to 5 pointers).
- **On-screen controls** (`ember.ui` widgets bound to input actions): virtual joystick (fixed or floating), buttons, D-pad; opacity, size and position configurable and saved in settings; auto-hidden when a gamepad or keyboard is used.
- Haptics: short vibration helper (optional, setting-controlled).

## Launcher design (I-015)
**API sketch** (`ember-android`, `dev.ember2d.android`):
```java
@PublicApi public abstract class EmberAndroidLauncher extends AndroidApplication {
    protected abstract EngineConfig config(boolean devMode);      // the only method a game implements
    protected AndroidApplicationConfiguration androidConfig();    // immersive, no accelerometer/compass
    protected final boolean isDebuggable();
}
```
- `onCreate` sets the log level (debug builds: DEBUG, release: INFO; output to logcat), starts `new EmberGame(config(devMode))` and logs package and version. Dev mode is the app's debuggable flag.
- Android `onPause`/`onResume` reach `EmberGame` as suspend/resume through libGDX; Back currently finishes the activity, which disposes the engine. The `back` action replaces this in I-100.
- A game's Android launcher is an application module (`demos/<demo>/android`) with a five-line `AndroidLauncher`, landscape by default, minSdk 23, compile and target SDK 36. libGDX natives are extracted for `arm64-v8a`, `armeabi-v7a` and `x86_64`.
- Exposes Android types in its API: it is a platform module, like `ember-desktop`; game code never sees them.

**Verified by** a scripted `adb` smoke test on the API 23 emulator: launch, Home (suspend), reopen (resume), Back (shut down), with the engine log lines checked in logcat.

## Touch input design (I-105)
Status: Ready (approved 2026-11-26). On-screen controls, UI coordinates and haptics stay with I-225 / F-012.

**Model.**
- Touch feeds the same action model as keys (F-005): gestures are **binding names** in `input.json`, so an action can be bound to keys and gestures together.
- A small engine recogniser turns touch events into these inputs. It uses no libGDX `GestureDetector`, because that relies on `Timer`, a background thread.
- The recogniser runs at the start of `INPUT` on unscaled real time, so gestures work while the game is paused (menus).

| Binding name | Kind | Meaning |
| --- | --- | --- |
| `TOUCH` | held | Any finger is down |
| `TAP` | momentary | A finger lifted within `tapTime`, and never moved beyond `tapSlop` |
| `DOUBLE_TAP` | momentary | A second tap within `doubleTapTime` and `tapSlop` of the first. The first tap still fires `TAP`; there is no delay |
| `LONG_PRESS` | held | Held from `longPressTime` (finger within `tapSlop`) until the finger lifts; a long press is never also a `TAP` |
| `DRAG` | held | A finger moved beyond `tapSlop` and is still down |
| `SWIPE_LEFT` / `_RIGHT` / `_UP` / `_DOWN` | momentary | A finger lifted after moving at least `swipeDistance` within `swipeTime`; the direction is the dominant axis (up = screen up) |

- **Momentary inputs** behave like a tap of a key inside one frame: `justPressed` and `justReleased` are both true and `pressed` is false. They are latched for the next fixed step like any edge.
- **Per finger:** every finger (up to 5 pointers) is recognised on its own, so two fingers tapping give two `TAP` edges, and contexts and the capture hook apply as for keys.

**Thresholds** go in an optional `touch` section of `input.json`. Distances are in density-independent pixels (dp, scaled with `Gdx.graphics.getDensity()`) and times in seconds:
```json
"touch": { "tapSlop": 10, "tapTime": 0.3, "doubleTapTime": 0.3, "longPressTime": 0.5, "swipeDistance": 48, "swipeTime": 0.3 }
```
These values are the defaults; unknown fields or values that are not positive fail validation.

**Pointer API** (additions to `Input`):
- Frame view only; fixed-step code should use actions.
- Pointers are libGDX pointer indexes 0..4; others are ignored.
```java
int maxPointers();                              // 5
boolean pointerDown(int pointer);
boolean pointerJustDown(int pointer);           // this frame
boolean pointerJustUp(int pointer);             // this frame
Vector2 pointerScreen(int pointer, Vector2 out);// window pixels, y down
Vector2 pointerWorld(int pointer, Vector2 out); // world units
Vector2 dragDelta(Vector2 out);                 // world units moved this frame by the lowest dragging finger; 0 if none
```
The existing `pointerScreen(out)` and `pointerWorld(out)` stay as pointer 0 (mouse or first finger).

**Mouse and touch.**
- **Desktop:** libGDX reports the mouse as pointer 0, so mouse clicks and drags also produce the touch inputs. Touch controls can then be tested without a device.
- **Android:** a finger reports as button 0, so on Android touches do **not** drive the `MOUSE_*` bindings; only the touch names apply there.

**Capture and release.**
- **Capture:** `capturesPointer()` ignores new touches.
- **Release:** `releaseAll()` ends every touch without producing gestures.

**Tests.**
- **Unit:** each gesture from scripted events and times, including the edge cases:
  - a tap that becomes a drag
  - a long press that is not a tap
  - a double tap outside the slop counted as two taps
  - a slow swipe treated as a drag only
  - two fingers recognised independently
  - momentary edges latched for the fixed step
  - capture, release all and threshold validation
- **Android emulator (API 23):** `adb shell input tap`, `input swipe` (swipes and long press) and a manual double tap, checked through the demo.
- **Real device:** multi-touch can only be checked by hand on a real device and stays open.

**Demo.**
- `TAP` moves the player to the tapped point, showing `pointerWorld`.
- `DOUBLE_TAP` toggles pause.
- `LONG_PRESS` opens the menu context, and `TAP` closes it.
- Desktop and Android use the same bindings.

**Not in I-105.** Pinch and rotate gestures (non-goal), gestures limited to screen regions (on-screen controls, I-225), UI coordinates (F-012), haptics.

**Decisions in I-105.** The defaults were approved as proposed. Details settled while implementing:
- **Primary button only:** only the primary button makes a touch. On desktop, right and middle clicks drive `MOUSE_*` bindings but no gestures.
- **Swipes and drags:** a swipe is also a drag. `DRAG` goes down when the finger leaves the slop and up on lift, so a fast swipe inside one frame gives a `DRAG` edge and the `SWIPE_*` edge.
- **Long press then move:** a long press that later moves keeps `LONG_PRESS` held until the lift, and also starts a `DRAG`.
- **Same-frame taps:** taps of two fingers in the same frame merge into one `TAP` edge, because an action edge is a single flag per frame. Taps in different frames give separate edges.
- **Double-tap pairs:** a third quick tap starts a new pair, so it is a `TAP` and not a second `DOUBLE_TAP`.
- **Pointer positions:** `pointerScreen(pointer, out)` and `pointerWorld(pointer, out)` give the last position of that finger, also after it lifts. `pointerScreen(out)` and `pointerWorld(out)` give the mouse position, including hover.
- **Thresholds:** the `touch` thresholds are `TouchThresholds` (public record) from `InputBindings.touch()`.
- **Screen density:** the engine sets the density from `Gdx.graphics.getDensity()` at boot.
- **Mouse buttons:** `MOUSE_*` are switched off when the app type is Android or iOS.
- **Demo:**
  - `tap` (TAP) walks the player to the tapped point; keys cancel the walk.
  - `pause` adds DOUBLE_TAP and `menu` adds LONG_PRESS.
  - `ui_back` adds TAP.
  - Desktop mouse clicks behave the same way.

## Basic on-screen controls (I-122)
Status: Ready (approved 2026-11-26).
- **Why now:** pulled forward from I-225 by the developer (2026-11-26) so every Phase 2 feature can be played on Android.
- **Engine-drawn:** I-122 draws the controls itself, with no Scene2D and no assets.
- **Left for I-225:**
  - styled widgets, a D-pad
  - dragging controls and saving the layout in settings
  - hiding when a keyboard or gamepad is used
  - safe-area insets

**Model.** On-screen controls are another input source of `ember.input`, next to keys and gestures:
- **Joystick:** drives a **2D axis** with an analog value of length 0–1 after the dead zone. While the stick is held, `axis2("move")` returns the stick value instead of the key value (keys still work when the stick is idle).
- **Buttons:** each one drives an **action**, exactly like a key bound to that action: `pressed`, `justPressed` (latched for the fixed step), `justReleased`, `heldTime`. The action's context rules apply.
- **Touches on a control:** a touch that starts on a control belongs to it until it lifts. It produces no gestures (`TOUCH`, `TAP`, `DRAG`, swipes), so pressing a button never also fires a `TAP`. The pointer API still reports it.
- **Multi-touch:** walking with the joystick and pressing a button work at the same time.

**Configuration** goes in an optional `touchControls` section of `input.json`. Sizes are in dp; button positions are measured from the bottom-right corner of the screen:
```json
"touchControls": {
  "opacity": 0.35,
  "joystick": { "axis": "move", "mode": "floating", "radius": 56, "deadZone": 0.2 },
  "buttons": [
    { "action": "jump",  "label": "A",  "x": 72,  "y": 72,  "radius": 40 },
    { "action": "pause", "label": "II", "x": 56,  "y": -56, "radius": 24 } ] }
```
- **Joystick placement:**
  - `floating` (default): the stick appears where the thumb lands anywhere in the **left half** of the screen.
  - `fixed`: it stays at `"x"`/`"y"` (dp from the bottom-left).
  - While idle, a faint base is drawn at the default position (`x`/`y`, default 96, 96).
- **Button positions:** a negative `y` counts from the top edge, so the pause button above sits at the top right.
- **Labels:** optional short text.
- **Validation:** unknown actions, axes or fields, a 1D axis for the joystick, values that are not positive, and more than 8 buttons all fail with the file and the path, like the rest of the bindings.

**When shown.** `EngineConfig.touchControls`:
- `AUTO` (default): shown on Android and iOS
- `ALWAYS`: also on desktop, where the mouse is pointer 0, so the controls can be tried without a phone
- `NEVER`

They are hidden while no `touchControls` section exists. They are drawn above the game and the debug overlay, in screen pixels, with circles of the chosen opacity (a pressed button or held stick is brighter) and labels in libGDX's default font.

**Not in I-122:** a D-pad, swipe areas, controls inside the world, vibration, and controls per input context (a menu context sees the same buttons; actions of contexts not on top read as up anyway, by F-005's rules).

## API sketch (I-122)
```java
package dev.ember2d.input;
@PublicApi public record TouchControlsConfig(float opacity, Joystick joystick, List<Button> buttons) {   // from InputBindings.touchControls()
    public record Joystick(String axis, boolean floating, float x, float y, float radius, float deadZone) { }
    public record Button(String action, String label, float x, float y, float radius) { }
}
// Input gains (read-only, for drawing and tests):
boolean touchControlsActive();                     // shown and configured
Vector2 joystickValue(Vector2 out);                // current stick value, zero when idle
package dev.ember2d.app;
public enum TouchControlsMode { AUTO, ALWAYS, NEVER }   // EngineConfig.touchControls
```
Internals:
- `input.internal.TouchControls` does hit tests, stick tracking, button state and pointer claiming, and runs before gesture recognition.
- `render.internal.TouchControlsOverlay` draws (`render` may use `input`, tier 2).
- `EmberGame` draws the overlay after the world image and the debug panel.

## Decisions in I-122
The defaults were approved as proposed. Details settled while implementing:
- **Pointer API:** a touch claimed by a control is removed from the touch events before gesture recognition, so the pointer API (`pointerDown(i)` and so on) does not report it either. The spec first said the pointer API would still report it.
- **Contexts:** the joystick's value replaces the key value of its axis only while the axis's context is on top of the context stack. A pushed menu context sees `move` as 0.
- **Input codes:** buttons press their action through internal input codes after the touch names. They have no name, so `input.json` cannot bind them except through `touchControls`.
- **Window size:** the controls are positioned in the window's logical size (the touch coordinate space). `EmberGame` passes it at boot and on every resize.
- **Drawing:** `TouchControlsOverlay` lives in `ember.app`, the composition root, and is created on first use. It draws after the world image and the debug panel, with a `ShapeRenderer` and libGDX's default font. It is not unit-tested, because the shape renderer needs a real GL context; it is checked on the emulator.
- **Demo:**
  - `input.json` has a floating joystick, button "A" for `jump` (bottom right) and button "II" for `pause` (top right). `jump` is no longer bound to `TAP`.
  - The desktop launcher accepts `--touch-controls` (sets `ALWAYS`).

## Tests (I-122)
- **Unit:**
  - stick value, dead zone, clamping and the floating origin
  - a touch in the right half does not start the stick
  - fixed mode
  - button hit test, press, release and fixed-step latching
  - stick and button at the same time with two fingers
  - claimed touches give no `TAP` and no `DRAG`
  - keys still drive the axis while the stick is idle
  - validation errors
  - `AUTO`/`ALWAYS`/`NEVER`
- **Overlay:** the draw calls for idle, held and pressed states, through the recording batch.
- **Emulator (I drive it with `adb`):**
  - a long `input swipe` on the left half walks the player right
  - a tap on the jump button jumps
  - so walking into the glow zone and riding the platform are finally checked on Android
- **Desktop with `ALWAYS`:** you check with the mouse.

## Non-goals
- iOS (ADR-019). Google Play services, ads, in-app purchases, cloud saves (game-level integrations, later).
- Gesture recognition beyond the list above.

## Acceptance criteria
- `demo-platformer` runs on an Android device and the minimum-API emulator; suspend/resume and context loss leave the game intact.
- On-screen joystick and jump button control the platformer; floating joystick works for the shooter.
- Safe areas respected on a notched device or emulator profile.
- Back button opens the pause menu.
- Release AAB builds with R8; JSON prefabs and saves load correctly in the minified build.
- No `ember-devtools` classes in Android builds.

## Risks/open questions
- Performance budget on low-end devices: measure `demo-shooter` bullet stage on a mid-range phone (target 60 fps, 3,000 bullets).
