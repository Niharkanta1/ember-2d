# F-008 — Assets and Hot Reload

- Status: Draft
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

## API sketch
```java
@PublicApi public interface Assets {
    <T> T get(String id, Class<T> type);  TextureRegion region(String id);
    AsyncLoad loadGroup(String group);  void unloadGroup(String group);
    boolean isLoaded(String id);
}
```
```json
{ "version": 1, "assets": [
  { "id": "atlas.main", "path": "gfx/main.atlas", "type": "atlas", "group": "global" },
  { "id": "map.level1", "path": "maps/level1.tmx", "type": "tiledMap", "group": "level1" } ] }
```
Region IDs use `atlasId/regionName` (e.g. `atlas.main/player_idle`).

## Acceptance criteria
- Load by ID; unknown ID error names the ID and the closest matches.
- Missing optional/required behaviour as specified.
- Group unload releases references; shared assets stay loaded.
- Hot reload updates a running scene's tile map and a JSON data asset without restart.
- No asset loaded by path outside `ember.assets` (ArchUnit check on `Gdx.files` use).
