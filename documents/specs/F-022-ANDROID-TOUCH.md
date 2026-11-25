# F-022 — Android Platform and Touch Input

- Status: Draft
- Phase: 1 (Android launcher, smoke build), 2 (touch actions), 3 (on-screen controls, Android demo build)
- Module / package: `ember-android`; `ember-core` / `ember.input`, `ember.render`, `ember.ui`
- Depends on: F-001, F-004, F-005, F-012, F-013, F-020
- Tasks: S-04, I-015, I-105, I-225, I-255
- Decisions: ADR-019, ADR-015

## Goal
Every Ember2D game can ship on Android with the same game code: touch controls, correct lifecycle and screen handling, and a reliable build.

## Scope
**Platform (`ember-android`)**
- Android launcher (`AndroidApplication`) configured from `EngineConfig`; immersive full-screen; orientation per game (landscape default, portrait for novels).
- App lifecycle: Android `onPause`/`onResume` map to F-001 `suspend`/`resume` (game pause state unchanged, accumulator reset, audio paused).
- **Graphics context loss:** on resume after context loss, recreate the pixel-perfect `FrameBuffer`, shaders and any GL objects not managed by `AssetManager`; managed textures reload automatically. A `ContextRestored` event is published.
- Back button: mapped to an input action (`back`), default handler opens the pause menu or pops the scene.
- Storage: saves and settings use libGDX local storage (app-private).
- Build: APK for testing, AAB for release (F-020); R8 enabled with keep rules generated from the component registry; minimum API level chosen in I-015.

**Screen handling (`ember.render`, `ember.ui`)**
- Safe-area insets (notches, cut-outs, gesture bars) exposed through `Renderer.safeArea()`; UI layouts respect it.
- Pixel-perfect mode with aspect ratios from 4:3 to 21:9: integer scaling with letterbox, or "fill" mode that extends the visible world area horizontally (per game setting).
- UI scale setting with a sensible default from screen density.

**Touch input (`ember.input`)**
- Touch actions: tap, double tap, long press, swipe (direction), drag; available as actions like keyboard input.
- Pointer helpers: touch positions in world and UI coordinates, multi-touch (up to 5 pointers).
- **On-screen controls** (`ember.ui` widgets bound to input actions): virtual joystick (fixed or floating), buttons, D-pad; opacity, size and position configurable and saved in settings; auto-hidden when a gamepad or keyboard is used.
- Haptics: short vibration helper (optional, setting-controlled).

## Non-goals
- iOS (ADR-019). Google Play services, ads, in-app purchases, cloud saves (game-level integrations, later).
- Gesture recognition beyond the list above.

## Acceptance criteria
- `demo-platformer` runs on an Android device and the minimum-API emulator; suspend/resume and context loss leave the game intact.
- On-screen joystick and jump button control the platformer; floating joystick works for the shooter.
- Safe areas respected on a notched device or emulator profile.
- Back button opens the pause menu.
- Release AAB builds with R8; JSON prefabs and saves load correctly in the minified build.
- No `ember-devtools` classes in Android builds.

## Risks/open questions
- Performance budget on low-end devices: measure `demo-shooter` bullet stage on a mid-range phone (target 60 fps, 3,000 bullets).
