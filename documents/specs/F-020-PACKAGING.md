# F-020 — Packaging and Release Builds

- Status: Desktop part Ready (expanded and approved in the I-009 plan, 2026-11-25); complete demo and desktop release Ready (I-250, approved 2026-11-27 under the standing approval for Phases 3 to 6; see "Complete demo and desktop release (I-250)"); Android release Ready (I-255, approved 2026-11-27 under the standing approval; see "Android release build (I-255)")
- Phase: 0 (desktop empty window), 1 (Android smoke build), 3 (MVP demo release on desktop and Android)
- Module / package: `ember-desktop`, `ember-android`, demo build scripts
- Depends on: F-001, F-022
- Tasks: I-009, I-015, I-250, I-255
- Decisions: ADR-015, ADR-019

## Goal
Downloadable desktop and Android builds exist from the first phases, so shipping is never a last-phase surprise.

## Scope
**Desktop**
- Construo Gradle plugin (as recommended on the libGDX wiki) producing zipped builds with a minimised bundled JRE for Windows x64, Linux x64 and macOS (x64 and Apple Silicon).
- Release flavour excludes `ember-devtools`, sets `devMode=false`, and uses packed atlases.
- Version number from Gradle injected into the game (shown in title/menu).
- Crash log written to a per-game folder in release builds.
- Optional: itch.io upload with butler (manual step documented).

**Android**
- Standard Android Gradle plugin: debug APK for devices/emulators, signed release AAB for Google Play.
- R8 minification with keep rules generated from the component registry (F-010); `ember-devtools` never included.
- Version code/name derived from the Gradle version.
- Upload keystore handling documented; the keystore never committed.

## Desktop design (I-009)
**Build configuration** (per demo, in `demos/<demo>/desktop/build.gradle`; moved to a shared script once a second demo exists):
- Construo Gradle plugin 2.3.0 with targets `winX64`, `linuxX64`, `macX64` and `macM1`. Each target downloads a pinned Temurin JDK 21 (exact release and SHA-256 in the build file); jlink trims it to the modules the game needs.
- Tasks: `:demos:<demo>:package<Target>` (aliases of `:demos:<demo>:desktop:package<Target>`), producing `demos/<demo>/desktop/build/construo/dist/<name>-<target>.zip`.
- Version: the Gradle `version` is written to the jar manifest (`Implementation-Version`); the launcher shows it in the window title and logs it. Running from source shows `dev`.
- Dev vs release: `gradlew run` sets `-Dember.devMode=true` (debug logging, dev log file). Packaged builds do not set it, so they run in release mode. `EngineConfig.devMode` (F-001) takes over in I-010.
- A check fails packaging if `ember-devtools` is on the release runtime classpath.

**API sketch** (`ember-desktop`, `dev.ember2d.desktop`):
```java
@PublicApi public final class CrashLog {
    public static void install(String gameName, String version);       // default uncaught-exception handler
    public static Path write(Throwable error, Path directory, String version); // returns the written file
    public static Path appDataDirectory(String gameName);              // per-OS per-game folder
}
```
**Crash log:** release builds install `CrashLog`. An uncaught exception writes `crash-<yyyyMMdd-HHmmss>.log` (version, OS, Java version, stack trace) to the per-game folder: `%APPDATA%\<game>` on Windows, `~/Library/Application Support/<game>` on macOS, `$XDG_DATA_HOME/<game>` or `~/.local/share/<game>` on Linux. The path is logged at ERROR. The fatal error screen is part of F-001 (I-010).

**CI:** a `package` job on pushes to `main` and manual runs builds the Windows and Linux zips and uploads them as workflow artifacts. Not run on pull requests (each JDK download is about 200 MB).

**Tests:** unit tests for the crash-log writer and the per-OS folder; packaging verified by running the Windows zip's executable without Java on `PATH` or `JAVA_HOME`. macOS targets are configured but verified only when a Mac is available.

## Android debug build (I-015)
- `./gradlew :demos:demo-platformer:android:assembleDebug` produces `demos/demo-platformer/android/build/outputs/apk/debug/android-debug.apk` (about 5 MB, three ABIs). It is part of every `./gradlew build`, and CI uploads it as the `platformer-demo-android-debug` artifact on every run.
- Version name comes from the Gradle version; version code is 1 until the release build (I-255), which also adds signing, R8 and the AAB.

## Complete demo and desktop release (I-250)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. The Phase 3 exit gate asks for "a small complete platformer (3+ levels) with main menu, pause, HUD, audio, settings and save, packaged for desktop". This task completes the demo and makes the desktop zip; I-255 does Android.

**Three levels, each teaching one more mechanic of `kit-platformer` (F-015):**

| Level | Map | Mechanics | How the mechanics are switched on |
| --- | --- | --- | --- |
| 1 Meadow | `maps/level1.tmx` (existing) | run, jump, one-way planks, pigs, spikes, moving platform | prefab defaults |
| 2 Cliffs | `maps/level2.tmx` | wall slide and wall jump; a vertical moving platform | the player object has a class-typed `PlatformerBody` property (`wallSlide`, `wallJump`), the I-245 workflow |
| 3 Sky | `maps/level3.tmx` | air jump and dash over spike pits | `PlatformerBody` property (`airJumps` 1, `dash` true) |

- **Asset groups:** each map is in its own group (`level1`, `level2`, `level3`).
- **Making the maps:** levels 2 and 3 are made by `assets-src/demo-platformer/maps/make_levels.py` from ASCII layouts. The script writes ordinary Tiled files, which can then be edited in Tiled. The script is only a starting point; Tiled is the editor.
- **Level code:** `DemoScene(int level)` loads `map.level<N>`. A `Levels` table in the demo holds each level's number, title and map.

**Progress (F-013):**
- **The slot:** save slot 0 holds the campaign. Its data is `{ "unlocked": N }`, and its `SlotMeta` is the scene `level<N>`, the total play time and the summary "Level N of 3".
- **Finishing a level** unlocks the next one and saves the slot.
- **Best times:** they stay in global data, under `best.level<N>`.
- **A damaged slot:** the backup is used when it is readable. Otherwise progress starts at level 1, and the problem is logged.

**Menus:**
- **Main menu:** Play opens a level list. Unlocked levels show their best time; locked levels show their name and are disabled. The menu preloads the group of the newest unlocked level.
- **Results panel:** "Next level" (when there is one), "Play again" and "Main menu". After level 3 the title is "You finished the demo!".
- **Version:** the main menu shows the game version in the top right corner. `PlatformerDemo.config(devMode, version)` takes it from the launcher: the desktop jar manifest, or the Android package's version name.

**Desktop release:**
- **The zip:** `./gradlew :demos:demo-platformer:packageWinX64` builds the Windows zip with Construo (I-009). The Temurin JDK is already cached in the build folder, so there is no new download.
- **Check:** run the zip's executable from a folder outside the repository with no `JAVA_HOME`. The menu, the three levels and the saved progress must work, and the saves go to `~/.ember2d/demo-platformer`.
- **Not checked here:** the Linux and macOS zips are configured (I-009) but cannot be run on this machine.

**Tests:**
- `LevelMapTest` covers every level: each object is a prefab; there is one player, one goal and a checkpoint; the key objects stand on ground.
- A reachability check (`LevelReachabilityTest`) uses a cautious jump model: 4 tiles across and 3 up with a plain jump; more with the abilities the level switches on. It walks from the player to the goal over standable cells and never stands on spikes, which shows each level can be finished.
- `ProgressTest` covers unlocking and saving, the damaged-slot fallback, and the level list's locked and unlocked states.

## Decisions in I-250
- **Reachability model:** the plain jump reach is 5 columns. With 4, level 1 (already played and approved) failed, because its 4-tile pits are 5 columns from edge to edge.
- **The new mechanics are needed:** `LevelReachabilityTest.eachNewMechanicIsNeeded` shows that level 2 cannot be finished without the wall jump, nor level 3 without the air jump and dash.
- **Level list:** it shows the panel alone, like the settings menu. With the title and the frog above it, the list ran off a 180-pixel-high screen on the emulator. The version label sits top right, because at the bottom it covered "Back".
- **Ability log:** `DemoScene` logs the player's mechanics as set by the map (for example "air jumps 1, dash true, wall jump false"), so a wrong class property shows up in the log.
- **Checked on the emulator (API 23):**
  - The level list shows locked and unlocked levels; I made a save that unlocks all three and pushed it with `run-as`.
  - Levels 2 and 3 load, and their mechanics come from the map.
  - In level 3 the B (dash) button appears, which also closes an I-225 check that was still open.
  - The version shows in the menu.
- **Checked on desktop:** the Windows zip, run without Java on `PATH` or `JAVA_HOME`, starts in release mode, keeps saves in `~/.ember2d/demo-platformer` and enters the main menu.
- **Not checked:**
  - Nobody has played levels 2 and 3 to the end, so the "Next level" and "You finished the demo!" panels have not been seen. The reachability test covers whether the levels can be finished.
  - Playing the desktop zip through is left to the developer at the phase gate.

## Android release build (I-255)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6.

**Version code.**
- **The formula:** the code comes from the Gradle version `MAJOR.MINOR.PATCH` (a `-SUFFIX` such as `-SNAPSHOT` is ignored): `MAJOR * 10000 + MINOR * 100 + PATCH`. For example, 0.1.0 is 100 and 1.2.3 is 10203.
- **Limits:** MINOR and PATCH must be below 100; anything else fails the build with a message.
- **Version name:** the full Gradle version.

**Signing (the keystore is never committed).**
- **The upload key:** the release build type signs with an upload key read from four Gradle properties:
  - `ember.signing.storeFile` (path)
  - `ember.signing.storePassword`
  - `ember.signing.keyAlias`
  - `ember.signing.keyPassword`
- **Where the values live:** the developer keeps them in `~/.gradle/gradle.properties` (outside the repository). CI has no upload key yet; adding one (the keystore as a repository secret, passed as `ORG_GRADLE_PROJECT_ember.signing.*`) is a later step for the developer.
- **Without the properties:** the release build falls back to the debug key, so `./gradlew build` and CI work without secrets. The build then logs a warning that the bundle cannot be uploaded to Google Play.
- **Making the key:** a one-off manual step with `keytool`, documented in the guide (G-3). The agent does not create keys.

**Outputs.**
- **The bundle:** `./gradlew :demos:demo-platformer:android:bundleRelease` writes `build/outputs/bundle/release/android-release.aab`, minified with R8 and the generated keep rules (F-010).
- **The mapping file:** R8 writes `build/outputs/mapping/release/mapping.txt`, which de-obfuscates crash stack traces. Keep it with each uploaded bundle.
- **Checking it on a device:** a bundle cannot be installed directly without `bundletool`. That is a new download, so it is not used here. The emulator check uses `assembleRelease` instead, which builds the same build type with the same R8 rules and signing.
- **CI:** builds the bundle on pushes to `main` and uploads the bundle and the mapping file as the `platformer-demo-android-release` artifact.

**Device test (emulator, API 23):** install the release APK, then:
- check that the menu, all three levels (prefabs spawned in the minified build), the on-screen controls, settings and progress survive a force-stop;
- check that log lines show real scene names (`-keepnames`, I-120).
A real phone is still open from Phase 2; it is listed as a risk for the gate.

## Decisions in I-255
- **Outputs, as built:** `bundleRelease` gives a 3.6 MB bundle with the three ABIs, two dex files, all assets and the generated project file. `assembleRelease` gives a 4.6 MB APK with version code 100 and version name 0.1.0-SNAPSHOT (checked with `aapt dump badging`). R8 writes `mapping.txt`.
- **Checked on the emulator (API 23), release APK:**
  - It installs and starts in release mode.
  - Log lines name `MenuScene` and `DemoScene`, so the keep-names rules work under R8.
  - Level 1 spawns its 30 prefab entities, including `pig_2`'s class-typed `Patroller` property.
  - The level list and the version label show.
- **Not checked:**
  - Levels 2 and 3 in the release build: the release APK is not debuggable, so no save could be pushed to unlock them. They use the same prefabs and the same class-property path as level 1.
  - Signing with a real upload key: the agent does not create keys. The wiring is the standard `signingConfigs` block, chosen when all four properties are set. The developer creates the key, sets the properties, and checks that `bundleRelease` stops warning.
  - A real phone: still open from Phase 2, and part of the Phase 3 exit gate ("running on an Android phone").
- **No downloads:** `bundletool` was not downloaded. The bundle's contents were checked by listing it, and the runtime check used the release APK.

## Non-goals
- Desktop installers, code signing and notarisation (document as later steps), Steam integration, iOS and web packaging, Play Store listing automation.

## Acceptance criteria
- `./gradlew :demos:demo-platformer:package<Platform>` produces a runnable zip on CI for at least the developer's platform.
- Release build runs on a machine without Java installed.
- Release build has no dev tools classes and no dev file watcher.
- CI builds a debug APK on every change to `ember-core` or a kit; the Phase 3 release AAB installs and runs on a device.
