# F-023 — Visual Novel Kit

- Status: Draft
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
