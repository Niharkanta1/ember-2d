# F-006B — Optional Box2D Module

- Status: Draft
- Phase: 6 (only when a demo or game needs rigid bodies)
- Module / package: `ember-box2d`
- Depends on: F-003, F-019, F-002
- Tasks: I-500
- Decision: ADR-005

## Goal
Let games that want rigid-body physics (crates, ragdoll props, physics puzzles) use Box2D inside Ember2D's ECS and fixed step, without affecting games that don't.

## Scope
- Wrap the classic libGDX Box2D extension (v2). Reconsider the Box2D v3 bindings (`gdx-box2d`) when they leave incubation.
- `RigidBody` component (type, fixtures, filter, sensor) created/destroyed with the entity.
- Box2D world stepped in the `fixed` phase; transforms synced to `Transform` (with interpolation support).
- Contacts published as the same event types as F-006 where meaningful.
- Unit conversion through `pixelsPerUnit`.
- Debug draw through the debug hook.
- Box2D types appear only inside `ember-box2d` (ADR-013).

## Non-goals
- A shared abstraction over Box2D and kinematic collision; the two APIs stay separate.
- Mixing kinematic actors and Box2D bodies in the same collision queries (document how to bridge with sensors if needed).

## Acceptance criteria
- Body lifecycle follows entity lifecycle; no leaked bodies after scene exit.
- Contacts and sensors produce events; filters work.
- Demo scene with stacked crates is stable at 60 Hz fixed step.
- `ember-core` builds and runs without this module on the classpath.
