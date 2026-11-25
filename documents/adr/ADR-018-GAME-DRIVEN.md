# ADR-018: Game-driven development

- Status: Accepted (new)
- Date: 2026-11-25
- Context: The original roadmap built seven phases of engine features before the first genre demo (Phase 8). Engines built without a game tend to grow features nobody uses and miss the ones games need.
- Decision: A small platformer starts in Phase 2 as soon as sprites, input and collision exist. Every engine feature after Phase 1 is pulled in by a demo that needs it. Kit code is first written inside the demo, then extracted into the kit once it works. Each phase gate requires a playable or shippable build.
- Alternatives: Engine-first roadmap — rejected (risk of never shipping a game).
- Consequences: Engine APIs are validated by real use early. Some code moves from demo to kit, which is planned work, not refactoring debt.
- Risks: Demo-specific shortcuts leaking into the engine. Mitigation: genre decoupling rules in `ARCHITECTURE.md`; ArchUnit rule against genre words in engine packages.
- Impacted specifications: `ROADMAP.md`, `IMPLEMENTATION_PLAN.md`.
