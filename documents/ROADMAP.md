# Roadmap & Exit Gates

Game-driven (ADR-018): every phase ends with a playable or shippable build. A playable platformer level exists at the end of Phase 2. **The MVP is complete at the end of Phase 3** and ships on desktop and Android (ADR-019).

| Phase | Name | Exit gate |
| --- | --- | --- |
| 0 | Foundation | Packaged desktop build opens an empty window |
| 1 | Runtime core | Sprite moves smoothly at 60 Hz and 144 Hz; same build runs on Android |
| 2 | Platformer slice | One playable level, edited in Tiled, with enemies placed as prefabs |
| 3 | Platformer kit + services (MVP) | Small complete platformer with menu and save, on desktop and Android |
| 4 | Shooter + bullet hell | Shooter demo with a bullet-hell boss stage, on public API only |
| 5 | RPG + visual novel | RPG demo and novel demo, on public API only |
| 6 | Optional extras | Each item pulled in by a demo that needs it |

## Guide chapter at every phase gate
Every phase exit gate also requires a **user guide chapter** in `documents/guide/`, written by the implementing agent from the actual code and demos, and **reviewed and approved by the developer**. A phase is not complete until its chapter is approved.

Each chapter explains how to *use* what the phase delivered (not its internals):
- What the feature is for and when to use it.
- Step-by-step usage with code and data examples taken from the demos.
- Data formats with a complete annotated sample (prefab, Tiled object, state machine, pattern, Ink tags, as relevant).
- Common mistakes and how to recognise them.
- Where to look when something breaks (logs, debug overlay, dev-tools panel).

Chapters must match the code at the gate; later phases update earlier chapters when an API changes.

| Phase | Chapter |
| --- | --- |
| 0 | `00-getting-started.md` — build, run, package for desktop |
| 1 | `01-core-loop-ecs-scenes.md` — game loop, time, ECS, events, scenes, assets, Android build |
| 2 | `02-levels-prefabs-collision-animation.md` — Tiled maps and entity placement, prefabs, collision, animation state machine, dev tools, hot reload |
| 3 | `03-platformer-ui-audio-saves-android.md` — platformer kit, UI, audio, settings and saves, touch controls, releases |
| 4 | `04-shooter-and-bullet-hell.md` — common kit, top-down movement, weapons, bullet patterns |
| 5 | `05-rpg-and-visual-novel.md` — interaction, inventory, Ink dialogue, novel staging, world flags, localisation |
| 6 | One chapter per adopted extra (e.g. `06-box2d.md`, `06-spine.md`) |

## Phase 0 — Foundation
**Goal:** reproducible build, module skeleton and a shippable empty game.
- Generate the skeleton with gdx-liftoff (desktop and Android launchers), then trim to the module layout in `PROJECT_STRUCTURE.md`.
- Pin Java 17 language level and libGDX (1.14.x) versions.
- CI: build, unit tests, ArchUnit architecture tests.
- Logging setup (levels, categories, file output in dev).
- Construo packaging for Windows, Linux and macOS (F-020).
- Spec and task conventions in place.

**Exit gate:** CI green; a Construo-packaged build launches an empty window on the developer's OS. Guide chapter `documents/guide/00-getting-started.md` approved by the developer.

## Phase 1 — Runtime core
- App lifecycle and `EngineContext` (F-001, F-019).
- Loop, time, fixed step, render interpolation, timers, tweens (F-002).
- Event bus (F-019).
- ECS with entity references and phases (F-003).
- Minimal assets: IDs, manifest, `AssetManager` wrapper (F-008 part 1).
- Sprite rendering, layers, camera, pixel-perfect pipeline (F-004 part 1).
- Scene lifecycle and scene stack (F-010 part 1).
- Debug overlay: frame metrics (F-014 part 1).
- `ember-android` launcher; Android debug build in CI; context-loss handling for the frame buffer (F-022 part 1).
- Spikes S-01 (imgui-java in the game window) and S-04 (Android build with Java 17 features and R8).

**Exit gate:** a sprite moves with frame-independent, interpolated motion that looks smooth at 60 Hz and 144 Hz; pause/resume and scene switch work; the same build runs on an Android device and survives backgrounding; lifecycle, time and ECS tests pass. Guide chapter `documents/guide/01-core-loop-ecs-scenes.md` approved by the developer.

**Closed 2026-11-26.** Smooth motion confirmed by the developer on desktop; Android verified on the API 23 emulator (a real-device check is still open); pause/resume and scene switching covered by tests (the demo shows them once input arrives in Phase 2).

## Phase 2 — Platformer slice
- Input actions and contexts, including touch actions (F-005, F-022).
- Tiled map loading, rendering, tile collision mapping (F-009).
- Component registry, JSON prefabs and Tiled entity placement: classes, property overrides, templates, object references (F-009, F-010).
- Kinematic collision, triggers, spatial queries (F-006).
- Animation: atlas and Aseprite import, events, animation state machine (F-007).
- Text rendering (F-004 part 2).
- Hot reload in dev builds (F-008 part 2).
- In-game dev tools (F-018).
- `demo-platformer`: one level with run, jump, coyote time, jump buffer, enemies, a hazard and a goal, all placed in Tiled. Player code lives in the demo for now.
- Spike S-02 (start of phase): kinematic collision and template/class-property loading against a Tiled map.

**Exit gate:** one playable level, edited in Tiled (including enemy placement) and hot-reloaded while the game runs; player animation driven by the state machine; collider view in dev tools. Guide chapter `documents/guide/02-levels-prefabs-collision-animation.md` approved by the developer.

## Phase 3 — Platformer kit + services (MVP)
- Extract `kit-platformer` from the demo; add optional mechanics behind flags (F-015).
- Audio (F-011).
- Scene2D UI: HUD, main menu, pause menu (F-012).
- On-screen touch controls, safe areas, back button (F-022).
- Settings store, save slots and global data (F-013).
- Scene transitions and per-scene asset preloading (F-010 part 2).
- Tiled custom-type generator from prefabs (F-009).
- Release builds of `demo-platformer`: Construo desktop zips and an Android AAB (F-020).

**Exit gate:** a small complete platformer (3+ levels) with main menu, pause, HUD, audio, settings and save, packaged for desktop and running on an Android phone with touch controls. This is the MVP. Guide chapter `documents/guide/03-platformer-ui-audio-saves-android.md` approved by the developer.

## Phase 4 — Shooter + bullet hell
- `kit-common`: health, damage, hit/hurt boxes, triggers, pools, spawners (F-021).
- `kit-bullets`: bullet world, circle collision, graze, emitters and patterns (F-024).
- `kit-topdown` movement; Y-sort render mode; scrolling camera mode (F-016, F-004).
- `shooter` package: weapons on `kit-bullets`, reload/ammo, twin-stick touch aiming (F-016).
- Enemy AI via gdx-ai (optional dependency).
- `demo-shooter` with a bullet-hell boss stage.

**Exit gate:** shooter demo playable on desktop and Android, with a boss using at least three pattern types and 3,000 bullets at 60 fps on a mid-range phone; built on public API only, with no genre-specific engine change. Guide chapter `documents/guide/04-shooter-and-bullet-hell.md` approved by the developer.

## Phase 5 — RPG + visual novel
- Spike S-03 (blade-ink story loading and state save/restore).
- `DialogueRunner` in `kit-common` (F-021).
- `rpg` package: interaction, items and inventory, Ink dialogue (F-017).
- World flags persisted with saves (F-013).
- `kit-novel`: tag-driven staging, dialogue box, choices, backlog, auto/skip, save anywhere, CG gallery (F-023).
- Localisation string tables and Ink translation approach (spec to be written in this phase; text-heavy games need it).
- `demo-rpg`: a small town and dungeon with an NPC conversation, a door and a chest that stay opened, and one cutscene staged with `kit-novel`.
- `demo-novel`: one chapter, two characters, a choice that changes the ending.

**Exit gate:** RPG and novel demos playable on desktop and Android, built on public API only; save/load restores world state and novel stage state. Guide chapter `documents/guide/05-rpg-and-visual-novel.md` approved by the developer.

## Phase 6 — Optional extras
Pulled in only when a demo or a real game needs them:
- `ember-box2d` optional module (F-006B).
- `ember-spine` optional skeletal animation (ADR-021; requires a Spine license).
- Particles (F-014 part 2), shaders/materials (hit flash, palette swap, outline).
- Gamepad via gdx-controllers (F-005).
- Lighting, profiling tools.
- Decision point: standalone editor yes/no (ADR-009).
- Decision point: iOS port yes/no (would need its own ADR and spike; ADR-019).

**Exit gate:** each item passes its own spec acceptance and is used by at least one demo. Each adopted extra has its guide chapter approved by the developer.
