# Creating a New Game Project

Ember2D games live **inside the Ember2D build** as three Gradle modules under `demos/<your-game>/`. The engine is not published to a Maven repository, and the root build's module rules only allow game modules with this layout. Every demo doubles as a project template: the supported way to start a game is to copy the demo closest to your genre.

```text
demos/my-game/
  core/       game code (scenes, components, systems) + assets/ (shared by both launchers)
  desktop/    DesktopLauncher, DevLauncher (dev runs), Construo packaging
  android/    AndroidLauncher, manifest, R8 rules, signing
```

## 1. Pick a template
| Your game | Copy | Kits it brings |
| --- | --- | --- |
| Side-view platformer | `demos/demo-platformer` | kit-platformer |
| Top-down shooter or arena game | `demos/demo-shooter` | kit-topdown, kit-bullets, kit-common |
| Bullet hell or shoot-'em-up | `demos/demo-shooter` (boss scene) | kit-bullets, kit-topdown, kit-common |
| Top-down RPG or adventure | `demos/demo-rpg` | kit-topdown (rpg), kit-novel, kit-common |
| Visual novel | `demos/demo-novel` | kit-novel, kit-common |

## 2. Copy and rename
The steps below use `demo-shooter` as the source and `my-game` as the new name.

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
- [ ] Unique `applicationId` and Construo `identifier`.
- [ ] `config.saveFolder` set to your game's folder name.
- [ ] Every component used by data is registered in your `ComponentSetup`.
- [ ] `ART-CREDITS.txt` lists your assets and their licences.
- [ ] `./gradlew build` is green: tests, the architecture rules and the Android API check.
- [ ] The game runs on an Android device or emulator ([Building for Android](Building-for-Android)).
