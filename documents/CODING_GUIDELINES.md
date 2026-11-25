# Coding Guidelines

## General
- Java 17 language level (ADR-015); use the project's pinned JDK and Gradle conventions.
- Keep classes focused, APIs small and packages cohesive.
- Prefer composition over inheritance. Kits are used by registering components and systems, not by subclassing.
- Avoid global mutable state and static service locators; pass `EngineContext`.
- Use explicit lifecycle/disposal contracts for resources.
- Do not introduce dependencies outside the register in `TECHNICAL_DECISIONS.md` without approval.

## libGDX usage (ADR-013, ADR-014)
- libGDX core types (`Vector2`, `Rectangle`, `Color`, `TextureRegion`, `Batch`, `Viewport`, `Array`, `ObjectMap`, pools, Scene2D) may appear in public APIs.
- Never expose Box2D or Spine types, LWJGL3/GLFW or Android backend types, or file paths in public APIs. Games refer to assets by ID.
- Prefer the libGDX implementation over writing a new one (assets, UI, particles, Tiled, JSON, pooling). A custom replacement needs an ADR.
- Use libGDX collections and pools in per-frame code; use `java.util` elsewhere if clearer.

## Android safety (ADR-015, ADR-019)
Applies to `ember-core`, all kits and demo `core` code. Desktop-only modules (`ember-desktop`, `ember-devtools`) are exempt.
- Use only Java library APIs available on the minimum Android API level (with core library desugaring). Not allowed: `java.nio.file`, `java.awt`, `javax.*`, `ProcessBuilder`, `java.lang.management`, `System.exit`.
- File access only through `Gdx.files`, and only inside `ember.assets` and `ember.save`.
- No threads except documented asset-loading threads.
- Reflection only through the component registry in `ember.data` (so R8 keep rules can be generated). Do not look up classes by name anywhere else.
- Any GL object not managed by `AssetManager` (frame buffers, custom meshes, shaders) must be registered for recreation after context loss.
- Keep per-frame allocation near zero; garbage collection pauses are more visible on phones.
- Never assume a keyboard or mouse: every gameplay action needs a touch binding or on-screen control.

## Naming
- Packages: lowercase.
- Classes/interfaces/enums/records: PascalCase.
- Methods/fields: camelCase.
- Constants: UPPER_SNAKE_CASE.
- Components are nouns ending in `Component` only when ambiguous (`Health`, `Velocity`, `ColliderComponent`); systems end in `System`.
- Test classes end in `Test`; architecture tests in `ArchitectureTest`.
- No genre words (`jump`, `weapon`, `quest`, `inventory`, `bullet`) in `ember-core` packages.

## Structure
- Production code: `src/main/java`. Tests: `src/test/java`, mirroring production packages. Test resources: `src/test/resources`.
- Demo code belongs in `demos/`, never in engine or kit modules.
- Public API types carry `@PublicApi`. Implementation details go in `internal` sub-packages.

## Java
- Use records and immutable data where practical (definitions, config, events).
- Components are mutable plain objects with a public no-arg constructor (needed for JSON prefabs) and are registered in the component registry.
- Avoid allocation in per-frame loops; do not prematurely optimise without measurements.
- Do not swallow exceptions. Errors name the file, object and field involved.
- Document public APIs and lifecycle expectations.
- No Java object serialization.

## Tests
- Unit-test logic without a window when possible (libGDX math and collections work headless; use the headless backend where `Gdx.files` is needed).
- Integration tests for backend adapters (Box2D, ImGui, asset loading).
- Deterministic tests for timing and state logic using an injectable clock.
- Collision tests use small hand-built grids and assert exact positions.
- Bullet pattern and animation state machine tests run headless at a fixed step and assert exact positions/states.
- The Android debug build is part of CI; Android-specific behaviour (lifecycle, context loss) is checked in the demo smoke test on an emulator or device.
- Keep tests isolated and reproducible. Do not rely on manual tests alone for core contracts.

## Change discipline
- No unrelated refactoring.
- No renaming/moving files without task scope.
- No new abstractions unless required by the spec or the approved task plan.
- Update documentation for approved contract changes.
