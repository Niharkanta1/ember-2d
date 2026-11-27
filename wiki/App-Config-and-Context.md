# App, Config and Context

Package `ember.app` (spec F-001, F-019). This page covers how a game starts and how code reaches the engine's services.

## 1. `EmberGame`: the entry point
`EmberGame` is the libGDX `ApplicationListener`. Games never subclass it; they configure it:
```java
// desktop
new Lwjgl3Application(new EmberGame(MyGame.config(devMode, version)), windowConfig);

// android
public class AndroidLauncher extends EmberAndroidLauncher {
    @Override
    protected EngineConfig config(boolean devMode) {
        return MyGame.config(devMode, versionName());
    }
}
```
`EmberGame` does the following:
- **At boot:** validates the config, builds the `EngineContext`, loads the `global` asset group, the input bindings and the settings, then starts `config.firstScene`.
- **Every frame:** runs the loop (see [Frame and Data Flow](Frame-and-Data-Flow)).
- **App suspend and resume:** handles them separately from game pause.
- **Errors:** an uncaught exception ends the game. It is logged, and release desktop builds write a crash log ([Debugging and Dev Tools](Debugging-and-Dev-Tools)). A fatal error screen (F-001) is not implemented yet.

## 2. `EngineConfig`
Every field has a default. `validate()` runs at boot and lists every invalid field in one `EngineConfigException`.

| Field | Default | Meaning |
| --- | --- | --- |
| `virtualWidth`, `virtualHeight` | 320, 180 | The world's resolution in virtual pixels. Pixel-perfect modes scale it by a whole number. |
| `pixelsPerUnit` | 16 | Virtual pixels per world unit (one tile). |
| `fixedStep` | 1/60 | Simulation step in seconds (at most 0.25). |
| `maxCatchUpSteps` | 5 | Fixed steps per frame before the rest are dropped (at most 60). |
| `renderMode` | `PIXEL_PERFECT` | `PIXEL_PERFECT` (whole-number scale, black bars), `PIXEL_PERFECT_FILL` (widens the world on wide screens), `SMOOTH` (any scale, no snapping). |
| `backgroundColor` | black | Clear colour. |
| `firstScene` | — (required) | `Supplier<Scene>`, for example `MenuScene::new`. |
| `assetManifest` | `"manifest.json"` | The manifest's path in the assets. |
| `inputBindings` | null | Asset ID of the input bindings JSON. Without it, no actions exist. |
| `components` | null | Your `ComponentSetup` (prefabs, maps, keep rules). |
| `tiledProject` | null | Path of a `.tiled-project` file, so class-typed Tiled properties work. |
| `devMode` | false | Debug logging, dev-only checks (bad story tags throw), dev features. |
| `debugOverlayInRelease` | false | Allow the F3 overlay in release builds. |
| `debugOverlayVisibleAtStart` | false | Show the overlay at boot (Android dev builds). |
| `uiMode`, `uiScale` | `VIRTUAL`, 0 | UI at the virtual resolution, or `SCREEN` at window size / `uiScale` (0: from screen density). |
| `simulatedSafeInsets` | null | Pretend there is a notch (desktop testing). |
| `touchControls` | `AUTO` | On-screen controls: `AUTO` (phones, hidden while a keyboard is used), `ALWAYS`, `NEVER`. |
| `touchControlsSkin` | null | Skin asset with custom touch-control drawables. |
| `saveFolder` | null | Folder for saves and settings. Null keeps them in memory (tests, jams). |
| `saveSlots`, `saveVersion` | 3, 1 | Number of slots, and the current save format version. |
| `defaultLanguage` | `"en"` | Fallback language for string tables. |
| `loadingScreen` | `LoadingScreen.bar()` | Drawn when a scene's assets are not ready at the end of a fade. Null for a plain colour. |
| `assetsDir`, `assetWatcher` | null | Hot reload: the source assets folder and a watcher (dev launcher only). |

## 3. `EngineContext`
Built once at boot and passed to every scene (`enter(EngineContext context)`, or `context()` inside a scene).

| Method | Service | Page |
| --- | --- | --- |
| `config()` | the `EngineConfig` | this page |
| `time()`, `timers()`, `tweens()` | game time, global timers and tweens | [Time, Timers and Tweens](Time-Timers-and-Tweens) |
| `events()` | the `EventBus` | [Events](Events) |
| `input()` | actions, axes, touch | [Input](Input) |
| `assets()` | assets by ID | [Assets](Assets) |
| `prefabs()` | the `PrefabLibrary` | [Prefabs and Data](Prefabs-and-Data) |
| `renderer()` | layers, camera, batch | [Rendering and Camera](Rendering-and-Camera) |
| `audio()` | sounds and music | [Audio](Audio) |
| `saves()`, `saveSettings()`, `flags()` | slots, settings, world flags | [Saves, Settings and World Flags](Saves-Settings-and-World-Flags) |
| `strings()` | string tables | [Localisation](Localisation) |
| `scenes()` | the `SceneManager` | [Scenes and Transitions](Scenes-and-Transitions) |
| `debug()` | the F3 overlay | [Debugging and Dev Tools](Debugging-and-Dev-Tools) |
| `service(Class)`, `findService(Class)` | your own registered services | below |

### Your own services
Tests and tools build a context with `EngineContext.Builder`, which can register extra services: `builder.register(Leaderboard.class, myBoard)`. Game code then calls `context.service(Leaderboard.class)`. In a normal game, a field in your own code is usually simpler.

### Testing with a context
```java
EngineContext ctx = new EngineContext.Builder(config)
        .assets(fakeAssets)
        .saves(inMemorySaves)
        .build();
```
`Builder.components()` and `Builder.flags()` expose the registry and the flags for test setup.

## 4. App lifecycle versus game pause
| | App suspend (`AppSuspended`) | Game pause (`time().setPaused(true)`) |
| --- | --- | --- |
| Caused by | window minimised, Android home button or call | your pause menu |
| Engine does | pauses audio, releases held input, saves settings and the suspend save | stops fixed steps and scaled time |
| On return | the long gap is ignored (no fast-forward), and `AppResumed` is published | you unpause |

Services can implement `AppLifecycleListener` (`onSuspend`, `onResume`, `onResize`). Game code usually listens to the `AppSuspended` and `AppResumed` events.

## 5. Best practices
- Keep `config(...)` in the game's `core` module and call it from both launchers, so desktop and Android cannot drift apart.
- Pass the version string into the config and show it in your menu. The demos read it from the jar manifest (desktop) or the package version name (Android).
- Don't keep the `EngineContext` in static fields. Pass it, or use `context()` inside a scene.
