# UI

Package `ember.ui` (`Ui`, `UiMode`, `UiSkins`, `UiPalette`, `MenuList`, `ConfirmDialog`, `DialogueBox`, `HudCounter`, `HudBar`, `TouchLayoutEditor`, `Insets`). Spec F-012. The UI is libGDX **Scene2D.ui** with conventions for menus that work with keyboard, gamepad, touch and the Android back button.

## 1. The scene's UI
Each scene has its own Scene2D layer, created on first use with `ui()` and disposed on exit. It draws above the world and gets pointer and key events before the game:
```java
Ui ui = ui();
ui.setSkin(UiSkins.pixel(context.assets().get("font.pixel", BitmapFont.class)), true);   // true: disposed with the UI
Table root = ui.root();   // a full-screen table; add widgets to it
```
- **Skin:** `UiSkins.pixel(font)` generates a complete pixel-art skin. It includes the label styles `default`, `title` and `dim`, buttons, a `panel` drawable, sliders, scroll panes and windows. `UiSkins.pixel(font, palette)` recolours it.
- **Set the skin before creating widgets.** A widget made before `setSkin` keeps the old skin and draws as a black box.
- **Size:** `UiMode.VIRTUAL` (default) lays out in virtual pixels (320 × 180), matching the world's pixel scale. `UiMode.SCREEN` uses the window size divided by `uiScale`.
- **Helpers:** `ui.width()` and `ui.height()`; `ui.safeInsets()` (notches); `ui.dp(40)` (a finger-sized length in UI units); `ui.stage()` for overlays; `ui.input()`.

## 2. Menus
```java
input.pushContext("ui");                       // menus read ui_nav, ui_confirm, ui_back
MenuList menu = new MenuList(ui);
menu.add("Resume", this::closePause);
menu.addValue("Music", () -> percent(audio.volume(AudioCategory.MUSIC)),
        v -> audio.setVolume(AudioCategory.MUSIC, v / 100f), 0, 100, 10);   // left/right change it
TextButton locked = menu.add("Level 3", () -> start(3));
menu.setDisabled(2, true);                     // greyed out and skipped
menu.onBack(this::closePause);                 // ui_back / Android back
menu.focus(0);
```
- **Only the newest menu reads input:** a `ConfirmDialog` above a pause menu takes the keyboard until it closes.
- **Held directions** repeat after 0.35 s, every 0.1 s.
- **Touch:** items are at least `MenuList.MIN_TOUCH_DP` (40 dp) tall on touch screens.
- **Labels:** `add` returns the `TextButton`, so you can change its text later (a toggle).

### Confirm dialog
```java
ConfirmDialog.show(ui, "Quit to the main menu?", "Yes", "No", () -> context.scenes().replace(new MenuScene()));
```
"No" has the focus. The label does not wrap, so keep messages short.

## 3. HUD
```java
Table hud = new Table(ui.skin());
HudCounter time = new HudCounter(ui.skin(), "Time", null);      // prefix, optional icon drawable
HudCounter deaths = new HudCounter(ui.skin(), "Deaths", null);
HudBar health = new HudBar(ui.skin());
hud.add(time).left().row();
hud.add(deaths).left().row();
hud.add(health).left();
ui.root().add(hud).expand().top().left().pad(4f);
// every frame:
time.setSeconds(levelTime);    // formats m:ss and relayouts only when the text changes
deaths.setValue(count);
health.setValue(hp / (float) maxHp);
```
HUD widgets are not touchable, so taps pass through to the game.

## 4. Dialogue box
`DialogueBox` shows a name plate, typewriter text and choices. kit-novel's view and the RPG's conversations both use it.
```java
DialogueBox box = new DialogueBox(ui);
box.setTextSpeed(45f);                          // characters per second; 0 = instant
box.setOnAdvance(this::showNext);               // called when pressed with the line complete
box.show("Grik", Color.valueOf("FFD36B"), "Ah, a knight! Just in time.");
box.showChoices(List.of("I'll take a look.", "Not today."), index -> { story.choose(index); showNext(); });
```
- **Pressing:** a press (`ui_confirm`, a click or a tap on the box) first completes the line, then advances. `setConfirmActions(...)` changes the actions.
- **Ignored presses:** a press in the frame a line appears is ignored, so the press that opened a conversation doesn't skip its first line. While another menu has the keyboard (a pause menu), the box ignores its confirm action.
- **Queries:** `isComplete()`, `complete()`, `text()`, `shownText()` and `hasChoices()`.
- **Choices:** they appear inside the box. Give the box a minimum height rather than a fixed one, so it grows with them.

## 5. Overlays and panels
The demos keep a small helper (`ShooterUi`, `RpgUi`, `NovelUi`, `DemoUi`) for a modal overlay with a centred panel:
```java
static Table overlay(Ui ui) {
    Table overlay = new Table(ui.skin());
    overlay.setFillParent(true);
    overlay.setTouchable(Touchable.enabled);   // modal: clicks don't reach what is below
    Table panel = new Table(ui.skin());
    panel.setBackground("panel");
    panel.pad(8f, 10f, 8f, 10f);
    overlay.add(panel).minWidth(150f);
    ui.stage().addActor(overlay);
    return panel;
}
```

## 6. Touch-control layout editor
```java
TouchLayoutEditor.open(ui, input, () -> context.saveSettings());
```
Players drag and resize controls, change the opacity and reset them. The platformer demo offers it as "Edit controls" on touch screens.

## 7. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| A widget is a black box | made before the skin was set | call `setSkin` first |
| A menu ignores keys | `ui` context not pushed | `input.pushContext("ui")` |
| The first line of a conversation is skipped | your own box handles the opening press | use `DialogueBox`, or ignore presses in the frame a line appears |
| Confirming in the pause menu also advances the story | a reader that ignores menus | `DialogueBox` yields to active menus |
| Choices overflow the dialogue box | fixed height | `minHeight` instead of `height` |
| A dialog's text runs off screen | `ConfirmDialog` labels don't wrap | shorter text |
