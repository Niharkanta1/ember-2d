# ADR-022: `ember.app` is the composition root

- Status: Accepted
- Date: 2026-11-26
- Context: F-001 and F-019 placed `EmberGame` and `EngineContext` in `ember.core`. The context exposes the engine services (`events()`, `assets()`, `input()`, `renderer()`, `scenes()`, ...) and `EmberGame` creates them, so `ember.core` would depend on almost every engine package. That contradicts dependency rule 1 (`ember.core` depends on no other engine package), which keeps the base package usable by everything. The conflict surfaced in I-030, when the first higher-level service (`EventBus` in `ember.event`) was added to the context.
- Decision:
  - A new top-level package `ember.app` (`dev.ember2d.app`) is the composition root. It holds `EmberGame`, `EngineConfig`, `EngineConfigException`, `EngineContext`, `AppState` and `AppLifecycleListener`, moved from `ember.core` in I-030.
  - `ember.app` may depend on every package in `ember-core`. No other engine package depends on `ember.app`.
  - `ember.core` keeps what every package may use: logging, `Time`, `Phase`, `Timers`, `Tweens`, `StateMachine`, `@PublicApi` and the internal game loop. Rule 1 is unchanged.
  - Engine services that the context exposes keep living in their own packages; `EmberGame` creates and drives them.
- Alternatives:
  1. Put `EventBus` in `ember.core` — rejected: fixes events only; assets, input and rendering hit the same rule later.
  2. Relax rule 1 so `ember.core` may depend on service packages — rejected: `ember.core` would depend on everything and the layering would be meaningless.
  3. Service interfaces in `ember.core`, implementations elsewhere — rejected: `EmberGame` must still construct the implementations, which needs a composition root anyway.
- Consequences: Game code imports `dev.ember2d.app.EmberGame`, `EngineConfig` and `EngineContext`. The architecture tests gain a top layer (`app`). The move happens before any phase gate made these types stable API, so no deprecation path is needed.
- Risks / open point for I-070: scenes receive the context, and the context owns the scene manager, so `ember.scene` and `ember.app` would depend on each other (rule 12 forbids cycles). I-070 decides between placing `Scene`/`SceneManager` in `ember.app` or keeping `ember.scene` free of the context type. Until then `ember.scene` is not allowed to depend on `ember.app`.
- Impacted specifications: F-001, F-019, F-010 (I-070), `ARCHITECTURE.md`, the architecture rules (`EmberArchitectureRules`), the guide.
