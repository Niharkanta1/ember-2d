# Creating a New Game Project

Ember2D games live **inside the Ember2D build** as three Gradle modules under `demos/<your-game>/`. The engine is not published to a Maven repository, and the root build's module rules only allow game modules with this layout. The **project generator** creates those modules for you: one command copies a template, renames the package, classes, titles and module names, and registers the game in `settings.gradle` (spec F-027).

```text
demos/my-game/
  core/       game code (scenes, components, systems) + assets/ (shared by both launchers)
  desktop/    DesktopLauncher, DevLauncher (dev runs), Construo packaging
  android/    AndroidLauncher, manifest, R8 rules, signing
```

## 1. Generate the project
From the repository root:
```sh
./gradlew newGame -PgameName=my-game -PgamePackage=com.example.mygame
./gradlew newGame -PgameName=forest-run -PgamePackage=com.example.forest -PgameTitle="Forest Run" -Ptemplate=platformer
```
| Property | Required | Rule | Default |
| --- | --- | --- | --- |
| `gameName` | yes | lower-case words joined by `-` (`my-game`), at most 40 characters. Becomes the folder `demos/<gameName>`, the Gradle path `:demos:<gameName>`, the save folder, the log name and the desktop package name | |
| `gamePackage` | yes | a Java package with at least two parts (`com.example.mygame`), no Java keywords, not inside `dev.ember2d`. Becomes the Java package, the Android `namespace` and `applicationId`, and the macOS identifier | |
| `gameTitle` | no | at most 60 characters, without line breaks or `" ' \ < > &`. The window title, the Android app name and the package's display name | the name's words capitalised (`My Game`) |
| `template` | no | `blank`, `platformer`, `shooter`, `rpg` or `novel` | `blank` |

The class names come from the name: `forest-run` gives `ForestRunGame` (the config) and `ForestRunComponents` (the component setup). A name that already ends in `-game` is not doubled: `my-game` gives `MyGame` and `MyGameComponents`.

The generator checks everything first and lists every problem together. It writes nothing if the folder already exists, the game is already in `settings.gradle`, or an option is invalid.

**Then:**
```sh
./gradlew :demos:my-game:desktop:run              # dev run with hot reload and the dev tools
./gradlew :demos:my-game:core:test                # your tests and the architecture rules
./gradlew :demos:my-game:android:installDebug     # on a connected device or emulator
```
Reload the Gradle project in your IDE so it picks up the new modules.

## 2. Pick a template
| Template | Your game | Kits it brings | What you get |
| --- | --- | --- | --- |
| `blank` | anything; start from nothing | none (`ember-core` only) | a config, an empty component setup, one scene that shows the title (a bobbing world text) and quits on Escape or Back, the Press Start 2P font, `manifest.json`, `input.json`, an architecture test |
| `platformer` | side-view platformer | kit-platformer | the whole platformer demo: levels, enemies, menus, art, sounds, tests |
| `shooter` | top-down shooter, arena game, bullet hell | kit-topdown, kit-bullets, kit-common | the whole shooter demo, including the boss stage |
| `rpg` | top-down RPG or adventure | kit-topdown, kit-novel, kit-common | the whole RPG demo; its Ink stories in `core/ink/` |
| `novel` | visual novel | kit-novel, kit-common | the whole novel demo; its Ink story in `core/ink/` |

- A demo template is a **full copy** of the demo, art and tests included. Delete what you don't need and replace the art. Keep `ART-CREDITS.txt` truthful: the copied art follows its original licence.
- `blank` takes its desktop and Android launchers from the shooter demo, so packaging, signing and R8 work the same way as in every demo.
- To add a kit to a `blank` game, uncomment or add `api project(':kits:kit-…')` in `core/build.gradle` and call the kit's `register` in your component setup.
- For `rpg` and `novel`, the stories' sources are in `demos/<gameName>/core/ink/`. The compiled `.ink.json` files are copied too, so the game builds without inklecate ([Build Differences per Kit](Build-Differences-per-Kit)).

### What the generator renames
| In the demo | In your game |
| --- | --- |
| package `dev.ember2d.demo.shooter` (and `.desktop`, `.android`) | your package, with the folders moved |
| `ShooterDemo`, `ShooterComponents` (`PlatformerDemo`/`DemoComponents`, `RpgDemo`/`RpgComponents`, `NovelDemo`/`NovelComponents`) | `<Name>Game`, `<Name>Components` |
| `Ember2D - Shooter Demo`, `Ember2D Shooter Demo`, `Ember2D Shooter` (`app_name`) | your title |
| `demos/demo-shooter`, `:demos:demo-shooter`, `demo-shooter` (save folder) | `demos/<gameName>`, `:demos:<gameName>`, `<gameName>` |
| `shooter-demo` (desktop package name, log and crash-log folder) | `<gameName>` |
| `assets-src/demo-rpg/ink` (Ink sources) | `demos/<gameName>/core/ink` |

Only text files are changed (`.java`, `.gradle`, `.xml`, `.pro`, `.txt`, `.md`, `.properties`, `.ink`). Images, sounds, fonts, maps and JSON are copied byte for byte. `build/`, `bin/`, `logs/` and IDE files are skipped. Comments that talk about "the demo" stay as they are.

### Removing a generated game
Delete `demos/<gameName>` and its three `include` lines (under `// <gameName> (created by newGame, F-027)`) from `settings.gradle`.

## 2a. Doing it by hand
The generator automates these steps; you only need them to understand the layout or to set up a different one. They use `demo-shooter` as the source and `my-game` as the new name.

1. **Copy the folder:** `demos/demo-shooter` to `demos/my-game`. Leave out the `build/` folders.
2. **Rename the Java packages:** `dev.ember2d.demo.shooter` becomes your package, for example `com.example.mygame`, in `core`, `desktop` (`src/main` and `src/dev`) and `android`. Move the folders to match.
3. **Rename the entry classes:** `ShooterDemo` (the config) and `ShooterComponents` (the component setup), and update the references in the launchers.
4. **Edit the build files.** In `desktop/build.gradle`, replace every `demo-shooter` path. Change:
   - `mainClass` and the `DevLauncher` name in the `run` task;
   - the `Implementation-Title` strings;
   - `construo { name, humanName, identifier }`;
   - the `ember.assetsDir` path.
5. **Android build file:** in `android/build.gradle`, set `namespace` and `applicationId` to your package and ID. Update the `assets.srcDirs` path, the `emberKeepRules` main argument (your `ComponentSetup` class) and the project dependencies.
6. **Android resources:** `android/src/main/res/values/strings.xml` (`app_name`) and the activity name in `AndroidManifest.xml`, if you renamed the launcher's package.
7. **Register the modules** in `settings.gradle`:
   ```groovy
   include 'demos:my-game:core'
   include 'demos:my-game:desktop'
   include 'demos:my-game:android'
   ```
   The root `build.gradle` allows any `:demos:<name>:core`, `:desktop` and `:android` modules already. Don't add an entry there unless you use a different layout.
8. **Run it:**
   ```sh
   ./gradlew :demos:my-game:desktop:run
   ./gradlew build
   ```
   `build` also runs your game's architecture test, so a forbidden import fails early.

## 3. What the template gives you
### The config (`core`)
```java
public final class MyGame {
    public static EngineConfig config(boolean devMode, String version) {
        EngineConfig config = new EngineConfig();
        config.virtualWidth = 320;               // the world is drawn at 320 x 180 and scaled up by whole numbers
        config.virtualHeight = 180;
        config.firstScene = MenuScene::new;      // the scene started at boot
        config.inputBindings = "input";          // asset ID of the bindings JSON
        config.saveFolder = "my-game";           // saves on disk; null keeps them in memory
        config.components = new MyComponents();  // every component data can create
        config.devMode = devMode;
        return config;
    }
}
```
All the fields are on [App, Config and Context](App-Config-and-Context).

### The component setup (`core`)
```java
public final class MyComponents implements ComponentSetup {
    @Override
    public void register(ComponentRegistry registry) {
        CommonKit.register(registry);   // the kits you use
        TopDownKit.register(registry);
        registry.register(CameraFollow.class, CameraFollow::new)
                .register(Enemy.class, Enemy::new);   // your own components
    }
}
```
It must be a `public` class with a no-argument constructor. The Android release build runs it to generate R8 keep rules.

### The launchers
Desktop (`desktop/src/main/java/.../DesktopLauncher.java`) follows this order:
1. `StartupHelper.startNewJvmIfRequired()` restarts the JVM on macOS with `-XstartOnFirstThread`.
2. `DesktopLogging.configure(devMode, "my-game")` sets up logging and the dev log file.
3. `CrashLog.install(...)` runs in release builds only.
4. `new Lwjgl3Application(new EmberGame(MyGame.config(devMode, version)), windowConfig)` starts the game.

`DevLauncher` (`desktop/src/dev/java`) adds hot reload and the dev tools. Only the `run` task uses it, and release packaging refuses to include `ember-devtools`.

Android (`android/src/main/java/.../AndroidLauncher.java`):
```java
public class AndroidLauncher extends EmberAndroidLauncher {
    @Override
    protected EngineConfig config(boolean devMode) {   // devMode = the app is debuggable
        EngineConfig config = MyGame.config(devMode, versionName());
        config.debugOverlayVisibleAtStart = devMode;
        return config;
    }
}
```

### The assets (`core/assets`)
- **`manifest.json`:** lists every asset by ID ([Assets](Assets)). Both launchers read this one folder: desktop from the classpath, Android packs it into the APK.
- **`input.json`:** the input bindings ([Input](Input)).
- **`ART-CREDITS.txt`:** where every image and sound came from. Keep it up to date when you replace the art.

## 4. Generated data
The demos' maps, prefabs and atlases were first generated by scripts in `assets-src/demo-*/` (Python 3, standard library only). After the first run they are ordinary data files: edit them by hand or in Tiled. Copy a script only if you want to regenerate from scratch.

## 5. Checklist for a new game
- [ ] Unique `applicationId` and Construo `identifier` (the generator sets both from your package).
- [ ] `config.saveFolder` set to your game's folder name.
- [ ] Every component used by data is registered in your `ComponentSetup`.
- [ ] `ART-CREDITS.txt` lists your assets and their licences.
- [ ] `./gradlew build` is green: tests, the architecture rules and the Android API check.
- [ ] The game runs on an Android device or emulator ([Building for Android](Building-for-Android)).
