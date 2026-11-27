# Design Principles

These principles explain why Ember2D looks the way it does. They come from `documents/VISION.md`, `ARCHITECTURE.md` and the decision records in `documents/adr/`.

## 1. An integration layer, not a new engine
Where libGDX or its ecosystem solves a problem well, Ember2D wraps it thinly and adds conventions:

| Area | libGDX or ecosystem piece |
| --- | --- |
| Asset loading | `AssetManager` |
| UI | Scene2D |
| Maps | the Tiled loaders |
| Particles | `ParticleEffect` |
| AI paths and steering | gdx-ai |
| Dialogue | Ink through blade-ink |

libGDX types such as `Vector2`, `Color`, `TextureRegion`, `Batch` and `Table` appear in the public API on purpose. They are the vocabulary, not something to hide (ADR-013).

**What this means for you:** libGDX knowledge carries over, and you can drop to libGDX where Ember2D has no opinion.

## 2. Game-driven development
Every engine feature was pulled in by a demo that needed it. The four demos are complete small games built only on public APIs, with no engine change made for a genre's sake.

**For you:** when you need a pattern, look in a demo first. It has been run on desktop and on an Android emulator.

## 3. The engine knows no genre
`ember-core` must not contain genre words such as `jump`, `weapon`, `quest`, `inventory`, `bullet`, `enemy`, `platformer`, `shooter`, `rpg` or `novel`. A test fails the build if it does. Genre logic lives in kits, and kits are optional.

**For you:** the engine stays small and general. Use the kits you need and ignore the others.

## 4. Data over code
- Levels, entity placement, prefabs, animation state machines, input, bullet patterns, items, stories and strings are data.
- Data files are versioned JSON or standard formats (TMX, Ink, `.p`).
- Each is checked on load, with every problem listed at once, naming the file and field.
- Dev runs hot-reload them.

**For you:** you can tune most of a game without recompiling, and designers can work in Tiled, Inky and text editors.

## 5. A predictable loop
The game loop has these properties:
- simulation runs in a fixed step (1/60 s by default) and rendering interpolates between steps;
- systems read time from `Time` and never measure it themselves;
- game pause is separate from app suspend, so going to the home screen never pauses your game logic by surprise;
- queued events are delivered at fixed points in the frame.

**For you:** movement is smooth on 60, 120 and 144 Hz screens, and it plays the same at any frame rate. Note that this is not bit-exact determinism across machines.

## 6. Scenes own and release everything
A scene's `World`, timers, tweens, event subscriptions, UI and asset group live and die with it.

**For you:** switching screens never leaks listeners or entities. Global services live on `EngineContext` instead.

## 7. Android is a first-class target
Every change keeps the Android build working:
- shared code is linted against API 23 on every build;
- R8 keep rules are generated from your component setup;
- the engine handles suspend, resume and GL context loss;
- touch controls, safe areas and vibration are built in.

**For you:** a game that builds also builds for Android, and it usually runs the same.

## 8. Testable without a screen
Most systems run headless: worlds, systems, collision, input bindings, saves, stories and bullets. The engine and the demos have about 650 tests, and the demos test their own data:
- `LevelReachabilityTest` walks each platformer level;
- `RpgDataTest` plays the RPG quest through its story functions;
- `ChapterTest` reads both novel endings.

**For you:** write tests for your rules and your data the same way. See the demos' `src/test` folders.

## 9. Rules enforced by the build
Architecture is checked, not just documented:
- package tiers, the allowed module graph and genre words;
- Android-safe APIs, no threads, no reflection outside `ember.data`;
- `@PublicApi` hygiene.

A failure names the rule and the offending line.

**For you:** your game's `core` module runs the same rules through its `ArchitectureTest`.

## 10. No surprises in releases
Release builds have these properties:
- dev tools never ship;
- the desktop zip carries its own Java runtime, pinned by checksum;
- crash logs are written in release builds;
- the Android version code comes from the version.

**For you:** a release built from the documented commands runs on a player's machine without setup. See [Building for Desktop](Building-for-Desktop) and [Building for Android](Building-for-Android).
