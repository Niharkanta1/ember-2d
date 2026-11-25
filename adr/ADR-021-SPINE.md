# ADR-021: Skeletal animation through optional Spine module

- Status: Accepted (new) — module created only when a game needs it
- Date: 2026-11-25
- Context: Frame-by-frame animation (atlas, Aseprite) covers pixel-art games. Visual novels and higher-resolution games may want skeletal animation (breathing characters, smooth attacks). Spine's `spine-libgdx` is the reference runtime and supports all Spine features, but the Spine Runtimes license requires a Spine license to distribute software that contains them.
- Decision: Skeletal animation is provided only by an optional `ember-spine` module (Phase 6, or earlier if a game needs it) that adapts `spine-libgdx` to Ember2D's renderer and animation state machine (F-007). `ember-core` never depends on Spine. Using the module requires the developer to hold a Spine license.
- Alternatives: DragonBones or other free skeletal formats — Java/libGDX runtimes are unmaintained or third-party; reconsider if licensing becomes a blocker.
- Consequences: Games without Spine pay nothing. The F-007 state machine drives either frame clips or Spine animations through the same parameter model.
- Risks: License cost; Spine editor/runtime version coupling. Mitigation: optional module, versions pinned together.
- Impacted specifications: F-007, F-023.
