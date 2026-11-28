# Glossary

| Term | Meaning |
| --- | --- |
| **Actor** | a body moved only through `Collision`; actors never block each other |
| **Asset group** | a named set of manifest entries loaded and unloaded together; `global` loads at boot |
| **Asset ID** | the name a game uses for an asset (`atlas.shooter`, `map.room`); never a file path |
| **`assetManifest`** | the Gradle task that adds new asset files to a game's `manifest.json` |
| **Backlog** | the last 100 lines of a novel scene, with voice replay |
| **CG** | a full-screen picture in a novel, unlocked when shown and listed in the gallery |
| **Clip set** | JSON naming the frames of each animation clip |
| **`ComponentSetup`** | your class that registers every component data can create; also drives the Android keep rules |
| **Context (input)** | a set of actions and axes; contexts form a stack (`gameplay`, `ui`) |
| **Coyote time** | a jump still works shortly after running off a ledge |
| **Dev mode** | debug logging, dev checks and dev features; `gradlew run` and debuggable Android builds |
| **`EngineContext`** | the object that gives access to every engine service |
| **`EntityRef`** | a reference to an entity that knows when the entity is gone |
| **Family** | the entities that have a given set of components |
| **Fixed step** | the constant simulation step (1/60 s) |
| **Graze** | a bullet passing close to the player without hitting; scores points |
| **Hot reload** | saved files appearing in a running dev build |
| **Ink** | the narrative scripting language used for dialogue and novels (inkle); compiled by inklecate, run by blade-ink |
| **Interpolation** | drawing between the previous and current fixed-step positions with `time.alpha()` |
| **Keep rules** | R8 rules generated from your `ComponentSetup` so minified Android builds keep your components |
| **Kit** | an optional gameplay module for a genre |
| **Layer content** | something drawn inside a render layer besides sprites (tiles, bullets, particles) |
| **`makeClips`** | the Gradle task that writes a clip set from sprite strips or an Aseprite export |
| **Manifest** | `manifest.json`, the list of all assets |
| **Marker** | an empty prefab placed in a map as a named position (arrival points) |
| **Material** | a sprite effect drawn with the built-in shader: flash, outline, palette swap |
| **`newGame`** | the Gradle task that creates a game project under `demos/` from a template ([Creating a New Game Project](Creating-a-New-Game-Project)) |
| **Phase** | when a system runs in the frame: `INPUT`, `FIXED`, `UPDATE`, `LATE`, `RENDER` |
| **Pixels per unit** | virtual pixels in one world unit (16 by default) |
| **Prefab** | a JSON template for an entity's components |
| **R8** | Android's minifier and obfuscator, used for release builds |
| **Render layer** | a named, ordered drawing layer with a sort mode |
| **Scene** | one screen with its own world, timers, events, UI and asset group |
| **Solid** | a body that blocks actors; optionally one-way |
| **Staging tag** | an Ink tag that changes a novel's stage (`bg:`, `show:`, `music:` …) |
| **Suspend** | the platform pausing the app (minimised, home button); not game pause |
| **Template** | what `newGame` copies: `blank` or one of the four demos |
| **Trigger** | a collider that never blocks and reports enter and exit |
| **Virtual resolution** | the size the world is drawn at before scaling (320 × 180 by default) |
| **World** | the entities, components and systems of one scene |
| **World flags** | persistent per-play-through state (`door.cellar = true`) |
| **World unit** | the engine's game unit; one tile at the default scale |
