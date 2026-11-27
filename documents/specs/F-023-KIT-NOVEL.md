# F-023 — Visual Novel Kit

- Status: Ready (I-460, approved 2026-11-27 under the developer's standing approval for Phases 3 to 6; see "Design (I-460)")
- Phase: 5 (with the RPG package; both use Ink)
- Module / package: `kit-novel`
- Depends on: F-021 (`DialogueRunner`), F-012 (UI), F-011 (audio), F-013 (save), F-007 (animation state machine for expressions), F-002 (tweens), F-022 (touch); optional ADR-021 Spine
- Tasks: I-460, I-470
- Decision: ADR-017

## Goal
Make story-driven games — visual novels and story scenes inside other genres — with Ink scripts and no custom tooling.

## Scope
**Script and staging**
- Story written in Ink; lines as `Speaker: text`; choices are Ink choices.
- Staging through Ink tags, executed in order before the line is shown:

| Tag | Effect |
| --- | --- |
| `# bg: park_evening [transition=fade 1.0]` | Change background |
| `# show: alice happy left [transition=slide]` | Show character with expression at position |
| `# expr: alice sad` | Change expression |
| `# hide: alice` | Hide character |
| `# music: theme_calm [fade=2]` / `# stopmusic` | Music |
| `# sfx: door_knock` / `# voice: alice_014` | Sound and voice line |
| `# cg: beach_sunset` | Show CG image and unlock it in the gallery |
| `# wait: 1.5` / `# shake: 0.4` / `# flash: white 0.2` | Timing and screen effects |

- Unknown tags fail validation (dev builds) with story file and line.
- Characters defined in JSON: display name, name colour, expressions (region IDs or animation clips), default position, voice prefix.
- Positions: `left`, `center`, `right` plus custom named anchors; transitions use tweens.

**Reader features**
- Dialogue box with typewriter text (speed setting; tap/click to complete or advance), name plate, optional side portrait.
- Choice menu (keyboard, mouse, touch).
- Backlog of past lines (with voice replay).
- Auto mode and skip mode (skip read text only, or all, per setting); read-text tracking stored in a global persistent file.
- Hide UI to view the scene; quick save/quick load; save anywhere with thumbnail.
- CG gallery and scene replay unlocked by flags.
- Text settings: speed, size, auto-advance delay.

**Saving**
- Save = Ink story state + stage state (background, visible characters and expressions, music) + global flags.

## Design (I-460)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.kit.novel`; it depends on `ember-core` and `kit-common` (`DialogueRunner`).

The kit has two layers, so the staging logic is testable without a screen:
- **`NovelDirector`** (headless) runs the story, applies staging tags to a `StageState`, and tells a `StageListener` what to show.
- **`NovelView`** (Scene2D) is the standard `StageListener`. It draws the scene and the reader UI on a scene's `Ui`, and drives the director from input.

**Characters:** a JSON asset, parsed by `NovelCharacters.parse(json, file)`:
```json
{ "version": 1,
  "positions": { "left": 0.22, "center": 0.5, "right": 0.78 },
  "characters": {
    "alice": { "name": "Alice", "color": "#FF8FA3", "voice": "voice.alice_", "position": "left",
               "expressions": { "happy": "atlas.novel/alice_happy", "sad": "atlas.novel/alice_sad" },
               "default": "happy" } } }
```
- **`positions`:** fractions of the screen width. `left`, `center` and `right` are built in and can be overridden; other names are custom anchors.
- **Character fields:** `name` (shown in the name plate), `color` (of the name), `voice` (a prefix for voice asset IDs), the default `position`, `expressions` (expression name to region ID) and the `default` expression.
- **Speaker matching:** a line's speaker (from `DialogueRunner.speaker()`) is matched to a character ID, case-insensitively, or to its display name. Unknown speakers are shown as written, in the default text colour.

**Staging tags** are run in order before their line is shown. Options are `key=value` words. The table's `[transition=fade 1.0]` form is accepted too: brackets are ignored, and a number after `transition=x` is its time.

| Tag | Effect | Defaults |
| --- | --- | --- |
| `bg: <id> [transition=fade\|cut <s>]` | background region | fade 0.5 |
| `show: <char> [<expr>] [<position>] [transition=fade\|slide\|cut <s>]` | show or move a character | default expression and position, fade 0.3 |
| `expr: <char> <expr>` | change expression | |
| `hide: <char> [transition=fade\|cut <s>]` | hide | fade 0.3 |
| `music: <id> [fade=<s>]` / `stopmusic [fade=<s>]` | music | 1 |
| `sfx: <id>` / `voice: <id>` | sound / voice line | |
| `cg: <id>` / `hidecg` | show a full-screen image and unlock it in the gallery | |
| `wait: <s>` | pause before the line | |
| `shake: <strength>` | shake the screen | 0.4 |
| `flash: [<color>] [<s>]` | flash the screen | white 0.2 |

- **Other tags:** tags without a colon that are not commands, and the dialogue tags `speaker:` and `mood:`, are left to the game; they are available through `NovelLine.tags`.
- **Unknown tags:** an unknown `name:` tag, an unknown character, expression or position, or a bad number fails in dev builds with `IllegalStateException`, naming the story and the line. In release builds it is logged and skipped.
- **Lines with only tags** (staging between lines) are run and skipped, so the reader never sees an empty line.

**`NovelDirector(DialogueRunner, NovelCharacters, StageListener, NovelMemory, devMode)`:**
- **`next()`** returns a `Step`:
  - `LINE`, with `NovelLine(speakerId, name, color, text, voice, tags, read, delay)`;
  - `CHOICES`, with their texts;
  - or `END`.

  `delay` is the total of the `wait:` tags before the line. `read` tells whether the reader has seen this line before.
- **`choose(i)`.**
- **Backlog:** `backlog()` holds the last 100 lines (name, text, voice).
- **Saving:**
  - `save(SaveData out)` writes the Ink state (key `ink`) and the stage (key `stage`: background, music, characters with expression and position, cg).
  - `load(SaveData in)` restores both and calls `StageListener.restore(StageState)`, so the view redraws at once without transitions.
- **`NovelMemory`:** wraps a `SaveData` the game keeps in `saves().global()`. It records read lines (a hash of the speaker and text) and unlocked CGs. `unlockedCgs()` lists them for a gallery; `isRead(line)` and `markRead` serve skip mode.

**`NovelView`** (on a scene's `Ui`):
- **Layers:** the background, the characters at their anchors (bottom-aligned, scaled to fit 85 % of the height) and the CG, under the reader UI.
- **Dialogue box:** a name plate in the character's colour and typewriter text (`textSpeed` characters per second, 0 for instant).
- **Advancing:** a tap, `ui_confirm` or `fire` completes the line, then advances.
- **Choices:** a `MenuList`.
- **Reader buttons:** Auto (advance after the text plus `autoDelay` seconds), Skip (fast advance, stopping at unread lines unless `skipUnread` is on), Log (the backlog, with a voice replay button where a line has voice), and Hide (hides the UI until the next tap).
- **Transitions:** fades, slides and flashes use Scene2D actions; music and sounds go through `Audio`; shake uses the camera's trauma.
- **Settings:** `NovelSettings` holds `textSpeed`, `autoDelay` and `skipUnread`, and is kept in the settings store under `novel.*`.
- **Embedding:** `NovelView.play(onFinished)` runs a story scene inside any genre: the RPG cutscene. The novel demo uses the same view full-screen.
- **Saving anywhere:** the game calls `director.save` into a slot. Thumbnails are a follow-up.

**Tests (headless, with stories compiled by inklecate):**
- Every tag in the table updates the `StageState` and calls the listener with the parsed options.
- Bracket options are parsed.
- Unknown tags, characters and expressions fail with the story and the line named.
- Lines with only tags are skipped.
- Speakers resolve to characters.
- `wait:` adds to the delay.
- The backlog keeps the last 100 lines.
- Read tracking, and skip stopping at unread lines.
- CGs are unlocked into the memory.
- Saving mid-scene and loading restores the stage and the story position exactly.
- The character catalog's parsing and errors.

### Decisions (I-460, as built)
- `NovelView` is driven with `play(director, onFinished)` and `update(delta)` from the scene; it sits on the scene's `Ui` stage and removes itself at the end.
- Without a `bg:` the background stays transparent, so the view can run over a game's world.
- A voice ID is played as given if that asset exists, otherwise as `voice.<id>`; a missing voice is silent.
- Without a `CameraRig`, `shake:` moves the view itself.
- Skip mode does not wait for `wait:` delays.
- Deviations: save thumbnails and a scene-replay gallery are not built (follow-ups for I-470 if the demo needs them). The view has no automated test; it is checked in the novel demo on desktop and Android (I-470).

- **In I-450:** the dialogue box is now the shared `ember.ui.DialogueBox` (F-012). `setCharacterArea(bottom, height)` places smaller characters for story scenes over a game (the RPG cutscene uses 0.33 and 0.5). The view was checked on the emulator in the RPG cutscene: the typewriter, a choice, the backlog, a slide, a shake and a flash.

## Non-goals
- A visual novel editor (Ink + Inky are the editor). Lip sync, Live2D. Branching flowchart viewer.

## Acceptance criteria
- `demo-novel` (one chapter, two characters, a choice that changes the ending) plays on desktop and Android.
- Saving mid-scene and loading restores background, characters, music and story position exactly.
- Skip mode stops at unread text; backlog shows the last 100 lines.
- Every tag in the table works and is covered by a test story.
- The kit can also run story scenes inside other genres (used for an RPG cutscene in `demo-rpg`).

## Risks/open questions
- Localisation becomes important for text-heavy games; Ink's approach to translations needs a short investigation before the novel demo is finished.
