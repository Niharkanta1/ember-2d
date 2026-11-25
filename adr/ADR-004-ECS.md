# ADR-004: Custom lightweight ECS with object-based components

- Status: Accepted (amends D-004)
- Date: 2026-11-25
- Context: The established libGDX ECS libraries (Ashley, artemis-odb) have seen little development in recent years. Ember2D needs entities that serialize cleanly to JSON prefabs, are easy to inspect in dev tools, and are safe to reference across frames.
- Decision: Write a small ECS in `ember.ecs`: `int` entity handles with generation counters, `EntityRef` for stored references, plain-object components in one store per type, systems with families and phases (`input`, `fixed`, `update`, `late`, `render`), and queued structural changes. No archetypes, bitsets or code generation until a benchmark shows the need.
- Alternatives: (1) Ashley — simple and known, but in maintenance mode; kept as fallback if the custom ECS stalls. (2) artemis-odb — fast but heavier, with optional bytecode weaving. (3) Classic GameObject + components — easier at first, but blurs data and behaviour and complicates systems ordering.
- Consequences: Full control over serialization and inspection. Performance target is indie scale (hundreds to a few thousand entities).
- Risks: Scope creep into a "real" ECS. Mitigation: F-003 caps features; optimisation requires a benchmark.
- Impacted specifications: F-003, F-010, F-018.
