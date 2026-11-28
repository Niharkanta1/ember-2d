# Incremental Implementation Plan

## Rules
- Implement one task or a tightly coupled small set at a time.
- Before coding: read the relevant spec, inspect current code, summarize the plan, the proposed API and affected packages.
- If the spec is still a draft, expand it to template depth as the first step of the task (see `SPEC_DRIVEN_WORKFLOW.md`).
- Do not modify unrelated files. Do not refactor existing behaviour without a separate approved task.
- Write/adjust tests before or alongside implementation.
- After coding: run the prescribed tests, report results, list files changed and deviations.
- Stop if the spec is ambiguous, an architecture rule is violated, or tests fail unexpectedly.
- Update docs only when behaviour/contracts changed.
- No speculative feature additions. Extraction of demo code into a kit is planned work, not refactoring.
- From Phase 1 on, every task that touches `ember-core` or a kit must keep the Android debug build green.

## Spikes
Time-boxed throwaway prototypes (`templates/SPIKE_TEMPLATE.md`). Code is deleted; the result is a short written finding.

| ID | Question | Time box | When |
| --- | --- | --- | --- |
| S-01 | Can imgui-java render panels in the Ember2D LWJGL3 window and share input correctly with the game? | 1 day | Phase 1 — done: yes (`spikes/S-01-imgui-java.md`) |
| S-04 | Does an Android build with Java 17 features (records, switch expressions), reflection-loaded components and R8 minification run correctly on the minimum API level? | 1 day | Phase 1 — done: yes, with rules (`spikes/S-04-android-java17-r8.md`) |
| S-02 | Does the kinematic mover handle a Tiled platformer map (tile seams, one-way, moving platform) without snagging? Does the libGDX loader resolve object templates and class-typed properties, or must Ember2D resolve them? | 2 days | Phase 2 — done: yes, with conditions (`spikes/S-02-collision-tiled.md`) |
| S-03 | Does blade-ink load an Inky-compiled story and save/restore its state on desktop and Android? | 0.5 day | Start of Phase 5 |

## Sequence

### Phase 0 — Foundation
- **I-000 Repository bootstrap** — gdx-liftoff skeleton (desktop + Android), module layout, Gradle conventions, version pins, CI. No engine systems. (`PROJECT_STRUCTURE.md`)
- **I-005 Architecture tests** — ArchUnit rules from `ARCHITECTURE.md`, running in CI.
- **I-008 Logging** — logger setup, levels, categories, dev file output.
- **I-009 Desktop packaging** — Construo configuration and a release task. (F-020)

- **G-0 Guide chapter** — write `documents/guide/00-getting-started.md` from the delivered code and demos; developer review and approval required for the phase gate (`ROADMAP.md`)

### Phase 1 — Runtime core
- **I-010 App lifecycle and EngineContext** (F-001, F-019)
- **I-015 Android launcher and smoke build** — `ember-android`, minimum API level decision, debug APK in CI, suspend/resume wiring (F-022, F-020)
- **I-016 Android API guard** — `tools/android-api-check` lints shared sources against minSdk in every build (follow-up of S-04; ADR-015)
- **I-020 Time and loop** — fixed step, catch-up limit, interpolation alpha, pause, time scale (F-002)
- **I-025 Timers, tweens, state-machine helper** (F-002)
- **I-030 Event bus** — immediate and queued delivery, scoped subscriptions (F-019)
- **I-040 ECS foundation** — handles, `EntityRef`, stores, families, phases, queued changes, transform with previous/current state (F-003)
- **I-050 Assets, part 1** — IDs, manifest, `AssetManager` wrapper, fallbacks (F-008)
- **I-060 Rendering and camera, part 1** — sprite system, layers, sorting, camera follow/bounds, pixel-perfect pipeline, context-loss recreation (F-004)
- **I-070 Scene lifecycle** — scene stack, enter/exit, per-scene `World`, cleanup (F-010)
- **I-080 Debug overlay** — FPS, frame time, entity count (F-014)
- **S-01 and S-04 spikes**

- **G-1 Guide chapter** — write `documents/guide/01-core-loop-ecs-scenes.md` from the delivered code and demos; developer review and approval required for the phase gate (`ROADMAP.md`)

### Phase 2 — Platformer slice
- **S-02 spike**
- **I-100 Input actions** — actions, axes, edge/hold state, two contexts (F-005)
- **I-105 Touch actions** — tap, long press, swipe, drag, multi-touch pointers as actions (F-022)
- **I-110 Tiled maps** — loading, rendering, parallax, animated tiles, tile collision mapping (F-009)
- **I-112 Prefabs and Tiled entity placement** — component registry, JSON prefabs, class/override/template/object-reference handling, two-pass spawn, generated R8 keep rules (F-009, F-010)
- **I-120 Kinematic collision** — actors, solids, one-way, riders, triggers, spatial hash queries (F-006)
- **I-122 Basic on-screen controls** — transparent joystick on the left driving a move axis, configurable action buttons on the right, engine-drawn, shown on touch screens; pulled forward from I-225 so Phase 2 features can be tested on Android (F-022, developer decision 2026-11-26)
- **I-130 Animation clips** — atlas and Aseprite import, playback, frame events (F-007)
- **I-135 Animation state machine** — parameters, states, transitions, priorities, validation, hot reload (F-007)
- **I-140 Text rendering** — bitmap and FreeType fonts (F-004)
- **I-150 Hot reload** — file watcher for maps, JSON, atlases, state machines (F-008)
- **I-160 Dev tools** — ImGui panels, inspector, ASM view, collider view (F-018)
- **I-170 demo-platformer level 1**

- **G-2 Guide chapter** — write `documents/guide/02-levels-prefabs-collision-animation.md` from the delivered code and demos; developer review and approval required for the phase gate (`ROADMAP.md`)

### Phase 3 — Platformer kit + services (MVP)
- **I-200 Extract kit-platformer** — move demo player code into the kit; add optional mechanics behind flags (F-015)
- **I-210 Audio** (F-011)
- **I-220 UI** — Scene2D skin, HUD, main/pause menu (F-012)
- **I-225 On-screen controls and safe areas** — builds on I-122: Scene2D-styled widgets, D-pad, layout editing and saving in settings, auto-hide with keyboard/gamepad, safe areas (F-022, F-012)
- **I-230 Settings, save and global data** — settings store, save slots, atomic write, suspend auto-save hook (F-013)
- **I-240 Scene transitions and preloading** (F-010)
- **I-245 Tiled type generator** — Gradle task writing prefab types into the Tiled project (F-009)
- **I-250 demo-platformer complete; desktop release** (F-020)
- **I-255 Android release build** — signed AAB, R8, device test (F-020, F-022)

- **G-3 Guide chapter** — write `documents/guide/03-platformer-ui-audio-saves-android.md` from the delivered code and demos; developer review and approval required for the phase gate (`ROADMAP.md`)

### Phase 4 — Shooter + bullet hell
- **I-300 kit-common** — health, damage, hitboxes, triggers, pools, spawners (F-021)
- **I-310 kit-topdown movement, Y-sort, scrolling camera** (F-016, F-004)
- **I-320 Shooter package** — weapons, reload/ammo, twin-stick aiming (F-016)
- **I-325 kit-bullets** — bullet world, circle collision, graze, emitters, patterns, bullet clear (F-024)
- **I-330 Enemy AI hooks with gdx-ai** (F-016)
- **I-340 demo-shooter with bullet-hell boss stage**

- **G-4 Guide chapter** — write `documents/guide/04-shooter-and-bullet-hell.md` from the delivered code and demos; developer review and approval required for the phase gate (`ROADMAP.md`)

### Phase 5 — RPG + visual novel
- **S-03 spike**
- **I-400 Interaction** (F-017, F-021)
- **I-410 Items and inventory** (F-017)
- **I-420 DialogueRunner in kit-common** (F-021)
- **I-430 World flags** (F-013)
- **I-440 Localisation** — string tables and Ink translation approach (spec to be written first)
- **I-450 demo-rpg**
- **I-460 kit-novel** — tag commands, characters, dialogue box, choices, backlog, auto/skip, save anywhere, gallery (F-023)
- **I-470 demo-novel**

- **G-5 Guide chapter** — write `documents/guide/05-rpg-and-visual-novel.md` from the delivered code and demos; developer review and approval required for the phase gate (`ROADMAP.md`)

### Phase 6 — Optional extras
- **I-500 ember-box2d** (F-006B) — done 2026-11-28: `RigidBody`, `Box2dSystem`, debug drawing, tile bodies; the platformer demo's Crates scene
- **I-510 Particles** (F-014)
- **I-520 Shaders and materials** — hit flash, outline, palette swap (F-026)
- **I-530 Gamepad** (F-005) — done 2026-11-28: `PAD_*` binding names with analog sticks, connect/disconnect events, desktop (jamepad) and Android backends; the platformer is playable with a pad. The rebinding UI followed in I-535
- **I-535 Rebinding UI** (F-005) — `RebindMenu` "press a key / button" screen, `listenForInput`, axis-direction overrides; platformer Settings → Controls
- **I-550 Standalone editor decision** (ADR-009 review) — done 2026-11-28: no editor; the jobs it would do are documented in the wiki page Working-Without-an-Editor
- **I-560 ember-spine** (ADR-021, F-007; only with a Spine license) — skipped by the developer (2026-11-28)
- **I-570 iOS decision** (ADR-019 review; spike first if pursued) — skipped by the developer (2026-11-28)
- **I-580 Project generator** — `./gradlew newGame` creates a game under `demos/` from a blank or demo template (F-027)
- **I-590 Asset tools** — `assetManifest` and `makeClips` generate the manifest and clip sets (F-028)

- **G-6 Guide chapters** — one chapter per adopted extra in `documents/guide/`; developer review and approval required. Drafts written 2026-11-28 (particles, materials, Box2D, gamepad, project and asset tools); waiting for review

### Demo: Gold in Shadows (F-029, F-030) — approved 2026-11-28
The developer's own libGDX game, rebuilt on Ember2D. One task at a time, in this order:
- **I-600 Project and assets** — done 2026-11-28: `newGame` (blank) as `demo-gold-in-shadows`; the original art with ART-CREDITS; clip sets; generated sounds; `ConvertLevels.java` turns the ASCII levels into Tiled maps with the original auto-tiling; the reachability test
- **I-610 Field of view and fog** (F-030) — done 2026-11-28: `kit-topdown` `vision`: `VisionField`, `FogLayer`, `VisionSystem`, `HiddenInFog`
- **I-620 Player** — done 2026-11-29: movement, mouse/stick aim, the 600 RPM rifle with reload, hearts and difficulty, hit feedback, camera, HUD
- **I-630 Creatures** — done 2026-11-29: rat, slug, ghoul, cultist: behaviours, perception, alerts and noise, telegraphed attacks, drops
- **I-640 The world** — gold, medkits, chests, key and exit door, spike traps, the three-level campaign, score and saves
- **I-650 Screens and platforms** — title, settings (difficulty, controls), pause, level-complete, death and end screens; Android twin-stick controls; gamepad; emulator checks
- **I-660 Documentation** — the demo README (how to play, how it is built, original → Ember2D class map, screenshots), the wiki guide, the F-030 wiki page

## Definition of Done
- Acceptance criteria fulfilled.
- Unit/integration tests pass; architecture tests pass; Android debug build passes (from Phase 1).
- No new dependency cycle; no new third-party dependency outside the register in `TECHNICAL_DECISIONS.md`.
- Public API documented and marked `@PublicApi`.
- Resource lifecycle validated (including Android suspend/resume where relevant).
- A demo or test proves the behaviour.
- If the task changes a public API covered by an approved guide chapter, that chapter is updated.
- No unrelated refactor.
- Specification and implementation match, or the deviation is documented and approved.
