# 00 — Getting started

This chapter covers what Phase 0 delivers: building Ember2D, running the platformer demo, logging, the architecture rules that keep the code base in shape, and packaging a desktop release. Later chapters cover the engine itself.

> Status: approved by the developer at the Phase 0 gate (2026-11-25). Updated for I-010 (the demo starts through `EmberGame`), I-015 (Android debug build, section 8) and I-030 (`EmberGame`, `EngineConfig` and `EngineContext` are in `dev.ember2d.app`).

## 1. What you get in Phase 0
- A Gradle multi-module build: `ember-core`, `ember-desktop`, `ember-android`, `ember-devtools`, `ember-box2d`, five kits under `kits/`, and the platformer demo under `demos/demo-platformer/` (`core`, `desktop` and `android`). Most modules are still empty placeholders; the module layout is final (see `PROJECT_STRUCTURE.md`).
- The demo opens an empty window. It exists to prove the build, the launcher and packaging.
- Logging (`Log`, `LogConfig`) with a dev log file on desktop.
- Architecture tests that enforce the rules in `ARCHITECTURE.md` on every build.
- Construo packaging: a zip per platform that runs without Java installed, plus crash logs.
- GitHub Actions CI: build and tests on every push and pull request; release zips on pushes to `main`.

## 2. Prerequisites
| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 (Temurin recommended) | Gradle runs on it. Code is compiled to the Java 17 language level and API, so newer APIs are rejected at compile time. |
| Gradle | none to install | Use the wrapper (`gradlew` / `gradlew.bat`); it downloads Gradle 9.9.2. |
| Android SDK | Platform 36 and Build-Tools 36.0.0 | Needed because `ember-android` is part of every build. Point to it with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=...` (never committed). |
| Git | any recent | `.gitattributes` normalises line endings; no `core.autocrlf` tweaks needed. |

Install the Android parts with the SDK manager (you accept the license yourself):
```powershell
sdkmanager "platforms;android-36" "build-tools;36.0.0"
```

Pinned versions live in one place, `gradle/libs.versions.toml` (libGDX 1.14.2, Android Gradle plugin 9.5.2, JUnit 6, AssertJ, ArchUnit, Construo). Only dependencies listed in `TECHNICAL_DECISIONS.md` may be added there.

## 3. Build and test
From the repository root (on Windows use `.\gradlew.bat`, elsewhere `./gradlew`):
```powershell
.\gradlew.bat build
```
`build` compiles every module (including the Android library), runs all unit and architecture tests, runs Android lint, and checks module dependencies. A green run ends with `BUILD SUCCESSFUL` and the line `Module dependencies OK for 15 projects.`

Useful variations:
```powershell
.\gradlew.bat :ember-core:test                 # one module's tests
.\gradlew.bat build -x :ember-android:build    # skip Android while iterating on Java code
.\gradlew.bat projects                         # list all modules
```
Test reports: `<module>/build/reports/tests/test/index.html`.

## 4. Run the demo
```powershell
.\gradlew.bat :demos:demo-platformer:desktop:run
```
A 1280×720 window titled "Ember2D - Platformer Demo dev" opens and stays dark; close it to stop. `run` starts the game in **dev mode** (`-Dember.devMode=true`), which turns on debug logging and the dev log file.

The launcher is `demos/demo-platformer/desktop/src/main/java/dev/ember2d/demo/platformer/desktop/DesktopLauncher.java`. Its order is the pattern every desktop launcher follows:
1. `StartupHelper.startNewJvmIfRequired()` — restarts the JVM with `-XstartOnFirstThread` on macOS (required by LWJGL3) and avoids a crash on Windows user names with non-Latin characters.
2. `DesktopLogging.configure(devMode, "platformer-demo")` — log levels and the dev log file.
3. `CrashLog.install(...)` — release builds only.
4. `new Lwjgl3Application(new EmberGame(PlatformerDemo.config(devMode)), configuration)` — the game is configured, not subclassed; blocks until the window closes.
5. `DesktopLogging.shutdown()` — closes the dev log file.

## 5. Logging
### Writing log messages
Get a logger once per class, by category, and keep it in a field:
```java
import dev.ember2d.core.Log;

public final class LevelLoader {
    private static final Log LOG = Log.get("levels");

    public void load(String levelId) {
        LOG.info("Loading " + levelId);
    }
}
```
Levels, from most to least severe: `ERROR`, `WARN`, `INFO`, `DEBUG` (`OFF` disables a category). `error` and `warn` also accept a `Throwable`.

In per-frame code, avoid building strings that are thrown away:
```java
if (LOG.isEnabled(LogLevel.DEBUG)) {
    LOG.debug("Entities alive: " + count);
}
```

### Configuring levels and output
`LogConfig` is set once at startup (the desktop launcher does it for you):
```java
LogConfig.setDefaultLevel(LogLevel.INFO);       // all categories
LogConfig.setLevel("assets", LogLevel.DEBUG);   // one noisy category
LogConfig.addSink(mySink);                      // extra destination, e.g. an in-game console
```
By default messages go to the libGDX logger: the console on desktop, logcat on Android. Before libGDX has started (or in unit tests) they go to standard error as `LEVEL [category] message`. A sink that throws never stops other sinks.

### Dev mode vs release
| | Dev (`gradlew run`) | Release (packaged zip) |
| --- | --- | --- |
| Default level | `DEBUG` | `INFO` |
| Dev log file | `logs/platformer-demo-dev.log` under the working directory (for `run`: `demos/demo-platformer/desktop/logs/`) | none |
| Crash log | no (exceptions surface in the console) | yes, see section 7 |

A dev log line looks like:
```text
2026-11-25 22:08:40.037 INFO  [launcher] Ember2D Platformer Demo dev (dev mode)
```
`logs/` is git-ignored. Dev mode will move to `EngineConfig.devMode` in Phase 1 (I-010); the `ember.devMode` system property is the Phase 0 stand-in.

`Log`/`LogConfig` are static on purpose: logging is the one exception to "no static service locator" (`ARCHITECTURE.md`), because it is needed before the engine context exists.

## 6. Architecture rules
Every `build` checks the rules of `ARCHITECTURE.md` automatically. You do not run anything extra, but you need to understand a failure when you see one.

### What is enforced
| Rule | Where it applies | Example of a violation |
| --- | --- | --- |
| Engine package layering (rules 1–4a): `core` → `event`/`ecs`/`assets`/`input` → `render`/`camera`/… → `tilemap` → `scene`/`data`/`save` → `app` (`EmberGame`, `EngineContext`; ADR-022) | `ember-core` | a class in `ember.core` importing `ember.render`, or any engine package importing `ember.app` |
| Only documented packages | `ember-core` | adding `dev.ember2d.misc` without updating `ARCHITECTURE.md` and the rules |
| No genre words in engine names (rule 5): `jump`, `weapon`, `quest`, `inventory`, `bullet`, `enemy`, `platformer`, `shooter`, `rpg`, `novel` (whole words; `BulletinBoard` is fine) | `ember-core` | `dev.ember2d.collision.JumpSolver` |
| Allowed module graph (rules 6–8) | all modules | `kit-platformer` depending on `kit-novel` |
| Android-safe Java (rule 9): no `java.nio.file`, `java.awt`, `javax.*`, `java.lang.management`, `ProcessBuilder`, `System.exit` | `ember-core`, kits, demo `core` | `Paths.get(...)` in a kit |
| No threads | `ember-core`, kits, demo `core` | `new Thread(...)`, executors, `Thread.sleep` |
| `Gdx.files` only in `ember.assets` and `ember.save` | `ember-core`, kits, demo `core` | reading a file in `ember.audio` |
| Reflection only in `ember.data` (rule 10) | `ember-core`, kits, demo `core` | `Class.forName` in a kit |
| Public API (rule 11): `internal` packages are never `@PublicApi`; `@PublicApi` types never expose Box2D, Spine, backend, LWJGL, `android.*`, file types (`File`, `Path`, `FileHandle`) or `internal` types | `ember-core`, kits, demo `core` | a `@PublicApi` method returning `FileHandle` |
| No package cycles (rule 12) | `ember-core` | `ecs` and `input` importing each other |

Desktop-only modules (`ember-desktop`, `ember-devtools`) are exempt from the Android rules.

### Reading a failure
An architecture failure is a normal test failure. The test name is the rule; the message names the class, the offending call and the source line:
```text
ArchitectureTest > engineRules() > no classes that reside in a package 'dev.ember2d..' should ...
    or should call method where System.[exit] ... FAILED
Method <dev.ember2d.render.TempViolation.quit()> calls method <java.lang.System.exit(int)>
```
A module-graph failure comes from the `verifyModuleDependencies` task:
```text
:kits:kit-platformer must not depend on :kits:kit-novel (allowed: [:ember-core, :kits:kit-common, :kits:kit-bullets])
```
Fix the code, not the rule. A rule change is an architecture change and needs an ADR.

### Where the rules live
- All ArchUnit rules: `ember-core/src/testFixtures/java/dev/ember2d/archtest/EmberArchitectureRules.java`.
- Each module's `ArchitectureTest` (in `src/test/java`) applies them. A new kit or demo `core` module copies that three-line test and adds `testImplementation testFixtures(project(':ember-core'))`.
- The allowed module graph: `allowedModuleDependencies` in the root `build.gradle`. A new module must be added there, or the build fails as soon as it depends on another module.
- `EmberArchitectureRulesTest` proves each rule fires on deliberately broken fixture classes (`ember-core/src/test/java/archfixture/`).

### Marking public API
Types meant for game code carry `@PublicApi` (`dev.ember2d.core.PublicApi`). Implementation details go in a sub-package named `internal`, for example `dev.ember2d.core.internal.GdxLogSink`.

## 7. Package a desktop release
```powershell
.\gradlew.bat :demos:demo-platformer:packageWinX64
```
Other targets: `packageLinuxX64`, `packageMacX64` (Intel), `packageMacM1` (Apple Silicon). The first run downloads the target's JDK (about 200 MB); later runs reuse it.

Output: `demos/demo-platformer/desktop/build/construo/dist/platformer-demo-winX64.zip` (about 39 MB). Unzipped:
```text
platformer-demo.exe                      launcher
desktop-0.1.0-SNAPSHOT-release.jar       game + engine + libGDX + natives (built by the releaseJar task)
runtime/                                 Temurin 21 trimmed by jlink; no Java install needed
app/platformer-demo.json                 launcher settings (main class, class path)
```
Run `platformer-demo.exe`. The window title shows the version from Gradle (`0.1.0-SNAPSHOT`); the build runs in release mode.

What packaging guarantees:
- The bundled JDK is pinned by release and SHA-256 in `demos/demo-platformer/desktop/build.gradle`; a tampered download fails the build.
- `verifyReleaseClasspath` fails packaging if `ember-devtools` would end up in the release.
- To check that crash logs work, start the packaged game with `--crash-test`; it throws at startup on purpose and writes a crash log.

**Crash logs.** When a release build dies from an uncaught exception, it writes `crash-<yyyyMMdd-HHmmss>.log` (version, OS, Java version, stack trace) to:
| OS | Folder |
| --- | --- |
| Windows | `%APPDATA%\platformer-demo\` |
| macOS | `~/Library/Application Support/platformer-demo/` |
| Linux | `$XDG_DATA_HOME/platformer-demo/` or `~/.local/share/platformer-demo/` |

The path is also logged at `ERROR` level. A fatal error screen comes with Phase 1 (I-010).

**macOS:** the macOS targets are configured but have not been tested on a Mac yet. The app needs no `-XstartOnFirstThread` flag; the launcher handles it.

## 8. Android debug build
Build and install the demo on a phone (USB debugging on) or an emulator:
```powershell
.\gradlew.bat :demos:demo-platformer:android:assembleDebug
adb install -r demos\demo-platformerndroiduild\outputspk\debugndroid-debug.apk
adb shell am start -n dev.ember2d.demo.platformer/.android.AndroidLauncher
```
The APK (about 5 MB) runs on Android 6 (API 23) and newer, in landscape, and contains libGDX natives for `arm64-v8a`, `armeabi-v7a` and `x86_64`. Debug builds run in dev mode (debug logging).

The Android launcher is five lines: it extends `EmberAndroidLauncher` and returns the same config as the desktop launcher.
```java
public class AndroidLauncher extends EmberAndroidLauncher {
    @Override
    protected EngineConfig config(boolean devMode) {
        return PlatformerDemo.config(devMode);
    }
}
```

**Smoke test.** Logs go to logcat; the engine's tags are the log categories (`core`, `launcher`, ...):
```powershell
adb logcat -c
adb shell am start -W -n dev.ember2d.demo.platformer/.android.AndroidLauncher
adb shell input keyevent KEYCODE_HOME          # expect "Suspended"
adb shell am start -W -n dev.ember2d.demo.platformer/.android.AndroidLauncher   # expect "Resumed"
adb shell input keyevent KEYCODE_BACK          # expect "Shut down"
adb logcat -d -s core:* launcher:* AndroidRuntime:E
```
Expected: `launcher` logs package and version, then `core` logs `Starting Ember2D`, `Suspended`, `Resumed`, `Suspended`, `Shut down`, and no `AndroidRuntime` errors. The emulator used for verification is an API 23 `default` x86_64 image (`emulator -avd ember-api23`).

Release builds (signed AAB, R8) come in Phase 3 (I-255).

## 9. Continuous integration
`.github/workflows/ci.yml` has two jobs:
- **Build and test** — every push to `main`, every pull request, and manual runs: `./gradlew build` on Ubuntu with Temurin 21. Uploads the Android debug APK (`platformer-demo-android-debug`), and test reports when it fails.
- **Package desktop builds** — pushes to `main` and manual runs only (Actions tab → CI → Run workflow): builds the Windows and Linux zips and uploads them as the `platformer-demo-desktop` artifact.

## 10. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| `Failed to find Build Tools revision 36.0.0` | Android build-tools missing | `sdkmanager "build-tools;36.0.0"` |
| `requires ... compile against version 36 or later` | Android platform 36 missing | `sdkmanager "platforms;android-36"` |
| `SDK location not found` | neither `ANDROID_HOME` nor `local.properties` set | set one of them |
| Compile error about a Java API that exists in your JDK | code is compiled with `--release 17` | use a Java 17 API (Android safety) |
| Architecture test fails after adding a package | package not documented | add it to `ARCHITECTURE.md` and `ENGINE_PACKAGES` in `EmberArchitectureRules` (architecture change) |
| `... is not listed in allowedModuleDependencies` | new module | add it to the root `build.gradle` map, following `ARCHITECTURE.md` |
| No dev log file | game started outside `gradlew run` (release mode) | use `run`, or pass `-Dember.devMode=true` |
| Packaged game closes immediately | an exception at startup | read the crash log (section 7) |
| Android app closes immediately | an exception at startup | `adb logcat -d -s AndroidRuntime:E core:*` |
| Window title says `dev` instead of a version | running from classes, not the packaged jar | expected; the version comes from the jar manifest |

## 11. Where to look when something breaks
1. **Console** — the first `ERROR` or `WARN` line and its category.
2. **Dev log** — `demos/demo-platformer/desktop/logs/platformer-demo-dev.log` (dev mode), with timestamps.
3. **Crash log** — the per-OS folder in section 7 (release builds).
4. **Test reports** — `<module>/build/reports/tests/test/index.html`; architecture failures name the rule and the offending line.
5. **Gradle** — rerun with `--stacktrace`; `build/reports/problems/problems-report.html` lists build deprecations.
6. **CI** — the Actions tab; failed runs carry a `test-reports` artifact.
