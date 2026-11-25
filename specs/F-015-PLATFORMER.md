# F-015 — Platformer Kit

- Status: Draft
- Phase: 2 (inside `demo-platformer`), 3 (extracted to `kit-platformer`)
- Module / package: `kit-platformer`
- Depends on: F-003, F-005, F-006, F-007 (animation state machine), F-009 (Tiled placement), F-002 (timers), F-021 (optional), F-022 (touch)
- Tasks: I-170, I-200

## Goal
Responsive, configurable platformer movement built on kinematic collision, usable by registering components and systems.

## Scope
**Core (Phase 2):** horizontal acceleration/deceleration (ground and air), max speed, gravity, max fall speed, jump with variable height (cut on release), jump buffering, coyote time, grounded detection via `Collision.isTouching(DOWN)`, one-way platform drop-through.
**Optional (Phase 3, each behind a flag):** double/multi jump, dash, wall slide, wall jump, ladders, moving-platform momentum inheritance.
- `PlatformerBody` component holds tuning values (data-driven, hot-reloadable).
- The kit writes animation parameters (`grounded`, `speedX`, `velY`, `wallSliding`, `dashing`) to the entity's `AnimationStateMachine` (F-007) each update; clip choice lives in the ASM JSON, not in kit code. The kit emits events (`Jumped`, `Landed`, `Dashed`) for sound/particles.
- Input via action names configured on the component (`move`, `jump`, `dash`); works with keyboard, gamepad and on-screen touch controls (F-022).
- Enemies, hazards, pickups and moving platforms are placed in Tiled as prefab objects (F-009).

## Non-goals
- Slopes (until collision supports them), swimming, combat (use `kit-common`).

## Acceptance criteria
- Grounded/airborne transitions correct at tile edges.
- Jump buffer and coyote time windows honoured to the tick.
- Variable jump height: releasing early yields a lower apex.
- Each optional mechanic can be toggled without affecting others.
- Demo level validates feel at 60 Hz and 144 Hz, and with on-screen controls on an Android phone.
- Kit contains no code the demo needed engine changes for.
