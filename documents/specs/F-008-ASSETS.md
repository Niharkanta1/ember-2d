# F-008 — Assets and Hot Reload

- Status: Part 1 Ready (IDs, manifest, loading, groups, fallbacks: expanded and approved in I-050, 2026-11-26); hot reload (I-150) and scene preloading (I-240): Draft
- Phase: 1 (IDs, manifest, loading), 2 (hot reload), 3 (scene groups/preloading)
- Module / package: `ember-core` / `ember.assets`; hot reload driver in `ember-devtools`
- Depends on: F-001, F-019
- Tasks: I-050, I-150, I-240
- Decision: ADR-014 (wrap libGDX `AssetManager`)

## Goal
Games load everything by logical ID, with clear errors, safe disposal and fast iteration in dev builds.

## Scope
- Asset manifest (`manifest.json`): ID → path, type, group, optional flag.
- Wrapper over libGDX `AssetManager` (reference counting, dependencies, async loading, disposal are delegated).
- Supported types: texture atlas, texture, bitmap font, FreeType font, sound, music, Tiled map, particle effect, JSON data, Aseprite JSON, Ink story JSON. Custom loaders registered per type.
- Groups: load/unload a named group (per scene); progress reporting for loading screens.
- Fallbacks: missing optional textures use a magenta placeholder; missing optional sounds are silent; missing required assets fail the group load with a clear message.
- Hot reload (dev builds): file watcher on the assets folder; changed assets reloaded and `AssetReloaded` published; supported for maps, JSON data, atlases, shaders.

## Non-goals
- Custom cache, reference counting or dependency tracking (provided by `AssetManager`).
- Remote/streamed assets; asset bundles.

## User-facing behaviour
- Games refer to assets by ID (`assets.get("atlas.main", TextureAtlas.class)`, `assets.region("atlas.main/player_idle")`), never by path.
- At boot the engine reads the manifest and loads the `global` group synchronously; other groups load in the background (`loadGroup`) while the game shows progress, and are unloaded with `unloadGroup`.
- A missing optional asset shows a visible fallback and logs one warning; a missing required asset fails its group load with a message listing every missing ID and path.

## API sketch
```java
@PublicApi public interface Assets {                    // context.assets()
    <T> T get(String id, Class<T> type);   // unknown ID: error with up to 3 closest IDs; not loaded: error naming its group
    TextureRegion region(String id);       // "atlasId/regionName", or a texture ID (whole texture)
    AsyncLoad loadGroup(String group);     // background loading; the engine advances it every frame
    void unloadGroup(String group);
    boolean isLoaded(String id);
    boolean contains(String id);           // listed in the manifest
}
@PublicApi public interface AsyncLoad { String group(); float progress(); boolean isDone(); void finish(); }  // finish() blocks
@PublicApi public final class AssetLoadException extends RuntimeException { List<String> problems(); }
```
Implementation (internal): `ember.assets.internal.GdxAssets` over libGDX `AssetManager`, `AssetManifest` (parser and validation), a `JsonValue` loader, fallbacks.

## Data model
`manifest.json` at the root of the game's assets (`EngineConfig.assetManifest`, default `manifest.json`); paths are relative to the assets root.
```json
{ "version": 1, "assets": [
  { "id": "atlas.main", "path": "gfx/main.atlas", "type": "atlas", "group": "global" },
  { "id": "sfx.jump",   "path": "sfx/jump.ogg",   "type": "sound", "group": ["level1", "level2"], "optional": true } ] }
```
- `version`: 1 (required). `id`, `path`, `type`, `group` required; `optional` defaults to false.
- Types in part 1: `texture` (`Texture`), `atlas` (`TextureAtlas`), `font` (`BitmapFont`, bitmap fonts), `sound` (`Sound`), `music` (`Music`), `json` (libGDX `JsonValue`). Further types (Tiled maps, particles, FreeType fonts, Aseprite, Ink) arrive with the tasks that need them, together with a way to register loaders.
- `group` is a name or a list of names; an asset in several groups stays loaded until all of them are unloaded (`AssetManager` reference counting).
- Region IDs use `atlasId/regionName` (e.g. `atlas.main/player_idle`).
- Fallbacks for missing optional assets: 16x16 magenta texture/region (managed, so it survives Android context loss), silent sound and music, libGDX default font, empty JSON object.

## Dependencies
`ember.core`, `ember.event`; libGDX `AssetManager`, graphics, audio and JSON types. Files are opened only inside `ember.assets`.

## Lifecycle/resource ownership
- `EmberGame` creates the asset service after time and events (F-001 boot step 5), loads `global` at boot, advances `AssetManager.update()` at the start of every frame on the render thread, and disposes everything at shutdown after the game's own services.
- Games never dispose assets; they unload groups.

## Error handling
- Manifest problems fail boot with one message listing every problem with file, entry index/ID and field (unknown type lists the valid types; duplicate IDs; wrong version; missing fields). A missing manifest file fails boot naming the path.
- Unknown ID: `IllegalArgumentException` naming the ID and up to 3 closest IDs by edit distance. Wrong type: message names the asset's actual type. Not loaded: message names the group to load.
- Unknown group in `loadGroup`: lists the known groups.
- Missing required file at `loadGroup`: `AssetLoadException` listing each missing ID and path. Missing optional file: fallback and one warning.
- Missing atlas region at `region()`: magenta region and one warning per region ID (lookups happen during play).

## Performance considerations
`region()` results are cached per ID until the asset's group is unloaded; repeated lookups do not allocate.

## Worked example
```java
TextureRegion idle = context.assets().region("atlas.main/player_idle");
AsyncLoad load = context.assets().loadGroup("level1");   // loading screen reads load.progress() until load.isDone()
```

## Acceptance criteria
Part 1 (I-050) covers every criterion except hot reload (I-150).
- Load by ID; unknown ID error names the ID and the closest matches.
- Missing optional/required behaviour as specified.
- Group unload releases references; shared assets stay loaded.
- Hot reload updates a running scene's tile map and a JSON data asset without restart.
- No asset loaded by path outside `ember.assets` (ArchUnit check on `Gdx.files` use).

## Tests
Headless unit tests (libGDX headless files and mock audio, a no-op OpenGL stand-in from `java.lang.reflect.Proxy` in test code so textures, atlases and fonts load without a window): manifest parsing and every validation error; load by ID and closest-match suggestions; group load/unload with an asset shared by two groups; required vs optional missing files and each fallback; region lookup and cache; progress reporting.

## Risks/open questions
- Loading `EngineConfig` from `ember.json` (F-001) stays deferred until a game needs it.
