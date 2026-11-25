# Repository Structure

10 library modules plus 4 demos (ADR-003); `ember-spine` is added only if adopted. Engine subsystems are packages inside `ember-core`; boundaries are enforced by ArchUnit tests.

```text
ember2d/
├── ember-core/                  # all engine packages (see ARCHITECTURE.md); Android-safe Java only
│   └── src/main/java/ember/
│       ├── core/  event/  ecs/  assets/  input/
│       ├── render/  camera/  animation/  collision/  tilemap/
│       ├── scene/  data/  audio/  ui/  save/  debug/  particles/
├── ember-desktop/               # LWJGL3 launcher, Construo packaging config
├── ember-android/               # Android launcher, lifecycle, context loss, safe areas, R8 rules
├── ember-devtools/              # ImGui dev panels, hot reload driver (desktop dev builds only)
├── ember-box2d/                 # optional rigid-body module (Phase 6)
├── kits/
│   ├── kit-common/              # shared: health, damage, triggers, pools, spawners, DialogueRunner (Ink)
│   ├── kit-bullets/             # shared: data-oriented bullets and patterns
│   ├── kit-platformer/          # genre
│   ├── kit-topdown/             # genre: movement + packages shooter, rpg
│   └── kit-novel/               # genre: visual novel staging and reader features
├── demos/                       # each demo doubles as a project template
│   ├── demo-platformer/
│   │   ├── core/                # game code and assets (shared by both launchers)
│   │   ├── desktop/             # desktop launcher
│   │   └── android/             # Android launcher, manifest, icons
│   ├── demo-shooter/            # same core/desktop/android layout; includes a bullet-hell boss stage
│   ├── demo-rpg/
│   └── demo-novel/
├── tools/
│   └── tiled-types/             # Gradle task: generate Tiled custom types from prefabs (F-009)
├── assets-src/                  # source art: Aseprite files, Tiled projects and templates, Ink sources
├── adr/                         # architecture decision records
├── specs/                       # feature specifications
├── templates/                   # spec, task, spike and ADR templates
├── images/                      # logo and architecture diagram
├── old-files/                   # pre-revision docs, reference only
├── gradle/
├── settings.gradle
├── build.gradle
├── gradle.properties
└── README.md
```

## Notes
- **Demo assets** live in each demo's `core/assets/` folder (shared by desktop and Android), produced from `assets-src/` (atlas packing via a Gradle task, Ink compiled to JSON).
- **Tiled project:** each demo has a `.tiled-project` file in `assets-src/` with object templates (`.tx`) per prefab and generated custom types.
- **Templates:** there is no separate game-template tree. A new game starts by copying a demo. `templates/` holds documentation templates only.
- **Package base name** `ember` is a placeholder; choose the final base package in I-000.
- **Module creation rule:** a package becomes its own module only when it gains a second consumer with different dependencies, or needs a backend/native/licensed dependency other code must not see.
- **Tests:** `src/test/java` mirroring production packages; architecture tests in `ember-core`. Android instrumentation is limited to the demo smoke test.
- **Not present:** `ember-ios` (iOS is not a target; ADR-019), `ember-spine` until adopted (ADR-021).
