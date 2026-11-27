# Localisation

Package `ember.i18n` (`Strings`, `LanguageChanged`). Spec F-025.

> The engine supports string tables and translated Ink stories, with tests. **None of the four demos is localised yet**; their text is English in code and stories. The examples below follow the spec and the engine's tests.

## 1. String tables
One JSON asset per language, with the ID `strings.<language>`:
```json
{ "version": 1, "language": "de",
  "strings": { "menu.play": "Spielen", "hud.score": "Punkte {0}", "item.potion": "Trank" } }
```
```json
{ "id": "strings.en", "path": "i18n/en.json", "type": "json", "group": "global" },
{ "id": "strings.de", "path": "i18n/de.json", "type": "json", "group": "global" }
```
- Keys are yours. `{0}`, `{1}`, … are filled by `format`, and `{{` writes a literal `{`.
- **Errors:** a table with a wrong `version`, without `strings`, with a non-string value, or with a `language` that doesn't match its ID fails to load, naming the file and key.

## 2. Using strings
```java
Strings strings = context.strings();
String play = strings.get("menu.play");
String score = strings.format("hud.score", 120);   // "Punkte 120"
boolean known = strings.has("menu.play");
List<String> languages = strings.languages();      // codes that have a strings.<code> asset
strings.setLanguage("de");                         // publishes LanguageChanged("de")
String current = strings.language();
```
- **Lookup order:** the current language, then `EngineConfig.defaultLanguage` ("en"), then `[key]`. In dev builds, a missing key is logged once.
- **Games without tables:** `get(key)` returns the key itself, so `Strings` costs nothing.
- **Unknown language:** `setLanguage` throws, listing the known ones.
- **The player's choice:** saved under the `ui.language` setting and applied at boot.

Rebuild text on `LanguageChanged`:
```java
context.events().on(LanguageChanged.class, e -> rebuildMenu());
```

## 3. Localised assets
`strings.localised("ink.town")` returns `ink.town.de` when the manifest lists it, otherwise `ink.town`. Use it for anything with text: stories, images with words, voice.
```java
DialogueRunner story = DialogueRunner.fromAsset(context.assets(), context.strings().localised("ink.town"));
```

## 4. Translating Ink stories
- The translator copies `town.ink` to `town.de.ink` and translates **only the text**. Knots, stitches, variables, choice structure, tags and functions stay the same.
- Both are compiled and listed: `ink.town` and `ink.town.de`.
- A story state saved in one language loads in the other, because it belongs to the structure.
- Choices already on screen keep their language, because Ink stores choice text in the state. So switch language between conversations.

## 5. Limits
Not supported:
- right-to-left text;
- plural rules (use separate keys: `items.one`, `items.many`);
- font fallback for scripts your font lacks (choose a font that covers your languages);
- translation tooling.
