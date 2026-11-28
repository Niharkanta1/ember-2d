# Assets

Package `ember.assets` (`Assets`, `AsyncLoad`, `AssetReloaded`, `AssetReloadFailed`). Spec F-008. Games refer to every image, sound, map and data file by an **ID** listed in a manifest, never by a file path.

## 1. The manifest
`core/assets/manifest.json` (the path is `EngineConfig.assetManifest`). **You don't have to write it:** drop files into `core/assets/` and run `./gradlew assetManifest -Pgame=<game>`, which adds every new file with a type and ID and keeps your existing entries ([Working Without an Editor](Working-Without-an-Editor#4-the-asset-manifest)). The format:
```json
{ "version": 1, "assets": [
  { "id": "atlas.shooter", "path": "gfx/shooter.atlas", "type": "atlas", "group": "global" },
  { "id": "tex.knight_idle", "path": "gfx/knight_idle.png", "type": "texture", "group": "global" },
  { "id": "font.pixel", "path": "fonts/PressStart2P-Regular.ttf", "type": "freetype", "group": "global",
    "font": { "size": 8, "mono": true, "shadowOffsetX": 1, "shadowOffsetY": 1, "shadowColor": "#000000A0" } },
  { "id": "map.room", "path": "maps/room.tmx", "type": "map", "group": "room" },
  { "id": "sfx.shoot", "path": "sfx/shoot.wav", "type": "sound", "group": "global", "maxInstances": 3 },
  { "id": "music.boss", "path": "music/boss.wav", "type": "music", "group": "global" },
  { "id": "enemy/slime", "path": "prefabs/enemy/slime.json", "type": "prefab", "group": "global" },
  { "id": "fx.hit", "path": "fx/hit.p", "type": "particle", "group": "global" }
] }
```
| Field | Required | Meaning |
| --- | --- | --- |
| `id` | yes | unique ID used in code and data |
| `path` | yes | relative to the assets root |
| `type` | yes | see the table below |
| `group` | yes | a name or a list of names |
| `optional` | no | a missing file gets a fallback instead of failing the group |
| `maxInstances` | sounds | how many copies of the sound play at once |
| `font` | `freetype` | size, `mono`, colour, border, shadow, `characters`, `markup` |

### Types
| Type | Loads as | Notes |
| --- | --- | --- |
| `texture` | `Texture` | also usable as a whole-texture region (`regionId` = the ID) |
| `atlas` | `TextureAtlas` | regions are `"atlasId/regionName"` |
| `font` | `BitmapFont` | a bitmap font (`.fnt`) |
| `freetype` | `BitmapFont` | a `.ttf`/`.otf` rendered at load time; one file can back several sizes |
| `sound`, `music` | `Sound`, `Music` | through `Audio`, not directly |
| `json` | `JsonValue` | any data: input bindings, items, bullet types, weapons, strings, Ink stories |
| `map` | `TiledMap` | `.tmx` or `.tmj`; tilesets and images load with it |
| `prefab` | `JsonValue` | the ID is the prefab name (`enemy/slime`) |
| `clips`, `asm` | `JsonValue` | animation clip sets and state machines |
| `skin` | `Skin` | a Scene2D skin `.json` with its atlas |
| `particle` | `ParticleEffect` | a libGDX `.p` effect; its images are next to it |

Check what a build supports with an unknown type: the error lists every valid type.

## 2. Groups
- **`global`:** loads at boot and stays loaded.
- **Per scene:** a scene loads the group named by its `assetGroup()` before `enter`, and unloads it after `exit`.
- **By hand:** `assets.loadGroup("level2")` loads in the background and returns an `AsyncLoad` (`progress()`, `isDone()`, `finish()`). `unloadGroup` releases a group. An asset in several groups stays loaded until all of them are unloaded.
- **Preloading for transitions:** `context.scenes().preload(group)` ([Scenes and Transitions](Scenes-and-Transitions)).

## 3. Using assets
```java
Assets assets = context.assets();
TextureAtlas atlas = assets.get("atlas.shooter", TextureAtlas.class);
TextureRegion coin = assets.region("atlas.shooter/coin");    // or a texture ID for the whole texture
JsonValue items = assets.get("items", JsonValue.class);
String file = assets.path("items");                            // for error messages
boolean listed = assets.contains("voice.alice_001");           // in the manifest?
boolean ready = assets.isLoaded("map.room");
```
Sprites usually name regions in data (`"regionId": "atlas.shooter/slime"`), and the sprite system resolves them on first draw.

## 4. Missing and wrong assets
- **Unknown ID:** an error that suggests the closest IDs.
- **Missing optional file:** a visible fallback (magenta texture or region, silent sound, empty effect) and one warning.
- **Missing required file:** the group fails, listing every missing file.
- **Region missing from an atlas:** a magenta region and one warning per ID.
- **Not loaded:** "Asset … is not loaded; load its group first".

## 5. Hot reload
In desktop dev runs (`gradlew run`), saved files reload within about half a second:
- maps restart the scene;
- prefabs affect later spawns;
- clips and state machines switch on the next update;
- textures and atlases are looked up again;
- fonts are laid out again;
- input bindings apply at once;
- bullet patterns are used on the next run.

A broken file keeps the old version and logs the error. `manifest.json` changes need a restart. See [Debugging and Dev Tools](Debugging-and-Dev-Tools).

## 6. Organising a game's assets
The demos use this layout (all under `core/assets/`):
```text
manifest.json  input.json  ART-CREDITS.txt
gfx/        sprite sheets, atlases (*.atlas + pages)
maps/       *.tmx, *.tsx, tileset images, *.tiled-project
prefabs/    one JSON per prefab, in folders: enemy/, pickup/, fx/
anim/       *.clips.json, *.asm.json
sfx/ music/ sounds and music
fonts/      *.ttf with their licence
ink/        compiled stories (*.ink.json); sources in assets-src/
fx/         particle effects (*.p) and their images
```
ID conventions: `tex.*`, `atlas.*`, `map.*`, `sfx.*`, `music.*`, `font.*`, `anim.*`, `asm.*`, `ink.*`, `fx.*`, `bg.*`, `cg.*`, `pattern.*`, `weapon.*`. Prefabs use their path (`enemy/slime`).

### Making an atlas without a packer
An atlas file can name regions of existing sheets by hand. The shooter, RPG and novel demos do this:
```text
enemies.png
size: 64,64
format: RGBA8888
filter: Nearest,Nearest
repeat: none
slime
  bounds: 48,0,16,16
```
For many images, pack them with libGDX's TexturePacker instead.

## 7. Best practices
- Use `filter: Nearest` for pixel art.
- Keep per-level assets in per-level groups, and preload the next one.
- List every asset you ship in `ART-CREDITS.txt` with its licence.
- Use `optional` only for assets you can really live without (voice lines). Required assets that fail early are easier to fix.
