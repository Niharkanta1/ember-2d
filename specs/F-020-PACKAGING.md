# F-020 — Packaging and Release Builds

- Status: Draft
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

## Non-goals
- Desktop installers, code signing and notarisation (document as later steps), Steam integration, iOS and web packaging, Play Store listing automation.

## Acceptance criteria
- `./gradlew :demos:demo-platformer:package<Platform>` produces a runnable zip on CI for at least the developer's platform.
- Release build runs on a machine without Java installed.
- Release build has no dev tools classes and no dev file watcher.
- CI builds a debug APK on every change to `ember-core` or a kit; the Phase 3 release AAB installs and runs on a device.
