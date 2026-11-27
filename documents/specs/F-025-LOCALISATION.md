# F-025 — Localisation

- Status: Ready (written and approved in I-440, 2026-11-27, under the developer's standing approval for Phases 3 to 6)
- Phase: 5
- Module / package: `ember-core` / `ember.i18n` (new; tier 3 in ARCHITECTURE.md: it may use `core`, `event` and `assets`)
- Depends on: F-008 (assets), F-013 (settings), F-021 (`DialogueRunner`)
- Tasks: I-440

## Goal
Text-heavy games (RPGs, visual novels) can ship in several languages: UI and item text come from string tables, Ink stories have one compiled story per language, and the player's language choice is a saved setting.

## Scope
**String tables:**
- One JSON asset per language (type `json`). Its asset ID is `strings.<language>`, for example `strings.en` or `strings.de`:
  ```json
  { "version": 1, "language": "de",
    "strings": { "menu.play": "Spielen", "hud.score": "Punkte {0}", "item.potion": "Trank" } }
  ```
- Keys are game-chosen. Values may contain `{0}`, `{1}` and so on, filled by `format`.
- `{{` writes a literal `{`.

**`Strings`** (available as `context.strings()`):
- **Language:** `language()` is the current code. `setLanguage(code)` switches and publishes `LanguageChanged(code)`. `languages()` lists the codes that have a `strings.<code>` asset in the manifest.
- **Lookups:**
  - `get(key)` returns the current language's text.
  - It falls back to the default language (`EngineConfig.defaultLanguage`, "en").
  - If the key is in neither, it returns `[key]`. In dev builds a missing key is logged once per key.
  - `has(key)` tells whether the key exists.
- **`format(key, args...)`:** fills `{n}` placeholders with `String.valueOf(arg)`. A placeholder without an argument stays as it is.
- **Games without tables:** with no `strings.*` assets at all, `get(key)` returns the key itself, so `Strings` costs nothing.
- **Loading:** tables are read when first needed and again after a hot reload (`assets.generation()`). The table's asset group must be loaded; `global` is usual.
- **`localised(baseId)`:** returns `baseId + "." + language` when the manifest lists it, else `baseId`. This is how other assets (stories, images with text) get a language version.

**The player's choice:** `EngineSettings` keeps the language under `ui.language`. It is applied at boot and captured by `saveSettings()`. An unknown stored language is skipped (logged).

**Ink stories:**
- **One compiled story per language:** the translator copies `town.ink` to `town.de.ink` and translates only the text. Knots, stitches, variables, choices' structure, tags and functions stay the same.
- **Loading:** both are compiled by inklecate and listed as `ink.town` and `ink.town.de`. `DialogueRunner.fromAsset(assets, strings.localised("ink.town"))` loads the right one.
- **Saved state:** a saved story state belongs to the story's structure, which is the same in every translation. So a state saved in one language loads in another, provided the translation kept the structure. Switching language takes effect at the next conversation; the running runner keeps its story.

## Non-goals
Right-to-left text, complex plural rules (games use separate keys: `items.one`, `items.many`), font fallback for scripts the game's font lacks, and translation tooling (spreadsheets, export or import).

## API sketch
```java
package dev.ember2d.i18n;
@PublicApi public final class Strings {
    public Strings(Assets assets, EventBus events, String defaultLanguage, boolean devMode);
    public String language();  public void setLanguage(String code);  public List<String> languages();
    public String get(String key);  public boolean has(String key);  public String format(String key, Object... args);
    public String localised(String baseId);
}
@PublicApi public record LanguageChanged(String language) { }
// app: EngineConfig.defaultLanguage = "en"; EngineContext.strings(); EngineSettings.LANGUAGE = "ui.language"
```

## Errors
- `setLanguage` of a language without a table throws `IllegalArgumentException` listing the known ones.
- A table with a wrong `version`, a missing `strings` object, a non-string value or a `language` that differs from its asset ID fails when it is loaded, with `IllegalArgumentException` naming the file and key.

## Acceptance criteria
- Lookups, the fallback to the default language, and the `[key]` marker for missing keys.
- Formatting with placeholders, escapes and missing arguments.
- Switching language publishes `LanguageChanged`; an unknown language is refused.
- Games without tables get the keys back.
- `localised` picks the language version of an asset when it exists.
- The language setting round-trips through `saveSettings` and boot.
- A translated Ink story (same structure) loads a state saved with the original.

## Decisions in I-440
- **A new package:** `ember.i18n` is at tier 3, with the other services. ARCHITECTURE.md and the ArchUnit tiers list it.
- **Switching language in the middle of a conversation:** the test shows that a German story loads a state saved by the English one. Choices already on offer keep the language they were made in, because Ink stores choice texts in the state. Switching language is therefore meant to happen between conversations.
- **External function fallbacks:** I-440 found that blade-ink refuses unbound `EXTERNAL` functions even when the story has an Ink fallback. `DialogueRunner` now allows fallbacks (F-021 decisions).
- **Not checked on a device:** no demo is localised yet. The plan was for the RPG and novel demos (I-450, I-470) to use string tables; they were built without them (their text is in English in the Ink stories and the scenes). Localising one demo is a follow-up.
