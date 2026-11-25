# F-018 — Editor Strategy: In-game Dev Tools

- Status: Draft
- Phase: 2 (after spike S-01 in Phase 1)
- Module / package: `ember-devtools` (dev builds only)
- Depends on: F-003, F-008 (hot reload), F-014 (overlay), F-019; imgui-java; LWJGL3 backend
- Tasks: S-01, I-150, I-160
- Decision: ADR-009 (replaces the original JavaFX editor MVP)

## Goal
Fast content iteration without a standalone editor: Tiled for levels, plus in-game tools to inspect and tweak the running game.

## Scope
- Dear ImGui via imgui-java rendered on top of the game in dev builds; toggled with a key (default F1). Input goes to ImGui when it wants focus, otherwise to the game.
- **Panels:** entity list (filter by name/tag/component), component inspector with live editing of public fields (via the component registry), animation state machine view (current state, parameters, last transition), collider/trigger view toggle, bullet count and pattern view, event log (last N events by type), frame metrics graph, asset list with reload button, hot reload status, time controls (pause, step one fixed tick, time scale).
- Desktop only: ImGui needs the LWJGL3 window. Android builds use the F-014 debug overlay instead.
- **Hot reload driver:** file watcher feeding F-008.
- "Copy as prefab JSON" for a selected entity.

## Non-goals
- Standalone editor application, project management, undo/redo, in-editor tilemap painting, play/edit mode switching. Revisit after the demos ship (I-550).

## Acceptance criteria
- Release builds and all Android builds contain no `ember-devtools` classes (dependency check in CI).
- Editing a component field in the inspector changes game behaviour immediately.
- Step-one-tick works while paused.
- Hot reload status shows the last reloaded asset and any error.
