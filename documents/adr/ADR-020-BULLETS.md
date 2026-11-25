# ADR-020: Bullets as a data-oriented system outside the ECS

- Status: Accepted (new)
- Date: 2026-11-25
- Context: Bullet-hell games need thousands of simultaneous bullets (target: 3,000 at 60 Hz on a mid-range Android phone). Treating each bullet as an ECS entity with components and kinematic collision costs per-entity bookkeeping, component lookups and broad-phase work that bullets do not need: they move in straight or simple curved paths and only test against a few hitboxes.
- Decision: `kit-bullets` stores bullets in parallel primitive arrays (position, velocity, acceleration, angle, lifetime, radius, sprite index, owner team) inside a `BulletWorld` owned by the scene. One system updates all bullets in the `fixed` phase and one draw pass renders them from a single atlas. Collision is circle-based: enemy bullets against the player's small hitbox (plus a graze radius); player bullets against enemy hurtboxes through a uniform grid. Patterns (rings, spirals, aimed, waves) are data. Hits are reported as queued events and applied through `kit-common` damage, so gameplay code stays ECS-based. Enemies and the player remain ECS entities.
- Alternatives: (1) Bullets as ECS entities with F-006 collision — fine up to a few hundred; kept as the approach for ordinary shooter projectiles if preferred. (2) Box2D bullets — far too heavy.
- Consequences: A second, specialised update path. It is isolated in one kit and talks to the rest through events and `kit-common`.
- Risks: Two ways to make a projectile. Mitigation: the top-down shooter package uses `kit-bullets` for all projectiles; ECS projectiles are not provided by any kit.
- Impacted specifications: F-016, F-021, F-024.
