# Test Plan

## Test layers
1. **Unit tests:** pure logic — timing, ECS, collision, input state, serialization, migrations, kit mechanics.
2. **Architecture tests:** ArchUnit rules from `ARCHITECTURE.md` (package dependencies, no hidden types in public API, no genre words in the engine).
3. **Integration tests:** libGDX headless backend for asset loading and Tiled parsing; adapter tests for Box2D, ImGui and blade-ink.
4. **Demo checks:** each demo has a scripted smoke run (launch, load first scene, run N frames, exit cleanly) on desktop and on an Android emulator (minimum API level), plus the manual checklist below.
5. **Performance checks:** baseline scenes with recorded metrics per phase on the reference desktop and a mid-range Android phone; no unsupported claims.

## Baseline quality gate (every task)
- Gradle build succeeds, including the Android debug build (from Phase 1).
- Unit and architecture tests pass.
- No dependency cycles; no unregistered third-party dependency.
- Public API does not expose Box2D, Spine, backend or file-path types.
- No Android-unsafe Java APIs in `ember-core` or kits (architecture test).
- Resource disposal tests where applicable.
- Demo smoke run passes.
- No unrelated diffs.

## Acceptance checklist by subsystem

### Core and time (F-001, F-002)
- App lifecycle order defined and tested; app suspend is distinct from game pause.
- Repeated disposal is safe; invalid transitions are rejected.
- Fixed-step tick counts correct for given frame deltas; catch-up bounded; large deltas clamped.
- Interpolation alpha in [0, 1) and correct for given accumulator values.
- Time scale and pause affect the documented phases only.
- Timers and tweens fire at the right tick, respect pause, and can be cancelled.

### Events (F-019)
- Immediate events delivered synchronously; queued events delivered at the defined flush point.
- Subscriptions removed on scene exit.
- Events published during delivery are handled per the documented rule.

### ECS (F-003)
- Entity and component lifecycle; generation counters invalidate stale refs.
- Structural changes during iteration are queued and applied between systems.
- System order follows phases and registration order.
- Family queries return correct entities.
- No entity leaks between scene worlds.
- Benchmark sample exists before optimisation work.

### Rendering and camera (F-004)
- Layer and sort order (`z`, `ySort`) verified with a reference scene.
- Pixel-perfect: integer scale and letterbox correct for sample window sizes; camera snapped.
- Resize behaviour; resources disposed.
- Camera follow, dead-zone, bounds and shake behave as specified.

### Input (F-005)
- Pressed, just-pressed, just-released and held semantics across frames and fixed ticks.
- Binding validation; context precedence (UI over gameplay).
- Touch actions (tap, long press, swipe, drag) map to actions; multi-touch pointers tracked independently.
- Gamepad connect/disconnect (Phase 6 only).

### Android platform (F-022)
- Suspend/resume keeps game pause state; accumulator reset; audio paused and resumed.
- Graphics context loss: frame buffer and non-managed GL objects recreated; scene renders correctly after resume.
- Back button triggers the `back` action.
- Safe-area insets respected on a notched emulator profile; letterbox and fill modes correct on 4:3 to 21:9.
- On-screen joystick and buttons drive actions; layout saved in settings; hidden when a keyboard or gamepad is used.
- R8-minified release build loads prefabs, saves and Ink stories correctly.
- No `ember-devtools` classes in Android builds.

### Collision (F-006)
- `moveAndCollide` stops exactly at contact; `moveAndSlide` slides along walls.
- No snagging on tile seams.
- One-way solids: pass from below, land from above, drop-through when requested.
- Moving solids carry riders and push actors.
- Trigger enter/stay/exit events.
- Layer/mask filtering; overlap, point and ray queries.
- Unit conversion uses the project `pixelsPerUnit`.

### Animation (F-007)
- Loop, one-shot, ping-pong; frame timing; events on the right frame.
- Aseprite JSON import maps tags to animations.
- State machine: priorities, `*` transitions, triggers reset after use, `minTime`, `exitTime`, `exitOnFinish`.
- Invalid state machine definitions fail with file and transition index; hot reload applies.

### Assets (F-008)
- Load by ID; unknown ID fails with a clear message.
- Missing optional asset uses fallback and logs a warning.
- Group load/unload per scene; reference counts released.
- Hot reload replaces a changed asset without restarting (dev builds).

### Tilemap (F-009)
- Tiled TMX and JSON samples load; layer order correct.
- Tile-to-world coordinate mapping.
- Collision tiles become solids; one-way property honoured.
- Object class resolves to a prefab; `Component.field` overrides applied; wrong type reported.
- Object templates resolved; object-reference properties become valid `EntityRef`s; polylines become paths.
- Empty and malformed map handling.
- Generated Tiled custom types load in Tiled.

### Scenes and prefabs (F-010)
- Scene enter/exit, stack push/pop, transitions.
- Per-scene assets loaded before enter and released after exit.
- Prefab defaults plus Tiled property overrides; unknown property reported.
- Version migration of an older prefab file.

### Audio, UI (F-011, F-012)
- Volume settings applied per category; music crossfade.
- UI receives input before gameplay; HUD and menus render on the UI layer.

### Save and settings (F-013)
- Save round trip; corrupt file handling; version validation and migration.
- Atomic write: an interrupted save leaves the previous save intact.
- Settings persist separately from save slots.
- World flags and global data (unlocks, read-text tracking) round trip.
- Android suspend auto-save hook writes a valid save.

### Dev tools (F-018)
- Dev tools absent from release builds (dependency check).
- Inspector edits a component field live.

### Kits (F-015, F-016, F-017, F-021, F-023, F-024)
- Platformer: grounded detection, jump, variable height, buffer, coyote, optional mechanics toggled by flags; animation parameters written to the state machine.
- Shooter: fire modes, fire rate, reload/ammo, hits through `kit-bullets`; twin-stick touch aiming.
- Bullets: each emitter type produces expected positions at fixed step; hitbox and graze edges; bullet clear; nested emitters; 3,000 bullets at 60 fps on a mid-range Android phone with no per-bullet allocation.
- RPG: interaction range and selection, inventory add/remove/stack, dialogue flow and state save.
- Visual novel: every staging tag; save mid-scene and load restores background, characters, music and story position; skip stops at unread text; backlog.
- Common: damage, invulnerability window, death event, trigger zones, pool reuse, `DialogueRunner` state round trip.

## Manual smoke test (per demo, on desktop and on an Android phone)
1. Launch the packaged build (Construo zip / installed APK or AAB).
2. Resize and toggle fullscreen (desktop) or rotate and check notch/safe area (Android); check pixel-perfect scaling.
3. Move the player using action bindings (keyboard on desktop; on-screen controls on Android; gamepad from Phase 6).
4. Verify animations.
5. Collide with walls, one-way platforms and triggers.
6. Pause/resume via menu; alt-tab or Android home button (app suspend) does not unpause the game; returning after several minutes in the background still renders correctly.
7. Switch scenes.
8. Edit the level in Tiled while running (dev build) and see it reload.
9. Play sound and music; change volumes in settings.
10. Save, quit, relaunch, load.
11. Exit and verify clean disposal (no errors in log).
