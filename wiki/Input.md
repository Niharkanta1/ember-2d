# Input

Package `ember.input` (`Input`, `InputContextChanged`, `TouchLayout`). Specs F-005, F-022. Games read **named actions and axes**, never raw keys, so the same code works with keyboards, mice, touch screens, on-screen controls and gamepads.

## 1. The bindings file
Listed in the manifest (`"id": "input"`, type `json`) and named in the config (`config.inputBindings = "input"`). From the shooter demo:
```json
{ "version": 1, "contexts": {
    "gameplay": {
      "actions": { "fire": ["MOUSE_LEFT", "SPACE"], "reload": ["R"], "pause": ["P"], "menu": ["ESCAPE", "BACK"] },
      "axes": {
        "move": { "left": ["A"], "right": ["D"], "up": ["W"], "down": ["S"] },
        "aim":  { "left": ["LEFT"], "right": ["RIGHT"], "up": ["UP"], "down": ["DOWN"] } } },
    "ui": {
      "actions": { "ui_back": ["ESCAPE", "BACK", "P"], "ui_confirm": ["ENTER", "SPACE"] },
      "axes": { "ui_nav": { "left": ["LEFT", "A"], "right": ["RIGHT", "D"], "up": ["UP", "W"], "down": ["DOWN", "S"] } } } },
  "touchControls": {
    "opacity": 0.35,
    "joystick": { "axis": "move", "mode": "floating", "radius": 52, "deadZone": 0.2 },
    "aimStick": { "axis": "aim", "x": 150, "y": 110, "radius": 52, "deadZone": 0.15, "fire": "fire", "fireAt": 0.5 },
    "buttons": [ { "action": "reload", "label": "R", "x": 48, "y": 150, "radius": 28 },
                 { "action": "pause", "label": "II", "x": 48, "y": -48, "radius": 24 } ] } }
```
- **Bindings:** libGDX key names in upper case (`SPACE`, `L_SHIFT`, `F1`, `BACK`), mouse buttons (`MOUSE_LEFT`, …), touch gestures (`TOUCH`, `TAP`, `DOUBLE_TAP`, `LONG_PRESS`, `DRAG`, `SWIPE_LEFT`/`RIGHT`/`UP`/`DOWN`) and gamepad inputs (`PAD_A`, `PAD_START`, `PAD_LSTICK_LEFT`, …; section 5).
- **Axes:** a 1D axis has `negative`/`positive`, a 2D axis `left`/`right`/`up`/`down`. Diagonals are normalised unless `"normalize": false`.
- **Names:** action and axis names are unique across all contexts.
- **Checks:** the file is checked at boot, and every problem is listed with its path.
- **Kits:** their systems read fixed names. kit-platformer reads `move`, `jump` and `dash`. kit-topdown reads `move`, `aim` and the weapon's actions. The RPG reads `interact`. The UI reads `ui_nav`, `ui_confirm` and `ui_back`.

## 2. Reading input
```java
Input input = context.input();
if (input.justPressed("jump")) { ... }
boolean held = input.pressed("fire");
boolean released = input.justReleased("jump");
float holdTime = input.heldTime("jump");
float x = input.axis("steer");                  // 1D, -1..1
Vector2 move = input.axis2("move", tmp);        // 2D, length <= 1, y up
Vector2 mouse = input.pointerWorld(tmp);        // pointer in world units
if (input.hasAction("fire") && input.justPressed("fire")) { ... }   // optional actions
```
- **In `FIXED` systems:** `justPressed` and `justReleased` are true in the **first fixed step after** the press, so taps are never missed at high frame rates.
- **Elsewhere:** they are true in the frame of the press.
- **Unknown names:** asking for an action that isn't bound throws "Unknown input action". Guard optional actions with `hasAction`.
- **Pointers:** `pointerDown(i)`, `pointerJustDown(i)`, `pointerScreen(i, out)` and `pointerWorld(i, out)` give multi-touch access. `dragDelta(out)` gives the drag movement.

## 3. Contexts
Contexts form a stack, with `gameplay` at the bottom:
```java
input.pushContext("ui");      // a menu opens
input.popContext();           // it closes
String top = input.topContext();
```
- **Visibility:** only actions of contexts on the stack can be pressed, and a key bound in a higher context is hidden from lower ones.
- **Held keys:** keys held across a change count as up until pressed again, so the key that opened a menu doesn't also act in it.
- **Events:** changing the stack publishes `InputContextChanged`.
- **Release:** suspend and desktop focus loss release every held input.
- **Typical use:** push `ui` for menus, conversations and cutscenes. Pop back to `gameplay` in the scene's `exit()`.

## 4. On-screen controls
The `touchControls` section of the bindings file:
| Control | Fields | Placement |
| --- | --- | --- |
| `joystick` | `axis`, `mode` (`floating` or fixed), `radius`, `deadZone` | floats where the left thumb lands |
| `dpad` | `axis`, `x`, `y`, `radius`, `diagonals` | dp from the bottom left (replaces the joystick) |
| `aimStick` | `axis`, `x`, `y`, `radius`, `deadZone`, `fire`, `fireAt` | floats in the right half; past `fireAt` of its radius it holds `fire` |
| `buttons[]` | `action`, `label`, `x`, `y`, `radius` | dp from the **bottom right**; negative `y` counts from the top |

- **When they show:** `EngineConfig.touchControls = AUTO` shows them on phones and hides them while a keyboard or gamepad is used. `ALWAYS` and `NEVER` force them. Desktop dev runs can pass `--touch-controls`.
- **Contexts:** controls show only while their actions' context is on top, so they hide in menus.
- **Run-time changes:** `input.setTouchButtonVisible("dash", body.dash)` hides one button. The platformer shows its dash button only on levels with dash.
- **Layout editor:** `TouchLayoutEditor.open(ui, input, onDone)` lets players drag and resize controls. Save with `context.saveSettings()`.
- **Vibration:** `input.vibrate(ms)`, honouring `setHapticsEnabled`.
- **Skin:** `config.touchControlsSkin` can supply `touch-base`, `touch-knob`, `touch-button`, `touch-button-down`, `touch-dpad` and `touch-dpad-down`.

## 5. Gamepads
Gamepads are more binding names (F-005, I-530), read through gdx-controllers: jamepad/SDL on desktop, Android's controller support on phones and TVs. Nothing needs to be added to a game's build.

| Group | Names (Xbox layout: `PAD_A` is the bottom face button on every pad) |
| --- | --- |
| Face buttons | `PAD_A`, `PAD_B`, `PAD_X`, `PAD_Y` |
| Menu buttons | `PAD_BACK`, `PAD_START` |
| Shoulders and triggers | `PAD_L1`, `PAD_R1`, `PAD_L2`, `PAD_R2` |
| Stick clicks | `PAD_L3`, `PAD_R3` |
| D-pad | `PAD_UP`, `PAD_DOWN`, `PAD_LEFT`, `PAD_RIGHT` |
| Sticks | `PAD_LSTICK_LEFT/RIGHT/UP/DOWN`, `PAD_RSTICK_LEFT/RIGHT/UP/DOWN` |

```json
"jump": ["SPACE", "PAD_A"],
"move": { "left": ["A", "PAD_LEFT", "PAD_LSTICK_LEFT"], "right": ["D", "PAD_RIGHT", "PAD_LSTICK_RIGHT"],
          "up": ["W", "PAD_UP", "PAD_LSTICK_UP"], "down": ["S", "PAD_DOWN", "PAD_LSTICK_DOWN"] }
```
- **Analog:** stick directions are analog in axes (0..1 after a 0.2 radial dead zone), so `axis2` gives partial speed. Full diagonals are clamped to length 1.
- **As actions:** a stick or trigger is pressed past 0.5 and released below 0.4, so menus can be driven with the stick.
- **Which pad:** the most recently used connected pad (one player).
- **Connection:** `GamepadConnected(name)` and `GamepadDisconnected(name)` are queued. A disconnect releases every pad input. `input.gamepadConnected()` and `input.gamepadName()` are for button hints.
- **On-screen controls:** pad use hides them like the keyboard does.
- **Menus:** they work with a pad once `ui_nav`, `ui_confirm` and `ui_back` list pad names. The platformer demo has them all.
- **Checked:** unit tests; a desktop run detected an XInput controller; the Android release build starts with the backend. Playing with a pad by hand has not been confirmed yet.

## 6. Rebinding
**The ready-made screen** (`ember.ui.RebindMenu`, I-535): a "press a key / press a button" panel.
```java
List<RebindMenu.Entry> controls = List.of(
        RebindMenu.action("jump", "Jump"), RebindMenu.action("dash", "Dash"),
        RebindMenu.axis("move", "left", "Left"), RebindMenu.axis("move", "right", "Right"));
menu.add("Controls", () -> RebindMenu.open(ui, controls, context::saveSettings));
```
- **Device:** the first row switches between **Keyboard** and **Gamepad**; it starts on Gamepad when a pad is connected. Each row shows that device's inputs (`Jump:  Space, Z`).
- **Rebinding:** activating a row waits 5 seconds for a key or pad input. Escape (Android Back) cancels.
- **What changes:** the new input **replaces that device's inputs** of the row. The other device's inputs, touch gestures and mouse buttons stay.
- **Shared inputs:** they are reported (`Z is also: Dash`), not removed, because one key can mean two things on purpose.
- **Other rows:** **Reset all** and **Done**. Save in the callback: `context.saveSettings()` stores the overrides, and they load at the next start.
- **In the demo:** the platformer has it under Settings → Controls (on desktop, or on Android when a pad is connected).

**The API underneath:**
```java
input.rebindAction("jump", List.of("W", "UP"));
input.rebindAxis("move", "left", List.of("J", "PAD_LEFT"));   // saved as "move:left"
input.resetBinding("jump");            // or "move:left"
input.resetAllBindings();
Map<String, List<String>> overrides = input.bindingOverrides();   // saved with the settings
List<String> keys = input.actionInputs("jump");
List<String> left = input.axisInputs("move", "left");
input.listenForInput(EnumSet.of(InputDevice.KEYBOARD), name -> ..., () -> /* cancelled */ ...);
InputDevice.of("PAD_A");               // GAMEPAD
RebindMenu.label("L_SHIFT");           // "L Shift"
```
- **Listening:** `listenForInput` swallows the press it reports, so it triggers no action. While waiting, other key and pad presses are swallowed too; touch and the mouse keep working for the UI.
- **Reloads:** overrides survive hot reloads of the bindings file.

## 7. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| `Unknown input action "fire"` | a component reads an action your bindings don't have | bind it, or guard with `hasAction` |
| A menu ignores the keyboard | the `ui` context isn't pushed | `pushContext("ui")` |
| The player moves while a menu is open | gameplay and UI share keys, and the `ui` context isn't pushed | push it; keys in `ui` hide gameplay's |
| A tap starts the game in an emulator test | `adb shell monkey` injects a touch | start with `am start` |
| On-screen controls show on desktop | `touchControls = ALWAYS` or `--touch-controls` | use `AUTO` |
