# F-014 — Debug Overlay and Particles

- Status: Draft
- Phase: 1 (debug overlay), 6 (particles)
- Module / package: `ember-core` / `ember.debug`, `ember.particles`
- Depends on: F-002, F-003, F-004, F-008
- Tasks: I-080, I-510
- Decision: ADR-014 (particles use libGDX `ParticleEffect`)

## Part A — Debug overlay (Phase 1, grows each phase)
Lightweight diagnostics that work in any build, without ImGui (rich tools are F-018).
- Frame metrics: FPS, frame time (avg/max), fixed steps per frame, dropped steps, entity count, render calls.
- Collider, trigger and ray drawing (from F-006 / F-006B).
- Camera dead-zone and bounds drawing.
- Toggle with a key (default F3) in dev builds; disabled in release unless enabled in config.

Acceptance: overlay shows metrics in the Phase 1 demo; collider view in the Phase 2 demo; zero cost when disabled.

## Part B — Particles (Phase 6)
- Wrap libGDX `ParticleEffect` and `ParticleEffectPool`; effects authored with the libGDX particle editor and loaded by asset ID.
- `ParticleEmitter` component attaching an effect to an entity (follow position, auto-free when complete).
- One-shot helper: `particles.spawn("fx.hit", x, y)`.
- Drawn in a configured render layer.

Non-goals: a custom particle runtime or editor.

Acceptance: pooled effects reused without allocation; effects freed when complete or when their scene exits; demo shows hit and dust effects.
