# F-014 — Debug Overlay and Particles

- Status: Part A Phase 1 Ready (metrics panel, debug shapes, toggle: expanded and approved in I-080, 2026-11-26); collider/trigger/ray and camera views (Phase 2) and Part B particles (Phase 6): Draft
- Phase: 1 (debug overlay), 6 (particles)
- Module / package: `ember-core` / `ember.debug`, `ember.particles`
- Depends on: F-002, F-003, F-004, F-008
- Tasks: I-080, I-510
- Decision: ADR-014 (particles use libGDX `ParticleEffect`)

## Part A — Debug overlay (Phase 1, grows each phase)
Lightweight diagnostics that work in any build, without ImGui (rich tools are F-018).
- Frame metrics: FPS, frame time (avg/max), fixed steps per frame, dropped steps, entity count, render calls.
- Collider, trigger and ray drawing (from F-006 / F-006B).
- Camera dead-zone and bounds drawing.
- Toggle with a key (default F3) in dev builds; disabled in release unless enabled in config.

Acceptance: overlay shows metrics in the Phase 1 demo; collider view in the Phase 2 demo; zero cost when disabled.

### Phase 1 design (I-080)
**API sketch** (`ember.debug`):
```java
@PublicApi public final class DebugOverlay {               // context.debug(); always present
    public boolean isAvailable();  public boolean isVisible();  public void setVisible(boolean visible);  public void toggle();
    public void rect(float x, float y, float width, float height, Color color);   // world units, this frame only
    public void line(float x1, float y1, float x2, float y2, Color color);
    public void circle(float x, float y, float radius, Color color);
    // engine hooks: recordFrame(...), renderWorld(DebugCanvas, Matrix4), renderPanel(DebugCanvas, int, int)
}
@PublicApi public interface DebugCanvas { ... }            // drawing backend; engine: ShapeRenderer + built-in font
// EngineConfig: debugOverlayInRelease = false, debugOverlayVisibleAtStart = false
```
- **Panel** (top-left, screen resolution, translucent background, built-in font scaled by screen density): FPS and frame time average/max, engine CPU time per frame average/max, fixed steps in the last frame, total dropped steps, entities in the current scene, batch render calls in the last frame. Averages and FPS cover one-second windows and refresh once per second.
- **Debug shapes**: rectangles, lines and circles in world units, queued during the frame and drawn over the sprites with the world camera; ignored while hidden.
- **Availability**: always in dev mode; in release only with `debugOverlayInRelease`. Toggled with F3 (read directly from libGDX until input actions, I-100); `debugOverlayVisibleAtStart` shows it from boot (the demo does this for Android dev builds).
- **Zero cost when hidden**: shape calls and metric recording return immediately; the drawing backend is created on first show.
- **Layering**: `ember.debug` may not use `ember.render`, so `EmberGame` (`ember.app`) hands it the batch, camera and screen size during the render pass. Camera dead-zone/bounds and collider views need `ember.debug` to see `camera`/`render`/`collision`; that layering decision is taken with the collider view in Phase 2.

## Part B — Particles (Phase 6)
- Wrap libGDX `ParticleEffect` and `ParticleEffectPool`; effects authored with the libGDX particle editor and loaded by asset ID.
- `ParticleEmitter` component attaching an effect to an entity (follow position, auto-free when complete).
- One-shot helper: `particles.spawn("fx.hit", x, y)`.
- Drawn in a configured render layer.

Non-goals: a custom particle runtime or editor.

Acceptance: pooled effects reused without allocation; effects freed when complete or when their scene exits; demo shows hit and dust effects.
