# Vision & Product Definition

## Vision
Create a small, modular, Java-first 2D game framework on libGDX, with practical reusable runtime systems and a content pipeline built on proven external tools, enabling rapid creation of platformers, top-down shooters, bullet-hell games, top-down RPGs and visual novels for desktop and Android.

Ember2D's value is **an opinionated integration layer plus genre kits**, not a re-implementation of libGDX. Where libGDX or its ecosystem already solves a problem well (asset loading, UI, tilemaps, particles, AI, dialogue), Ember2D wraps it thinly and adds conventions.

## Target users
- Indie and solo developers
- Java developers
- Developers building pixel-art or stylized 2D games for PC and Android phones
- Developers who want reusable game systems without a general-purpose engine's complexity

## Target platforms
- **Desktop** (Windows, Linux, macOS) — development and release.
- **Android** — release target from the MVP; exercised from Phase 1 (ADR-019).
- **iOS** — not committed. The architecture keeps it possible; a decision is taken in Phase 6 if needed.

## Core principles
1. **Game-driven development.** Every engine feature is pulled in by a demo game that needs it. A playable build exists at every phase gate.
2. Runtime and tooling are separate layers; tools never become a runtime dependency.
3. libGDX remains the platform foundation; do not fork it and do not hide it behind duplicate abstractions (ADR-013).
4. Prefer a few cohesive modules with package boundaries enforced by tests over many small modules (ADR-003).
5. Gameplay systems are optional kits; the engine never contains genre-specific logic.
6. Data-driven content: levels and entity placement in Tiled, entity defaults in JSON prefabs, dialogue and novels in Ink, bullet patterns and animation state machines in JSON.
7. Testable headless logic where possible.
8. Implement specifications in small, reviewable increments.
9. No broad refactors without an approved change proposal.
10. Every engine and kit change must keep the Android build working.

## Genre profiles (kits)
- **Platformer** (`kit-platformer`): run, jump, variable jump height, jump buffering, coyote time; optional double jump, dash, wall jump/slide, one-way and moving platforms.
- **Top-down shooter** (`kit-topdown`, `shooter` package): 8-way movement, aiming (mouse, touch twin-stick, gamepad), weapon definitions, reload/ammo, enemy AI hooks.
- **Bullet hell / shoot-'em-up** (`kit-bullets`): thousands of bullets, data-defined patterns (rings, spirals, aimed, waves), small hitbox and grazing, scrolling camera.
- **Top-down RPG** (`kit-topdown`, `rpg` package): movement (free or grid), interaction, items and inventory, Ink dialogue, world flags for persistent state.
- **Visual novel** (`kit-novel`): Ink scripts with staging tags (backgrounds, characters, expressions, music), choices, backlog, auto/skip, save anywhere, CG gallery. Also usable for cutscenes in other genres.
- **Shared** (`kit-common`): health, damage, hit/hurt boxes, triggers, pools, spawners, Ink dialogue runner.

Kits are starter modules, not mandatory dependencies.

## MVP definition
**The MVP is complete at the end of Phase 3** (see `ROADMAP.md`): a small but complete platformer with menu, HUD, audio, settings and save, packaged for desktop and running on Android with touch controls, built only on Ember2D public APIs. Phases 4 and 5 extend the proven engine to shooter/bullet-hell, RPG and visual novel kits.

MVP capabilities:
- Desktop and Android launchers; predictable fixed-step loop with render interpolation.
- Sprites, Tiled maps with entity placement through prefabs, camera with pixel-perfect mode.
- Lightweight ECS, scenes, asset IDs over libGDX `AssetManager`.
- Kinematic collision, triggers and spatial queries.
- Input actions with keyboard, mouse and touch; on-screen controls.
- Frame animation (atlas and Aseprite) with an animation state machine; text.
- Audio, Scene2D-based HUD and menus, settings and save slots.
- Debug overlay, in-game dev tools, hot reload in dev builds.
- A Construo-packaged desktop release and an Android AAB.

## Non-goals for initial releases
- 3D engine
- Visual scripting or a general scripting language
- Networking/multiplayer
- Console certification/platform support
- iOS (not committed; see ADR-019)
- Advanced skeletal animation authoring (Spine is an optional integration only)
- Full compositing/post-processing suite
- General-purpose UI design suite
- A standalone editor application (revisit after the demos ship; ADR-009)
- A custom tilemap format or tilemap editor (Tiled is the level editor; ADR-016)
- A visual novel editor or bullet pattern editor (Ink/Inky and JSON with hot reload)
- Advanced lighting/shadow editor
- A complete clone of Unity/Godot

## Success criteria
- Four demo games (platformer, shooter with a bullet-hell boss stage, RPG, visual novel) are built using Ember2D public APIs only, with no engine changes made for genre-specific reasons.
- Every demo runs on desktop and on an Android phone.
- No demo touches Box2D, Spine, a platform backend or raw file paths outside adapter modules. (Using libGDX core types such as `Vector2` or `TextureRegion` is allowed.)
- Core runtime packages have documented contracts and automated tests.
- A new game can start by copying a demo and launch with minimal boilerplate.
- Levels, prefabs, patterns, state machines and saves are versioned, human-readable and inspectable.
- Each phase passes its exit gate with a playable or shippable build.
