# ADR-005: Kinematic collision by default, Box2D optional

- Status: Accepted (supersedes original D-005 "Box2D behind engine physics interfaces")
- Date: 2026-11-25
- Context: All three target genres need precise, kinematic character control rather than rigid-body simulation. With Box2D dynamic bodies, platformers suffer friction against walls, ghost collisions on tile seams, and need pre-solve workarounds for one-way platforms and riders. Top-down shooters need cheap overlap tests for many projectiles. RPGs need movement against tiles and trigger zones. An abstraction designed around Box2D concepts (bodies, materials, contact solver) would push these genres into workarounds.
- Decision: `ember.collision` provides kinematic actors and solids with `moveAndCollide`/`moveAndSlide`, AABB (and optional circle) colliders, tile-grid solids, one-way solids, moving solids that carry riders, triggers, layers/masks, and spatial-hash queries (overlap, point, ray). `ember-box2d` is a separate optional module wrapping the classic libGDX Box2D extension for games that want rigid bodies; it has its own API and is not hidden behind the kinematic API. The newer Box2D v3 bindings (gdx-box2d) are incubating and will be reconsidered when stable.
- Alternatives: (1) Box2D only — rejected for game feel. (2) A backend-neutral physics abstraction — rejected; only one backend exists and the two models are too different. (3) jbump or dyn4j — possible later, but the required kinematic subset is small enough to own.
- Consequences: A few hundred lines of collision code owned by the project, with deterministic, testable behaviour. Platformer, shooter and RPG kits share the same mover.
- Risks: Slopes are not supported initially. Mitigation: listed as a later extension in F-006.
- Impacted specifications: F-006, F-006B, F-009, F-015, F-016, F-017, F-021.
