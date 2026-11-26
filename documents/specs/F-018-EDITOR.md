# F-018 — Editor Strategy: In-game Dev Tools

- Status: Dev tools (I-160) Ready: expanded and approved 2026-11-26 (see "Dev tools (I-160)"); standalone editor: not planned (ADR-009)
- Phase: 2 (after spike S-01 in Phase 1)
- Module / package: `ember-devtools` (dev builds only)
- Depends on: F-003, F-008 (hot reload), F-014 (overlay), F-019; imgui-java; LWJGL3 backend
- Tasks: S-01, I-150, I-160
- Decision: ADR-009 (replaces the original JavaFX editor MVP)

## Goal
Fast content iteration without a standalone editor: Tiled for levels, plus in-game tools to inspect and tweak the running game.

## Scope
- Dear ImGui via imgui-java rendered on top of the game in dev builds; toggled with a key (default F1). Input goes to ImGui when it wants focus, otherwise to the game.
- **Panels:** entity list (filter by name/tag/component), component inspector with live editing of public fields (via the component registry), animation state machine view (current state, parameters, last transition), collider/trigger view toggle, bullet count and pattern view, event log (last N events by type), frame metrics graph, asset list with reload button, hot reload status, time controls (pause, step one fixed tick, time scale).
- Desktop only: ImGui needs the LWJGL3 window. Android builds use the F-014 debug overlay instead.
- **Hot reload driver:** file watcher feeding F-008.
- "Copy as prefab JSON" for a selected entity.

## Non-goals
- Standalone editor application, project management, undo/redo, in-editor tilemap painting, play/edit mode switching. Revisit after the demos ship (I-550).

## Acceptance criteria
- Release builds and all Android builds contain no `ember-devtools` classes (dependency check in CI).
- Editing a component field in the inspector changes game behaviour immediately.
- Step-one-tick works while paused.
- Hot reload status shows the last reloaded asset and any error.

## Dev tools (I-160)
Status: Ready (expanded for I-160 and approved 2026-11-26; see "Decisions in I-160"). Builds on spike S-01 (`spikes/S-01-imgui-java.md`).

**Dependencies (need approval, downloads).**
- **imgui-java 1.92.7.1:**
  - `imgui-java-binding` and `imgui-java-lwjgl3`
  - natives for Windows, Linux and macOS
  - only in `ember-devtools` and the demo's dev-only source set (I-150), never in release builds or Android
- **Pin LWJGL 3.4.1 for every desktop build.** imgui-java needs it (S-01 consequence 1), and the pin makes dev and release runs use the same LWJGL. It is a version-catalog constraint applied to `ember-desktop`.
- Both are added to the dependency register with this note.

**How it attaches (S-01 consequence 3: a wrapper, no engine change).**
- `DevTools.wrap(game)` returns an `ApplicationListener` that runs the game and, after each game frame, draws ImGui in the same LWJGL3 window.
- The demo's `DevLauncher` wraps the game (`DesktopLauncher.launch` gains a wrap hook next to the config hook).
- **Toggle:** F1 shows and hides the tools. They start hidden.
- **Input:** while ImGui wants the mouse or keyboard, the engine's `InputCapture` (already in `ember.input`) keeps those events away from the game's actions.

**Panels** (one window each, chosen from a main menu bar; open/closed state is not saved):

| Panel | Shows / does |
| --- | --- |
| Entities | Entities of the current scene's world: ID, name, tags, component names. Filter by text (name, tag or component). Select one for the inspector. |
| Inspector | Every component of the selected entity and its public fields (the registry's settable fields; others read-only), edited live. Widgets per type: int, long, float, double, boolean, String, enum (combo), `Color` (colour picker), `Vector2`, `Rectangle`. "Copy as prefab JSON" puts the entity's registered components with their current values on the clipboard, in prefab format. |
| Animation | For a selected entity with `AnimationStateMachine`: current state, time in state, last transition, parameters (editable), current clip and frame. |
| Time | Pause/resume, **step one fixed tick** while paused, time scale slider (0–4), fixed tick, dropped steps. |
| Frame | Graph of the last 240 frame times (ms), FPS, engine ms per frame, entity count, render calls. |
| Debug draw | Collider and trigger view on/off: the F3 overlay's world drawing, which already includes the collider view. |
| Events | The last 200 events, newest first: frame, type, `toString()`. Filter by type name; pause the log. |
| Assets | Every manifest ID with type, path, groups and whether it is loaded. A "Reload" button per loaded asset. **Hot reload status:** the last reloaded assets with times, and the last error with its file and problem. |

**Not in I-160:**
- bullet count and pattern view (arrives with `kit-bullets`, I-31x)
- docking and multi-viewport
- saving the panel layout
- high-DPI scaling other than 1.0
- macOS and Linux runs: they get natives, but are not checked here
- undo
- editing collections or nested objects (arrays are shown read-only)

**Small engine additions (ember-core, public API, cost nothing when unused).**
- `SceneManager.currentWorld()`: the current scene's world, or null (the dev tools have no `Scene` subclass access).
- `World.entities(IntArray out)` and `World.components(int entity, List<Object> out)`: list the live entities and an entity's components.
- `EventBus.monitor(Consumer<Object>)`: sees every published and delivered event, returns a `Subscription`. Dev tools only; a null check when nothing monitors.
- `Time.step()`: while paused, the next frame runs exactly one fixed step (and `UPDATE` with that step's delta). Ignored while running.
- `Assets.ids()`: every manifest ID, in manifest order.
- `AssetReloadFailed(id, path, problem)`: published by the hot reload driver when a new version is rejected (I-150 only logged it).
- **Manual reload:** `DevTools.watch(watcher)` wraps the `DirectoryWatcher`, so the Assets panel's "Reload" button feeds the entry's path through the normal hot reload path.

## API sketch (I-160)
```java
package dev.ember2d.devtools;
@PublicApi public final class DevTools {
    public static ApplicationListener wrap(EmberGame game);   // ImGui after each frame; F1 toggles
    public static AssetWatcher watch(AssetWatcher files);     // adds manual "Reload" requests to the file changes
}
// internal: ImGuiHost (context, GLFW/GL3 backends, frame), panels (EntitiesPanel, InspectorPanel, AnimationPanel,
// TimePanel, FramePanel, EventsPanel, AssetsPanel), FieldAccess (reflection: read/write public fields by type),
// PrefabJson (entity -> prefab JSON), EventLog (ring buffer), FrameStats (ring buffer)

package dev.ember2d.app;     public World currentWorld();                         // SceneManager
package dev.ember2d.ecs;     public void entities(IntArray out); public void components(int entity, List<Object> out);
package dev.ember2d.event;   Subscription monitor(Consumer<Object> listener);    // EventBus
package dev.ember2d.core;    void step();                                         // Time
package dev.ember2d.assets;  default List<String> ids() { return List.of(); }     // Assets
                             @PublicApi public record AssetReloadFailed(String id, String path, String problem) {}
```

## Tests (I-160)
- **ember-core (headless):**
  - `currentWorld`, `entities`/`components`
  - the event monitor sees published and queued events and stops after `cancel()`
  - `Time.step()` runs exactly one fixed step while paused and nothing while running
  - `Assets.ids()`
  - `AssetReloadFailed` published for a broken file
- **ember-devtools (no window):**
  - `FieldAccess` reads and writes each supported type and rejects final/unsupported fields
  - `PrefabJson` output loads back through `PrefabLibrary` into equal components
  - the `EventLog` and `FrameStats` ring buffers
  - the manual-reload watcher merges button requests with file changes
- **ImGui rendering and input:** a window is needed, so this is checked manually on desktop by you (as in S-01). F1 toggles; typing in the inspector does not move the player; clicks on panels do not reach the game; editing `PlayerControl` speed changes the movement at once; step works while paused; the Reload button and status work.
- **Release safety:**
  - `verifyReleaseClasspath` still passes.
  - The Android build and lint are unchanged.
  - I confirm the release jar contains no `imgui` or `dev.ember2d.devtools` classes.

## Decisions in I-160
- **Module dependencies:** `ember-devtools` depends on `ember-core`, the libGDX LWJGL3 backend and the LWJGL 3.4.1 BOM directly, as in ARCHITECTURE's layered model; it does not depend on `ember-desktop`.
  - `ember-desktop` also takes the BOM, so release desktop builds run LWJGL 3.4.1 too (checked: `org.lwjgl:lwjgl:3.3.3 -> 3.4.1` in the demo's release runtime classpath).
  - The demo's dev launcher is the only place that uses `ember-devtools` (the `dev` source set from I-150).
- **Engine additions beyond the sketch:**
  - `World.tags(entity, out)` for the Entities panel.
  - `AnimationStateMachine.parameterTypes()` (names and types of the bound definition) for the Animation panel.
  - `GdxAssets.reload(entries, failures)` reports rejected versions; the hot reload driver publishes them as `AssetReloadFailed`.
- **`Time.step()`:** the step frame gets exactly the time that completes one fixed step (fixed step minus what is already accumulated). So one `FIXED` runs, and `UPDATE` sees that time as its delta.
- **Dropped from the panels** (the public API has no such data; adding it was not worth it for I-160):
  - the asset type and groups in the Assets panel (it shows ID, path, loaded, Reload)
  - render calls in the Frame panel (it shows FPS, frame ms with a graph and maximum, engine ms, entity count)
- **Inspector:**
  - A component the registry knows has only its registry fields editable.
  - A component the registry does not know (a game component registered with `World.registerComponent` only) has every public field of a supported type editable.
  - Text and `long` fields are typed into a buffer and applied with Enter.
  - A panel that throws is logged and the game keeps running.
- **Copy as prefab JSON:**
  - Only fields that differ from a new component are written.
  - `Transform` position, previous position and previous rotation are left out (they are the spawn position).
  - The text goes to libGDX's clipboard (the ImGui binding has no clipboard setter).
- **Checks:**
  - Rendering and input sharing were not checked by me: computer use cannot drive the plain `java.exe` window (as in S-01). The log shows ImGui 1.92.7 attaching without errors. The developer checks it on desktop.
  - The release jar was checked to contain no `imgui`, `dev/ember2d/devtools` or `DevLauncher` classes, and the Android release runtime classpath has neither.
