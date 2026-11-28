# 06 — Gamepads

This chapter makes a game playable with a gamepad (F-005, I-530). Gamepads are read through gdx-controllers: jamepad/SDL on desktop, and Android's own controller support on phones and TVs. The example is the platformer demo, which can be played with a pad end to end.

> Status: draft for the developer's review at the Phase 6 gate. Not approved yet.

## 1. How it fits
A gamepad is just more **binding names** in `input.json`, next to keys and touch inputs. Gameplay code keeps asking for actions and axes (`input.justPressed("jump")`, `input.axis2("move", v)`), so it never knows whether a key, a finger or a pad did it.

Nothing needs to be added to your game's build: `ember-core` includes the controller API, `ember-desktop` and `ember-android` include the backends, and `ember-android` ships the R8 rule.

## 2. Binding names (Xbox layout)
| Group | Names |
| --- | --- |
| Face buttons | `PAD_A` (bottom), `PAD_B` (right), `PAD_X` (left), `PAD_Y` (top) |
| Menu buttons | `PAD_BACK` (View/Select/Share), `PAD_START` (Menu/Options) |
| Shoulders and triggers | `PAD_L1`, `PAD_R1`, `PAD_L2`, `PAD_R2` |
| Stick clicks | `PAD_L3`, `PAD_R3` |
| D-pad | `PAD_UP`, `PAD_DOWN`, `PAD_LEFT`, `PAD_RIGHT` |
| Sticks | `PAD_LSTICK_LEFT/RIGHT/UP/DOWN`, `PAD_RSTICK_LEFT/RIGHT/UP/DOWN` |

On a PlayStation or Switch pad, the names keep their positions: `PAD_A` is Cross on PlayStation and B on Switch.

## 3. The platformer's bindings
```json
"gameplay": {
  "actions": { "jump": ["SPACE", "Z", "UP", "W", "PAD_A"], "pause": ["P", "DOUBLE_TAP", "PAD_START"],
               "menu": ["ESCAPE", "BACK", "LONG_PRESS", "PAD_BACK"], "dash": ["X", "L_SHIFT", "PAD_X", "PAD_R1"] },
  "axes": { "move": { "left": ["LEFT", "A", "PAD_LEFT", "PAD_LSTICK_LEFT"], "right": ["RIGHT", "D", "PAD_RIGHT", "PAD_LSTICK_RIGHT"],
                      "up": ["UP", "W", "PAD_UP", "PAD_LSTICK_UP"], "down": ["DOWN", "S", "PAD_DOWN", "PAD_LSTICK_DOWN"] } } },
"ui": {
  "actions": { "ui_back": ["ESCAPE", "BACK", "P", "PAD_B", "PAD_START"], "ui_confirm": ["ENTER", "SPACE", "Z", "PAD_A"] },
  "axes": { "ui_nav": { "left": ["LEFT", "A", "PAD_LEFT", "PAD_LSTICK_LEFT"], ... } } }
```
- **Menus:** they work with the pad once `ui_confirm`, `ui_back` and `ui_nav` have pad names; `MenuList` needs nothing else.
- **New games:** games made with `newGame -Ptemplate=blank` start with pad bindings for the menus and quitting.

## 4. Analog sticks
- **Axes:** a stick direction gives its strength (0..1), so `axis2("move", v)` returns half speed for half a push. A radial dead zone of 0.2 hides stick drift. With `normalize`, a full diagonal has length 1, as it does with keys.
- **Actions:** a stick direction or trigger is "pressed" past 0.5 and "released" below 0.4, so a stick can move through menus without flicker.
- **Digital inputs:** the D-pad and buttons are 0 or 1.

## 5. Connecting and disconnecting
```java
context.events().on(GamepadConnected.class, e -> showHints("pad"));
context.events().on(GamepadDisconnected.class, e -> { showHints("keyboard"); pauseGame(); });
boolean pad = input.gamepadConnected();
String name = input.gamepadName();
```
- **Which pad:** the most recently used one (one player).
- **Disconnects:** every pad input is released, so the character doesn't keep running. Pausing on disconnect is good practice (console stores require it).
- **On-screen controls:** pad use hides them, like a keyboard does, and a touch brings them back.
- **Log:** connections are logged (`[input] Gamepad connected: XInput Controller`).

## 6. Limits
- **Players:** one player (the current pad). Local multiplayer with one pad per player is not supported.
- **Feedback:** no pad vibration or light bar control.
- **Rebinding:** use `RebindMenu` (section 7).
- **Checked:**
  - unit tests (analog values, dead zone, hysteresis, contexts, connect and disconnect);
  - a desktop run that found the developer's XInput controller;
  - Android release (R8) start-up with the controller backend.
- **Not yet checked:** a person playing with a pad on desktop or Android.

## 7. Letting players rebind
`ember.ui.RebindMenu` (I-535) is a ready-made "press a button" screen. The platformer has it under Settings → Controls:
```java
static final List<RebindMenu.Entry> CONTROLS = List.of(
        RebindMenu.action("jump", "Jump"), RebindMenu.action("dash", "Dash"), RebindMenu.action("pause", "Pause"),
        RebindMenu.axis("move", "left", "Left"), RebindMenu.axis("move", "right", "Right"),
        RebindMenu.axis("move", "up", "Up"), RebindMenu.axis("move", "down", "Down"));
menu.add("Controls", () -> RebindMenu.open(ui, CONTROLS, context::saveSettings));
```
- **Device:** the first row switches between Keyboard and Gamepad; it starts on Gamepad when a pad is connected.
- **Rebinding a row:** pick it and press the new button within 5 seconds. Escape cancels.
- **What changes:** the new input replaces only that device's inputs of the row, so changing the pad's jump leaves Space alone.
- **Shared inputs:** they are reported (`Z is also: Dash`) but kept.
- **Saving:** `context.saveSettings()` in the callback stores the changes (as overrides), and they load at the next start.

## 8. Checklist
- [ ] Pad names in every action and axis that players use, including `ui_confirm`, `ui_back` and `ui_nav`.
- [ ] Button hints follow `GamepadConnected` and `GamepadDisconnected`.
- [ ] Pause on disconnect.
- [ ] A Controls screen (`RebindMenu`) for the actions players use.

Reference: spec `specs/F-005-INPUT.md`, section "Gamepad (I-530)".
