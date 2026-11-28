# Feature Specifications

Specifications define feature scope, behaviour, API boundaries and acceptance criteria. Each feature is implemented as small tasks against its spec. See `../SPEC_DRIVEN_WORKFLOW.md` for spec status rules: **Draft** specs define scope and must be expanded to template depth before implementation; **Ready** specs are authoritative.

| ID | Spec | Phase | Package / module | Status |
| --- | --- | --- | --- | --- |
| F-020 | Packaging and release builds (desktop, Android) | 0, 1, 3 | `ember-desktop`, `ember-android` | Desktop Ready; Android debug build done; Android release Draft |
| F-001 | Engine core and app lifecycle | 1 | `ember.app` | Ready |
| F-002 | Time, loop, timers, tweens | 1 | `ember.core` | Ready |
| F-003 | Entity component system | 1 | `ember.ecs` | Ready |
| F-019 | Events and EngineContext | 1 | `ember.event`, `ember.app` | Ready |
| F-004 | Rendering, camera, text | 1, 2, 4 | `ember.render`, `ember.camera` | Part 1 Ready; text, look-ahead, parallax, UI, safe area Draft |
| F-008 | Assets and hot reload | 1, 2, 3 | `ember.assets` | Part 1 Ready; hot reload and preloading Draft |
| F-010 | Scenes, data, prefabs, component registry | 1, 2, 3 | `ember.app`, `ember.scene`, `ember.data` | Scene lifecycle Ready; rest Draft |
| F-014 | Debug overlay and particles | 1, 6 | `ember.debug`, `ember.particles` | Overlay (Phase 1) and particles (I-510) Ready; rest Draft |
| F-022 | Android platform and touch input | 1, 2, 3 | `ember-android`, `ember.input` | Launcher Ready; rest Draft |
| F-005 | Input actions | 2, 3, 6 | `ember.input` | Ready (I-100, I-530); rebinding UI Draft |
| F-006 | Physics: kinematic collision | 2 | `ember.collision` | Draft |
| F-007 | Animation and animation state machine | 2, 6 | `ember.animation` | Draft |
| F-009 | Tilemaps and entity placement (Tiled) | 2, 3 | `ember.tilemap` | Draft |
| F-018 | Editor strategy: in-game dev tools | 2 | `ember-devtools` | Draft |
| F-011 | Audio | 3 | `ember.audio` | Draft |
| F-012 | Game UI (Scene2D) | 3 | `ember.ui` | Draft |
| F-013 | Save slots, world flags, settings | 3, 5 | `ember.save` | Draft |
| F-015 | Platformer kit | 2, 3 | `kit-platformer` | Draft |
| F-021 | Common gameplay kit (incl. dialogue runner) | 4, 5 | `kit-common` | Draft |
| F-024 | Bullet kit (projectiles, bullet hell) | 4 | `kit-bullets` | Ready |
| F-025 | Localisation (string tables, language setting, translated Ink stories) | 5 | `ember-core` / `ember.i18n` | Ready |
| F-016 | Top-down movement and shooter | 4 | `kit-topdown` | Draft |
| F-017 | RPG package | 5 | `kit-topdown` | Draft |
| F-023 | Visual novel kit | 5 | `kit-novel` | Ready (I-460, I-470) |
| F-006B | Optional Box2D module | 6 | `ember-box2d` | Ready (I-500) |
| F-026 | Sprite materials: hit flash, outline, palette swap | 6 | `ember.render`, `kit-common` | Ready (I-520) |
| F-027 | Project generator (`newGame`) | 6 | `tools/new-game` | Ready (I-580) |
| F-028 | Asset tools: manifest and clip generation | 6 | `tools/asset-tools` | Ready (I-590) |

Not yet written (Phase 6 candidates): custom shaders and post-processing (beyond F-026), gamepad details (in F-005), Spine adapter details (in F-007, ADR-021).

File names are kept from revision 1 for continuity: `F-006-PHYSICS.md` now specifies kinematic collision, `F-014-PARTICLES-DEBUG.md` covers the debug overlay and the particle wrapper, and `F-018-EDITOR.md` specifies in-game dev tools instead of a JavaFX editor. Revision 1 versions are in `../old-files/specs/`.
