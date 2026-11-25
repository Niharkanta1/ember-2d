# ADR-014: Prefer libGDX built-ins and wrap them thinly

- Status: Accepted (new)
- Date: 2026-11-25
- Context: Several specs re-specified systems libGDX already provides: asset registry/cache/dependencies (`AssetManager`), UI controls and layout (Scene2D.ui), particles (`ParticleEffect`), tilemaps (`TiledMap` + loaders + renderers). AI and pathfinding were missing but exist in gdx-ai.
- Decision: Use the libGDX implementation and add only what Ember2D needs on top: asset IDs and groups, a project skin and HUD helpers, a particle wrapper with pooling, Tiled-to-collision and object-to-prefab mapping, and kit-level AI hooks over gdx-ai.
- Alternatives: Custom implementations — rejected unless a demo proves the built-in cannot meet a need; that requires a new ADR.
- Consequences: Large reduction in scope; behaviour and docs come from libGDX. Wrappers stay thin so libGDX docs remain applicable.
- Risks: Built-in limitations surface late. Mitigation: each wrapper spec lists known limits.
- Impacted specifications: F-008, F-009, F-012, F-014, F-016, F-017.
