# ADR-009: Tiled and in-game dev tools instead of a JavaFX editor

- Status: Accepted (replaces proposed D-009 "JavaFX editor shell with LibGDX viewport")
- Date: 2026-11-25
- Context: The original Phase 9 editor (project management, asset browser, hierarchy, inspector, scene view, tilemap editor, undo/redo, play mode) is roughly a year of solo work. Embedding an LWJGL3/libGDX viewport in JavaFX is unreliable across platforms, a risk the original D-009 already acknowledged.
- Decision: Use Tiled as the level and entity-placement editor. Build developer tools inside the running game with Dear ImGui (imgui-java, documented on the libGDX wiki) in the dev-only `ember-devtools` module: entity list, component inspector with live edit, collider and ray view, event log, frame metrics and hot reload status. Revisit a standalone editor only after the demos ship.
- Alternatives: (1) JavaFX editor — rejected, cost and embedding risk. (2) Scene2D/VisUI editor inside the game — viable, but ImGui is faster to build tool UIs with. (3) Separate preview window/process — still requires building a full editor.
- Consequences: No editor-specific data model or modules. Content iteration relies on Tiled + hot reload. Dev tools need the LWJGL3 backend, so they live outside `ember-core` and are desktop-only; Android builds rely on the F-014 debug overlay.
- Risks: imgui-java integration needs the GLFW window handle. Mitigation: spike S-01 in Phase 1.
- Impacted specifications: F-018 (now dev tools), F-008 (hot reload), F-009.

## Review (I-550, 2026-11-28): confirmed, no standalone editor
- **Decision:** keep this ADR. The developer agreed to "no editor" once the everyday jobs an editor would do have a documented way that needs no hand-written files.
- **Developer's questions and their answers:** documented in the wiki page `Working-Without-an-Editor`.

  | Question | Answer |
  | --- | --- |
  | Projects | `newGame` (F-027); games live under `demos/`; one Ember2D copy for several games, or a fork per game |
  | Prefabs | the dev tools' "Copy as prefab JSON", Tiled per-object overrides with generated `tiledTypes` (now also in games made from the blank template) |
  | Custom enemies | a component and a system in code, plus a prefab |
  | Manifest | new `assetManifest` task (F-028) |
  | Clips | new `makeClips` task from sprite strips or Aseprite exports (F-028) |
  | Cameras | `CameraFollow` target switching, tweening the `CameraRig`, scene transitions |
  | Particles | GDX Particle Editor |
  | Other jobs | Tiled, Aseprite, a texture packer, Skin Composer, Ink |

- **Known gaps** (not solved by tools; candidates for later work):
  - several world cameras at once (split screen, picture-in-picture);
  - hand-written animation state machines, bullet patterns and input bindings;
  - games can only live inside this repository until Ember2D is published as a library.
- **Revisit** when a real game hits one of these gaps often enough to justify engine or tool work.
