# ADR-003: Few library modules with enforced package boundaries

- Status: Accepted (amends D-003; updated 2026-11-25 for Android, bullets and visual novels)
- Date: 2026-11-25
- Context: The original structure proposed ~35 Gradle modules (18 engine, 3 gameplay, 8 editor, 3 templates, 3 demos). For a solo developer each module adds build configuration, test setup, publishing and cross-module refactor cost. Several module boundaries also overlapped (scene vs world vs prefab, `engine-api` vs `engine-physics-api`).
- Decision: Engine subsystems are packages inside `ember-core`, with dependency rules enforced by ArchUnit tests in CI. Public API types are marked `@PublicApi`; `internal` packages are private. No `engine-api` module. Modules exist only for platform launchers, optional native/licensed dependencies, dev-only code and kits:
  - **Platform:** `ember-core`, `ember-desktop`, `ember-android`
  - **Optional / dev:** `ember-devtools` (dev builds, desktop only), `ember-box2d` (optional), `ember-spine` (optional, created only when adopted; ADR-021)
  - **Shared kits:** `kit-common`, `kit-bullets`
  - **Genre kits:** `kit-platformer`, `kit-topdown`, `kit-novel`
  - **Demos:** `demo-platformer`, `demo-shooter` (includes a bullet-hell stage), `demo-rpg`, `demo-novel`
  That is 10 library modules (11 with `ember-spine`) plus 4 demos.
- Kit dependency rule: genre kits may depend on shared kits; shared kits may depend only on `ember-core` (and `kit-bullets` on `kit-common`); genre kits never depend on each other.
- Alternatives: (1) Keep ~35 modules — rejected, too costly to maintain. (2) Single module — rejected, optional native dependencies (Box2D, ImGui, Spine) and dev-only code must stay out of game builds.
- Consequences: Faster builds and refactors; boundaries are checked by tests instead of the compiler's module graph. A package becomes a module only when it gains a second consumer with different dependencies.
- Risks: ArchUnit rules can be skipped if CI is not enforced. Mitigation: architecture tests are part of the baseline quality gate.
- Impacted specifications: all; `PROJECT_STRUCTURE.md`, `ARCHITECTURE.md`.
