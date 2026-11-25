# F-020 — Packaging and Release Builds

- Status: Desktop part Ready (expanded and approved in the I-009 plan, 2026-11-25); Android part Draft
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

## Non-goals
- Desktop installers, code signing and notarisation (document as later steps), Steam integration, iOS and web packaging, Play Store listing automation.

## Acceptance criteria
- `./gradlew :demos:demo-platformer:package<Platform>` produces a runnable zip on CI for at least the developer's platform.
- Release build runs on a machine without Java installed.
- Release build has no dev tools classes and no dev file watcher.
- CI builds a debug APK on every change to `ember-core` or a kit; the Phase 3 release AAB installs and runs on a device.
