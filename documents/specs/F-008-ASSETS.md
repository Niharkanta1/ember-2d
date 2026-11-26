# F-008 — Assets and Hot Reload

- Status: Part 1 Ready (IDs, manifest, loading, groups, fallbacks: expanded and approved in I-050, 2026-11-26); hot reload Ready (I-150: expanded and approved 2026-11-26, see "Hot reload (I-150)"); scene preloading (I-240): Draft
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

## Hot reload (I-150)
Status: Ready (expanded for I-150 and approved 2026-11-26; see "Decisions in I-150" for how it was built).

**Goal.** In a desktop dev build, saving a map in Tiled or a JSON file in an editor changes the running game within about half a second, with no restart. A broken file never crashes the game: the old version stays and the error is logged.

**Parts.**
- **Watcher (`ember-devtools`, desktop only):**
  - `DirectoryWatcher` watches the game's source assets folder, including subfolders, with the JDK's `WatchService`. No new dependency.
  - It runs on its own daemon thread.
  - It collects changed files and hands them over after a quiet period of 200 ms, so an editor saving a file in several writes, or Tiled saving a map and its tilesets, causes one reload.
- **Hooks (`ember-core`, `ember.assets`):**
  - The engine reads the changed paths on the render thread at the start of a frame, maps them to manifest IDs, and reloads those assets.
  - Release builds and Android have no watcher, so nothing runs there.
- **Reading from the source folder:** desktop dev runs read the assets from the build's classpath copy, which does not change when a file is saved. With `EngineConfig.assetsDir` set, the asset service reads from that absolute folder instead.
  - The demo's Gradle `run` task passes the folder (`-Dember.assetsDir=.../core/assets`), and the launcher sets `assetsDir` and the watcher only in dev mode.
  - Packaged builds and `--release` runs are unchanged.

**What reloads (file → manifest IDs).**
- **Direct match:** a changed file that is an entry's `path` reloads that entry.
- **Dependencies:**
  - A file loaded as a dependency (an atlas page `.png`, a map's tileset image) reloads the entries that use it.
  - A `.tsx` tileset or `.tx` template reloads every loaded map.
- **Not reloaded:**
  - Entries that are not loaded (their group isn't loaded) are skipped; they load the new file when their group loads.
  - A change to `manifest.json` logs a warning that a restart is needed. Changing the manifest at runtime is out of scope.
  - Files not in the manifest and not a dependency are ignored. They are logged at DEBUG level.

**How each kind of asset takes effect.**

| Asset | After reload |
| --- | --- |
| `json`, `prefab` | The new `JsonValue` is served by `assets.get`. `PrefabLibrary` drops the cached prefab, so the next spawn uses it. Entities already spawned are not changed. |
| `clips`, `asm` | `AnimationLibrary` and `AsmLibrary` rebuild the definition. Animators and state machines switch on their next update. This uses the reload rules already built in I-130 and I-135. |
| input bindings (the `EngineConfig.inputBindings` entry) | Parsed and applied again. A broken file keeps the old bindings. |
| `texture`, `atlas` | Regions are looked up again. Sprites that got their region from `regionId` resolve it again on the next frame. A region set by code is the game's responsibility. |
| `font`, `freetype` | `Text` entities resolve their font again and lay out their glyphs again. |
| `map` | The engine publishes `AssetReloaded`, then **restarts the current scene**: it exits and enters the same scene object again, with a fresh world. This re-places the entities and rebuilds collision, so a moved enemy or a new platform appears. A scene can handle the reload itself by overriding `onAssetReloaded` and returning true. |
| `sound`, `music` | Reloaded and served by `assets.get`. Music that is playing keeps the old track until it is started again. |

- **Every reload:**
  - publishes `AssetReloaded(id, type, path)` on the global event bus (queued, delivered that frame), for game code;
  - logs one INFO line, for example `Reloaded map.demo (maps/demo-level.tmx) in 18 ms`.
- **Detection:** caches notice a reload through `Assets.generation()`, a counter that each reload increases. A cache compares one int per lookup and looks the asset up again only when the counter changed, so release builds pay nothing measurable.

**Errors.**
- **Keep the old version:** a new version is loaded and checked before it replaces the old one. If it fails (bad JSON, invalid prefab/clips/ASM data, a missing tileset or image), the old asset stays and the error is logged with the file and the problem, for example `Hot reload of anim.hero failed; keeping the old version: anim/hero.clips.json: clips.run.frames: ...`.
- **Scene restart failure:** if restarting the scene after a map reload throws, the error is logged, the old map version is kept, and the scene is entered again with the old map. If that fails too, the error goes to the normal crash path, as for a broken first scene.
- **Watcher problems:** a deleted file or folder, or watcher overflow, logs a warning and is otherwise ignored. A missing `assetsDir` fails at boot with the path.

**Not in I-150:**
- shaders (no shader asset type yet; they arrive with the task that adds them)
- bullet patterns (I-31x reuses this mechanism)
- changes to `manifest.json`
- reloading on Android
- changing entities that are already spawned when their prefab changes
- the dev tools panel showing reload status (I-160)

## API sketch (I-150)
```java
package dev.ember2d.assets;
@PublicApi public interface Assets {
    // ... existing methods
    default int generation() { return 0; }    // increases with every hot reload; caches compare it
}
@PublicApi public record AssetReloaded(String id, String type, String path) {}   // global event
@PublicApi public interface AssetWatcher extends AutoCloseable {   // implemented in ember-devtools
    void drainChanges(Consumer<String> changedPath);   // render thread; paths relative to the watched folder, '/'
    @Override void close();
}

package dev.ember2d.app;
// EngineConfig gains (dev builds only, ignored unless devMode):
public String assetsDir = null;        // absolute folder read instead of the internal assets
public AssetWatcher assetWatcher = null;
// Scene gains:
protected boolean onAssetReloaded(AssetReloaded event) { return false; }   // true = handled; map default: restart
// SceneManager gains:
public void restart();                 // exit and re-enter the current scene with a fresh world (after this frame)

package dev.ember2d.devtools;
@PublicApi public final class DirectoryWatcher implements AssetWatcher {
    public DirectoryWatcher(Path root);                 // starts watching (daemon thread)
    public DirectoryWatcher(Path root, long quietMillis);
}
```
Internal: `GdxAssets.reload(id)` (load and check the new version, then swap it in, keeping reference counts and groups), `GdxAssets.idsForChange(path)`, and the `EmberGame` frame step that drains the watcher in dev mode.

## Tests (I-150)
- **Watcher (temp folder, real `WatchService`):**
  - one change in a subfolder arrives once after the quiet period
  - several quick writes arrive as one change
  - `close()` stops the thread
- **`GdxAssets.reload` (headless, temp assets folder):**
  - each type is replaced and `generation()` increases
  - `idsForChange` for a direct path, an atlas page, a `.tsx` file and an unknown file
  - an entry that isn't loaded is skipped
  - a broken JSON or map keeps the old version and names the file
  - an asset shared by two groups keeps its reference count
- **Caches:**
  - `PrefabLibrary` spawns the new version
  - `AnimationLibrary` and `AsmLibrary` pick up a reloaded file through `generation()`
  - a sprite with `regionId` and a `Text` resolve again
- **Engine:**
  - a map reload restarts the current scene with a fresh world
  - a scene that returns true from `onAssetReloaded` is not restarted
  - input bindings are applied again
  - `AssetReloaded` is published
  - with `devMode` false the watcher is never drained
- **Demo (manual, desktop `run`):**
  - Move an enemy and add a platform tile in Tiled → both appear after saving.
  - Change `hero.clips.json` frame times → the animation changes.
  - Break a JSON file → an error is logged and the game keeps running.
  - You check desktop; the emulator is checked to be unaffected.

## Decisions in I-150
- **A failed scene restart waits for the next fix.** The spec said the old map would be kept and the scene entered again with it. Instead, in dev builds a restart that throws is logged ("fix the file and save it to try again"), and the scene stays out of the stack until the next successful reload of any asset restarts it. The old map is already released by then, and a typo in a map usually means the old map would fail the same way after the next save anyway. Release builds rethrow, as before.
- **Dev-only code in the desktop launcher.** The desktop build fails release packaging when `ember-devtools` is on the runtime classpath (F-020). So the watcher is wired in `DevLauncher`, in a separate `dev` source set of `demos/demo-platformer/desktop`. Only the Gradle `run` task uses that source set, and it also passes `-Dember.assetsDir`. `DesktopLauncher.launch(args, devSetup)` takes the dev additions; release jars are unchanged.
- **Names:**
  - The internal method is `GdxAssets.entriesForChange(path)`, not `idsForChange`. It returns manifest entries and matches dependencies transitively.
  - `GdxAssets.load(manifest, assetsDir)` reads from the folder. `GdxAssets.folderResolver(folder)` accepts relative paths and the absolute paths that dependency loading produces.
- **Reference counts:**
  - A reload first finishes queued loads (`AssetManager.finishLoading()`), so a group still loading, or a second group's reference to a loaded asset, counts before the reference counts are read and restored.
  - Found by the shared-group test: `unload()` would otherwise only drop the queued request.
- **Checking a new version:** it is loaded once in a separate `AssetManager` (same loaders) and thrown away. It is then loaded for real in the engine's manager. So a reload loads the file twice; this only happens in dev builds.
- **Caches:**
  - `AnimationLibrary`, `AsmLibrary` and `PrefabLibrary` rebuild **every** cached entry when `generation()` changes, not just the changed IDs. A clip set also depends on its sheet's texture, and a state machine on its clip set. On failure an entry keeps its old version and the problems are logged.
  - Clip sets built in memory are skipped.
- **Sprites:** `Sprite` remembers the region it got from `regionId` and the generation it came from. A region set by code, or by the animator, is never replaced; the animator sets frames from the rebuilt clip sets anyway.
- **`Scene.onAssetReloaded`** is called for every reloaded asset, not only maps. Only an unhandled `map` restarts the scene.
- **Frame timing:** a reload frame and the restart frame after it are long. The loop's accumulator is reset after both, so the game does not fast-forward or log dropped fixed steps.
- **Tests:**
  - `DirectoryWatcherTest` uses real files with 100–500 ms quiet periods and 20 s timeouts.
  - Desktop dev runs were checked from the log: a clip set change, a broken clip file (old kept), and adding and removing an enemy in the map (8, then 7 entities after the restarts).
