# ADR-016: Tiled as the level editor and level format

- Status: Accepted (new; amends D-006)
- Date: 2026-11-25
- Context: The plan defined a custom JSON tilemap format and a tilemap editor inside a future JavaFX editor. Tiled is a mature, free level editor; libGDX loads TMX and (since 1.13.1) Tiled JSON maps and renders them.
- Decision: Levels are Tiled maps. Tile properties or a collision layer define solids and one-way tiles. Object layers place entities: an object's type/class names a prefab and its custom properties override prefab fields. A scene is a Tiled map plus an optional JSON sidecar. Ember2D-specific JSON (prefabs, sidecars, saves, settings) keeps explicit `version` fields and migrations.
- Alternatives: (1) Custom format + own editor — rejected (ADR-009). (2) LDtk — strong editor, but Java/libGDX loaders are small third-party projects; can be added later as a second loader behind `ember.tilemap`.
- Consequences: Level editing, auto-tiling and object placement are available from Phase 2 with no tool work. Map format stability is owned by Tiled.
- Risks: Tiled format changes between versions. Mitigation: pin a Tiled version for demos; loader tests use committed sample maps.
- Impacted specifications: F-009, F-010.
