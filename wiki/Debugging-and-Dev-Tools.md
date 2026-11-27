# Logging, Debug Overlay and Dev Tools

`ember.core` (`Log`, `LogConfig`, `LogLevel`, `LogSink`), `ember.debug` (`DebugOverlay`), `ember-devtools` (`DevTools`, `DirectoryWatcher`), `ember-desktop` (`DesktopLogging`, `CrashLog`). Specs F-014, F-018.

## 1. Logging
```java
private static final Log LOG = Log.get("levels");    // one per class, by category

LOG.info("Loading " + levelId);
LOG.warn("Missing checkpoint", exception);
if (LOG.isEnabled(LogLevel.DEBUG)) {                 // avoid building strings in per-frame code
    LOG.debug("Entities alive: " + count);
}
```
- **Levels:** `ERROR`, `WARN`, `INFO`, `DEBUG`; `OFF` silences a category.
- **Configuration:** `LogConfig.setDefaultLevel(...)`, `LogConfig.setLevel("assets", LogLevel.DEBUG)`, `LogConfig.addSink(...)`.
- **Output:** the console on desktop, logcat on Android. Before libGDX starts, and in tests, messages go to standard error.
- **Desktop files:** dev runs write `logs/<game>-dev.log`. Release desktop builds write a crash log on an uncaught exception.
- **Static on purpose:** logging is the one static service, because it is needed before the context exists.

| | Dev run | Release |
| --- | --- | --- |
| Default level | `DEBUG` | `INFO` |
| Dev log file | `logs/<game>-dev.log` (desktop) | none |
| Crash log | no | `%APPDATA%\<game>\crash-*.log` (Windows), `~/Library/Application Support/<game>/` (macOS), `~/.local/share/<game>/` (Linux) |

Useful categories: `core`, `scene`, `assets`, `ecs`, `events`, `time`, `render`, `tilemap`, `data`, `animation`, `hotreload`, `devtools`, `save`, `novel`, plus your game's (`shooter`, `rpg`, `novel-demo`).

To check that crash logs work, start a packaged desktop build with `--crash-test`.

## 2. The debug overlay (F3)
Press **F3** in a dev build. Release builds have it only with `debugOverlayInRelease`. Android dev builds of the demos show it from the start (`debugOverlayVisibleAtStart`).

The panel shows:
- FPS, frame time and engine time (average and maximum over the last second);
- fixed steps in the last frame and dropped steps;
- the current scene's entity count;
- draw calls.

Debug shapes (world units, this frame only; free while hidden):
```java
context.debug().rect(x, y, w, h, Color.GREEN);
context.debug().line(x1, y1, x2, y2, Color.RED);
context.debug().circle(x, y, r, Color.YELLOW);
```
`CollisionDebugSystem` draws colliders, triggers and contacts while the overlay shows.

## 3. Hot reload (desktop dev runs)
The demos' Gradle `run` task passes `-Dember.assetsDir=<source assets>`. The dev launcher then sets `config.assetsDir` and `config.assetWatcher = DevTools.watch(new DirectoryWatcher(Path.of(dir)))`. Saved files show up within about half a second:

| You save | Effect |
| --- | --- |
| a map, tileset or tileset image | the map reloads; the scene restarts with a fresh world |
| a prefab | later spawns use it |
| a clip set or state machine | animators switch on their next update |
| a texture or atlas | sprites look their region up again |
| a font | text is laid out again |
| the input bindings | applied at once |
| a JSON data file (patterns, weapons) | used on its next read (patterns: next run) |
| `manifest.json` | a warning: restart |

A broken file keeps the old version, and the error is logged and shown in the dev tools. `AssetReloaded` and `AssetReloadFailed` events let game code react.

## 4. Dev tools (F1)
`DevTools.wrap(game)` in the dev launcher adds Dear ImGui panels (desktop dev runs only). Open them from the **Panels** menu:

| Panel | Use |
| --- | --- |
| Entities | list and filter the scene's entities; select one |
| Inspector | edit the selected entity's components live; **Copy as prefab JSON** |
| Animation | the state machine's state and transitions; set parameters, fire triggers |
| Time | pause, step one fixed tick, time scale |
| Frame | frame-time graph, FPS, engine time, entities |
| Debug draw | the collider view |
| Events | the last 200 events, filterable |
| Assets | every asset with **Reload**, the reload history and the last error |

While a panel has focus, the game doesn't see that input. `ember-devtools` is refused in release packaging (`verifyReleaseClasspath`) and doesn't exist on Android.

## 5. Debugging on Android
```sh
adb logcat -c
adb shell am start -n dev.ember2d.demo.rpg/.android.AndroidLauncher
adb logcat -s core:* scene:* rpg:* AndroidRuntime:E
adb exec-out screencap -p > screen.png
```
More in [Building for Android](Building-for-Android).

## 6. A debugging checklist
1. **Logs:** read the first `ERROR` or `WARN` line and its category.
2. **Overlay:** check frame times and dropped steps (F3).
3. **Colliders:** use the collider view for physical problems.
4. **Inspector:** check component values live (desktop).
5. **Events:** use the Events panel to see whether what you expect is published.
6. **Tests:** reproduce the bug in a headless test, as the demos' tests do.
