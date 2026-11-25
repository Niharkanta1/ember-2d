# Technical Decisions (ADR Summary)

Full records for new or changed decisions are in `adr/`. Revision 2 changes come from `REVIEW-2026-11.md`; later 2026-11-25 updates add Android, bullet hell, visual novels, Tiled entity placement and the animation state machine.

| ID | Decision | Status | Record |
| --- | --- | --- | --- |
| D-001 | Java as primary language | Accepted | — |
| D-002 | libGDX as base platform/rendering framework | Accepted | — |
| D-003 | Few library modules (10, plus optional Spine) in platform / shared-kit / genre-kit tiers; package boundaries enforced by ArchUnit | Accepted (amended) | `adr/ADR-003-MODULES.md` |
| D-004 | Custom lightweight ECS with object-based components; Ashley as fallback | Accepted (amended) | `adr/ADR-004-ECS.md` |
| D-005 | Own kinematic collision by default; Box2D as an optional module | Accepted (supersedes original D-005) | `adr/ADR-005-COLLISION.md` |
| D-006 | JSON for prefabs, scene sidecars, patterns, state machines, saves and settings; Tiled maps for levels | Accepted (amended) | `adr/ADR-016-TILED.md` |
| D-007 | Runtime and tooling remain separate | Accepted | — |
| D-008 | Desktop and Android release targets; iOS not committed (was: desktop-first MVP) | Accepted (amended) | `adr/ADR-019-ANDROID.md` |
| D-009 | No JavaFX editor; Tiled + in-game ImGui dev tools; standalone editor revisited after the demos ship | Accepted (replaces proposed D-009) | `adr/ADR-009-TOOLING.md` |
| D-010 | `SpriteBatch` renderer with a `FrameBuffer` pixel-perfect pass | Accepted (amended) | — |
| D-011 | No scripting/visual scripting in MVP (Ink for dialogue and novels is content, not scripting) | Accepted | — |
| D-012 | Genre-specific features in optional kits | Accepted | — |
| D-013 | libGDX core types are part of the public API; only Box2D, Spine, backends and file paths are hidden | Accepted (new) | `adr/ADR-013-LIBGDX-TYPES.md` |
| D-014 | Prefer libGDX built-ins and wrap thinly (`AssetManager`, Scene2D, `ParticleEffect`, Tiled loader, gdx-ai) | Accepted (new) | `adr/ADR-014-BUILT-INS.md` |
| D-015 | Java 17 language level; Android-safe Java APIs in engine and kits; TeaVM as the only future web path | Accepted (new, amended) | `adr/ADR-015-JAVA-PLATFORMS.md` |
| D-016 | Tiled as the level and entity-placement editor | Accepted (new) | `adr/ADR-016-TILED.md` |
| D-017 | Ink (blade-ink) for dialogue and visual novels; `DialogueRunner` in `kit-common` | Accepted (new, amended) | `adr/ADR-017-INK.md` |
| D-018 | Game-driven development: every engine feature is pulled in by a demo | Accepted (new) | `adr/ADR-018-GAME-DRIVEN.md` |
| D-019 | Android as an official target from Phase 1; iOS kept possible but not committed | Accepted (new) | `adr/ADR-019-ANDROID.md` |
| D-020 | Bullets as a data-oriented system outside the ECS (`kit-bullets`) | Accepted (new) | `adr/ADR-020-BULLETS.md` |
| D-021 | Skeletal animation only through an optional Spine module (license required) | Accepted (new) | `adr/ADR-021-SPINE.md` |
| D-022 | `ember.app` is the composition root (`EmberGame`, `EngineContext`, `EngineConfig`); `ember.core` depends on no other engine package | Accepted (new) | `adr/ADR-022-APP-COMPOSITION-ROOT.md` |

## Change policy
A decision change requires an ADR (`templates/ADR_TEMPLATE.md`) with context, alternatives, consequences, migration impact and approval before implementation.

## Dependency register
Third-party dependencies must be listed here before use. Each sits behind one small adapter so it can be replaced.

| Dependency | Used by | Purpose | Adapter |
| --- | --- | --- | --- |
| libGDX 1.14.x (core, LWJGL3, Android backend, freetype; headless backend for tests) | all | Platform | — (foundation) |
| Android Gradle plugin, Android SDK | `ember-android`, demo launchers | Android builds | — (build only) |
| gdx-box2d (classic v2 extension) | `ember-box2d` | Optional rigid bodies | `ember-box2d` API |
| imgui-java | `ember-devtools` | Dev tool panels (desktop) | `ember-devtools` |
| blade-ink | `kit-common` | Ink runtime for dialogue and novels | `DialogueRunner` |
| gdx-ai | `kit-topdown` (`shooter`, `rpg`), optional | Pathfinding, steering, behaviour trees | Kit AI package |
| gdx-controllers | `ember-core` (`input`), Phase 6 | Gamepad | `ember.input` |
| spine-libgdx | `ember-spine`, optional, Phase 6 | Skeletal animation (commercial license required) | `ember-spine` |
| ArchUnit | tests | Architecture rules | — (test only) |
| JUnit 5, AssertJ | tests | Testing | — (test only) |
| Construo Gradle plugin | `ember-desktop`, demo desktop launchers | Desktop packaging with a bundled JRE | — (build only) |
