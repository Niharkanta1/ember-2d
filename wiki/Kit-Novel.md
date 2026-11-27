# kit-novel

Module `kits/kit-novel`, package `kit.novel`. Spec F-023, ADR-017. Visual novels written in **Ink**, staged with tags, presented with a ready-made reader. The same pieces run cutscenes inside other genres.

| Class | Role |
| --- | --- |
| `NovelCharacters` | the cast: names, colours, expressions, positions (JSON) |
| `NovelDirector` | runs a `DialogueRunner` as a staged scene: tags, stage state, backlog, read tracking, save and load |
| `StageListener` | what a view implements to show staging (`background`, `show`, `hide`, `music`, `stopMusic`, `sound`, `voice`, `cg`, `shake`, `flash`, `restore`) |
| `StageState` | background, music, CG and visible characters (saved with the story) |
| `NovelView` | the standard Scene2D presentation: layers, `DialogueBox`, choices, Auto, Skip, Log, Hide |
| `NovelLine` | one line: speaker id, name, colour, text, voice, game tags, read, delay |
| `NovelMemory` | read lines and unlocked CGs, kept in global save data |
| `NovelSettings` | `textSpeed` (40 chars/s), `autoDelay` (1.5 s), `skipUnread` (false) under `novel.*` settings |

## 1. The cast
```json
{ "version": 1, "positions": { "far": 0.05 },
  "characters": {
    "kero": { "name": "Kero", "color": "#8BE38B", "position": "left", "default": "normal",
              "expressions": { "normal": "atlas.novel/kero_normal", "surprised": "atlas.novel/kero_surprised",
                               "happy": "atlas.novel/kero_happy" } } } }
```
- Positions: `left`, `center`, `right` (fractions of the width) and your own named anchors.
- Expressions are region IDs. Unknown fields, colours, positions or default expressions are reported together.
- A line's speaker (`Kero: …` or `#speaker:kero`) matches a character by ID or name; unknown speakers are shown as written.

## 2. Staging tags
Tags on their own line apply to the next line of text:
```ink
# bg: bg.meadow [transition=fade 1.0]
# music: music.meadow fade=1.5
# show: kero left
Kero: What a fine day for a walk!
# show: pip surprised right transition=slide
# sfx: sfx.pop
Pip: Kero! Kero! My cherries are gone!
```
| Tag | Effect |
| --- | --- |
| `bg: id [transition=fade 1.0]` | background (`fade`, `slide`, `cut`) |
| `show: id [expression] [position] [transition=…]` | show or move a character |
| `expr: id expression` | change expression |
| `hide: id [transition=…]` | hide |
| `music: id [fade=2]`, `stopmusic [fade=2]` | music |
| `sfx: id`, `voice: id` | sound, voice line (`id` or `voice.<id>`) |
| `cg: id`, `hidecg` | full-screen picture; unlocked in `NovelMemory` |
| `wait: 0.5` | delay before the line (adds up) |
| `shake: 0.4`, `flash: white 0.2` | effects (colour name or `#rrggbb`) |

- Options are written `name=value`, with or without brackets.
- **Unknown staging tags** fail in dev builds with the story and line named, and are logged and skipped in release builds.
- **Game tags:** plain tags without a colon (`#chapter_end`, `#ending_together`) and dialogue tags (`speaker:`, `mood:`) pass to the game in `NovelLine.tags()`. A `name:value` tag with an unknown name counts as an unknown staging tag.
- Lines with only tags are skipped.

## 3. Directing and presenting
```java
NovelView view = new NovelView(ui(), context.assets(), context.audio(), context.input(), context.renderer().camera(),
        new NovelSettings().load(context.saves().settings()));
NovelCharacters cast = NovelCharacters.parse(context.assets().get("cast", JsonValue.class), context.assets().path("cast"));
NovelDirector director = new NovelDirector(DialogueRunner.fromAsset(context.assets(), "ink.chapter1"), cast, view,
        new NovelMemory(context.saves().global()), context.config().devMode);
view.setOnLine(line -> { /* line.tags(), line.speakerId(), line.read() */ });
view.play(director, () -> { /* the end */ });
// every frame (skip while paused):
view.update(delta);
```
### `NovelView`
- **Layers:** background (none means the game world shows through), characters, CG, the dialogue box, a flash overlay.
- **Pressing:** a tap, `ui_confirm` or `fire` completes the line, then advances.
- **Choices:** a `MenuList` in the middle.
- **Auto:** advances after the text plus `autoDelay`.
- **Skip:** fast-forwards and stops at any unread line (including the one on screen), unless `skipUnread`.
- **Log:** the backlog (last 100 lines) with voice replay buttons.
- **Hide:** hides the UI until the next tap.
- **Character size:** `setCharacterArea(bottom, height)`. The default (0, 0.85) suits full-screen novels; a cutscene over the world uses (0.33, 0.5).
- **Shake:** goes through the `CameraRig` when given, otherwise the view shakes itself.

### Using the director without the view
`director.next()` returns a `Step` of kind `LINE` (with a `NovelLine`), `CHOICES` (a list) or `END`. `choose(i)` picks a choice. Implement `StageListener` for your own presentation; every method has a default.

## 4. Save anywhere
```java
director.save(slotData);      // Ink state, stage, the line on screen, the backlog
director.load(slotData);      // redraws the stage at once (StageListener.restore)
view.play(director, onEnd);   // shows the saved line again, then continues
```
After a process kill and a load, the background, characters with expressions and positions, music, the current line and the backlog are exactly as they were. This was checked on the emulator.

## 5. Memory and settings
- `NovelMemory(saves.global())`: `isRead`, `markRead` (done by the director), `unlockCg`, `isUnlocked`, `unlockedCgs()` for a gallery. Call `saves.flush()` to write it.
- `NovelSettings`: `load(settings)` and `save(settings)`; `textSpeed` 0 shows lines at once.

The full game is in [Guide: Visual Novel](Guide-Visual-Novel); a cutscene inside an RPG is in [Guide: RPG](Guide-RPG).
