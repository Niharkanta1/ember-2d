# Getting Started

This page takes you from a fresh clone to a running demo on desktop and on Android. It covers the tools, the build, the demos, and where the first logs go.

## 1. Prerequisites
| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 (Temurin recommended) | Gradle runs on it. All shared code is compiled with `--release 17`, so Java APIs newer than 17 are rejected at compile time. |
| Gradle | nothing to install | Use the wrapper (`./gradlew`, `.\gradlew.bat`). It downloads Gradle 9.9.2. |
| Android SDK | Platform 36, Build-Tools 36.0.0 | Needed even for desktop work, because the Android modules are part of every build. Point to it with `ANDROID_HOME` or `local.properties` (`sdk.dir=...`, never committed). |
| Git | any recent | `.gitattributes` normalises line endings. |
| Tiled | 1.10 or newer | Optional. The level editor ([mapeditor.org](https://www.mapeditor.org)). |
| inklecate / Inky | Ink 1.2.x | Optional. Only to edit Ink stories (RPG and visual novel). The compiled stories are committed. |
| An Android device or emulator | Android 6.0 (API 23) or newer | Optional. For running the Android builds. |

Install the Android parts with the SDK manager (you accept the licences yourself):
```sh
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

All library versions are pinned in `gradle/libs.versions.toml`: libGDX 1.14.2, the Android Gradle plugin, gdx-ai 1.8.2, blade-ink 1.2.1, JUnit, AssertJ, ArchUnit and Construo. Only dependencies listed in `documents/TECHNICAL_DECISIONS.md` may be added there.

## 2. Build and test everything
```sh
./gradlew build
```
`build` does all of this:
- compiles every module, including the Android library and the four Android demo apps;
- runs every unit test and architecture test;
- runs Android lint and the Android API check;
- checks the module dependency rules.

A green run ends with `BUILD SUCCESSFUL`. The current build runs about 650 tests.

Useful variations:
```sh
./gradlew :ember-core:test                    # one module's tests
./gradlew :kits:kit-novel:test --tests '*NovelDirectorTest*'
./gradlew build -x :ember-android:build       # skip Android while iterating on Java code
./gradlew projects                            # list all modules
```
Test reports are at `<module>/build/reports/tests/test/index.html`.

## 3. Run the demos on desktop
Each demo has a `desktop` module with a `run` task. `run` is a **dev run**, which turns on:
- debug logging and a dev log file;
- hot reload from the source assets;
- the F1 dev tools and the F3 overlay.

```sh
./gradlew :demos:demo-platformer:desktop:run
./gradlew :demos:demo-shooter:desktop:run
./gradlew :demos:demo-rpg:desktop:run
./gradlew :demos:demo-novel:desktop:run
./gradlew :demos:demo-gold-in-shadows:desktop:run
```

| Demo | Controls on desktop |
| --- | --- |
| Platformer | Arrows or WASD move, Space, Z or W jump, P or Escape pause. Levels 2 and 3 add wall jumps, an air jump and a dash. |
| Shooter | WASD move, the mouse aims, left button or Space fires, R reloads, P or Escape pauses. Dev builds have "Skip to boss". |
| RPG | WASD or arrows move, E, Space or Enter interact and advance text, P or Escape pause. |
| Visual novel | Click, Space or Enter advance; Escape or the Menu button opens the menu. |
| Gold in Shadows | WASD or arrows move, the mouse aims, left button or J fires, R reloads, E opens chests, Escape or P pauses. |

Extra desktop arguments (dev runs):
```sh
./gradlew :demos:demo-shooter:desktop:run --args="--touch-controls"            # on-screen controls with the mouse
./gradlew :demos:demo-platformer:desktop:run --args="--safe-insets=80,0,80,0"  # simulate a notch (pixels L,T,R,B)
```

## 4. Run a demo on Android
With a device (USB debugging on) or an emulator running:
```sh
./gradlew :demos:demo-rpg:android:installDebug
adb shell am start -n dev.ember2d.demo.rpg/.android.AndroidLauncher
```
The application IDs are `dev.ember2d.demo.platformer`, `.shooter`, `.rpg` and `.novel`. Debug builds run in dev mode and show the F3 overlay from the start (phones have no F3 key). See [Building for Android](Building-for-Android) for release builds and signing.

> Start apps with `am start`, not `adb shell monkey`. Monkey injects a random touch, which presses whatever button is under it.

## 5. Where things are
```text
ember-core/            the engine (one module, many packages)
ember-desktop/         desktop launcher helpers, logging, crash log
ember-android/         EmberAndroidLauncher
ember-devtools/        hot reload watcher, Dear ImGui tools (desktop dev only)
ember-box2d/           optional rigid-body module (placeholder; Phase 6)
kits/kit-*/            the five kits
demos/demo-*/          core (game code + assets), desktop, android
assets-src/            scripts that generated the demos' data (Python, Java), Ink sources
tools/android-api-check  checks that shared code only calls Java APIs present on Android 6
documents/             vision, architecture, decisions, specs, guide chapters
wiki/                  this handbook
```

## 6. Logs
- **Desktop:** the console. Dev runs also write `logs/<game>-dev.log` under the desktop module's folder.
- **Android:** logcat. The engine's log categories are the tags, for example `adb logcat -s core:* scene:* rpg:*`.
- **Release crashes on desktop:** `crash-<time>.log` in `%APPDATA%\<game>` (Windows), `~/Library/Application Support/<game>` (macOS) or `~/.local/share/<game>` (Linux).

More in [Logging, Debug Overlay and Dev Tools](Debugging-and-Dev-Tools).

## 7. Next steps
- Read [Architecture Overview](Architecture-Overview) for the big picture.
- Start your own game with [Creating a New Game Project](Creating-a-New-Game-Project).
- Pick a genre guide: [Platformer](Guide-Platformer), [Top-Down Shooter](Guide-Top-Down-Shooter), [Bullet Hell](Guide-Bullet-Hell), [RPG](Guide-RPG), [Visual Novel](Guide-Visual-Novel).
