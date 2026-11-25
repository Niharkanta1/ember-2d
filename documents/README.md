# Ember2D — SPEC-Driven Development Package

**Working name:** Ember2D (verify trademark/domain availability before public release).
**Tagline:** A focused 2D game framework for platformers, shooters, bullet hell, RPGs and visual novels on libGDX — for desktop and Android.

This package is a living engineering specification, not a code implementation. It defines scope, architecture, module contracts, milestones, validation and a specification-first workflow.

> **Revision 2 (2026-11-25).** The plan was revised after an architecture review (`REVIEW-2026-11.md`): far fewer modules, kinematic collision by default with Box2D optional, libGDX types allowed in the public API, Tiled + in-game dev tools instead of a JavaFX editor, and a game-driven roadmap where a playable platformer exists by Phase 2. Same-day additions: Android as an official target, bullet-hell and visual novel kits, Tiled entity placement through prefabs, and an animation state machine. Pre-revision files are kept in `old-files/`.

## Start here
1. Read `VISION.md`
2. Read `ARCHITECTURE.md` and `TECHNICAL_DECISIONS.md` (full records in `adr/`)
3. Read `ROADMAP.md`
4. Follow `SPEC_DRIVEN_WORKFLOW.md`
5. Begin Phase 0 with `IMPLEMENTATION_PLAN.md`
6. Before each task, read its spec under `specs/` and the matching section of `TEST_PLAN.md`

## Documents
- `VISION.md` — purpose, users, platforms, goals, non-goals and success criteria
- `ARCHITECTURE.md` — modules, packages, dependency rules, runtime model and data flow
- `TECHNICAL_DECISIONS.md` — decision summary and dependency register; `adr/` holds the full records
- `ROADMAP.md` — phases and exit gates
- `IMPLEMENTATION_PLAN.md` — task sequence, spikes and Definition of Done
- `SPEC_DRIVEN_WORKFLOW.md` — rules for implementation agents and developers
- `CODING_GUIDELINES.md` — Java, Gradle, Android-safety, tests and code quality rules
- `TEST_PLAN.md` — verification strategy and acceptance gates
- `PROJECT_STRUCTURE.md` — repository layout
- `REVIEW-2026-11.md` — the architecture review that produced revision 2
- `specs/` — feature specifications (index in `specs/README.md`)
- `guide/` — user guide, one chapter per phase gate (`guide/00-getting-started.md` first)
- `spikes/` — spike findings (`spikes/S-01-imgui-java.md`, `spikes/S-04-android-java17-r8.md`, ...)
- `templates/` — templates for specs, tasks, spikes and decision records
- `images/` — logo and architecture diagram
- `old-files/` — pre-revision versions, kept for reference only; not authoritative

## Toolchain at a glance
| Area | Choice |
| --- | --- |
| Language / build | Java 17 language level, Gradle, project generated with gdx-liftoff |
| Framework | libGDX 1.14.x (pin the exact version in Phase 0) |
| Platforms | Desktop (LWJGL3) and Android; iOS not committed |
| Levels and entity placement | Tiled (TMX or Tiled JSON), object templates, prefab classes |
| Sprites / animation | Texture atlases; Aseprite JSON import; JSON animation state machines; optional Spine |
| UI | Scene2D.ui, on-screen touch controls |
| Dev tools | Dear ImGui via imgui-java, in-game, desktop dev builds only |
| Dialogue and visual novels | Ink via blade-ink |
| Bullet patterns | JSON, hot-reloadable |
| AI | gdx-ai (optional) |
| Physics | Own kinematic collision; Box2D as optional module |
| Packaging | Construo (desktop); Android Gradle plugin (APK/AAB) |
| Architecture tests | ArchUnit |

## Naming
Ember2D is the recommended working name. Alternatives: **Wolf2D**, **Forge2D**, **PixelForge**, **Luma2D**. Check trademarks, domains, GitHub names and existing products before committing to branding. Note that "Forge2D" is already used by a Box2D port in the Flame (Flutter) ecosystem.
