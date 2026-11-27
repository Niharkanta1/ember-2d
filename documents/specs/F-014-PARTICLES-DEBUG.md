# F-014 — Debug Overlay and Particles

- Status: Part A Phase 1 Ready (metrics panel, debug shapes, toggle: expanded and approved in I-080, 2026-11-26); collider/trigger/ray and camera views (Phase 2): Draft; Part B particles Ready (I-510, approved 2026-11-27; see "Design (I-510)")
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

### Design (I-510)
Status: Ready. Approved 2026-11-27 under the developer's standing approval for Phases 3 to 6. Package `dev.ember2d.particles`.

**Assets:** a new manifest type `particle` loads a libGDX particle effect file (`.p`, the particle editor's format). Its images are looked up next to the file, as the libGDX loader does by default. A missing effect gives an empty effect and one warning, like other missing assets.

**API sketch:**
```java
@PublicApi public final class Particles implements LayerContent {
    public Particles(Assets assets, float pixelsPerUnit, String layer);      // draws on that render layer
    public Effect spawn(String effectId, float x, float y);                  // one-shot, pooled, freed when complete
    public void update(float delta);  public int active();  public void clear();   // clear(): frees all (scene exit)
    public void drawLayer(String layer, Batch batch);
    public static final class Effect { position(x, y); stop(); isComplete(); free(); }   // a handle; invalid once freed
}
@PublicApi public final class ParticleSource {                               // component
    public String effect = "";  public float offsetX, offsetY;  public boolean despawnWhenDone;
}
@PublicApi public final class ParticleSystem extends EntitySystem {          // LATE, after movement
    public ParticleSystem(World world, Particles particles);
}
```
- **Units:** effects are authored in pixels; `Particles` scales each effect's prototype once by 1 / pixels-per-unit, so effects are placed in world units.
- **Pools:** one libGDX `ParticleEffectPool` per effect ID, created on first use. A completed effect is freed back to its pool during `update`, so a steady stream of hits allocates nothing after warm-up.
- **Drawing:** `drawLayer` draws the active effects when the layer is the configured one. The game attaches it with `spriteRenderSystem.addLayerContent(particles)`.
- **`ParticleSource`:** the system starts the effect for each entity with a `ParticleSource` and a `Transform`, keeps it at the entity's position plus the offset, and frees it when the entity is removed. When the effect completes it is freed; with `despawnWhenDone` the entity is despawned too (a one-shot effect placed as an entity).
- **The scene:** `clear()` frees every active effect; scenes call it in `exit()`. The component is registered with the engine's components.
- **Layering:** `ember.particles` may use `ember.render` (for `LayerContent`), like `ember.animation`. ARCHITECTURE.md and the ArchUnit rule list the exception.
- **The component's name:** `ParticleSource`, not `ParticleEmitter`, because libGDX's `ParticleEmitter` (an emitter inside an effect) would clash in every file that uses both.

**Demo:** the shooter demo shows a hit effect where bullets hit enemies and dust under the player while running. Its effect files are made by `assets-src/demo-shooter/fx/MakeEffects.java`, which builds the emitters with libGDX's API and writes them with libGDX's own `save`, because the particle editor is not installed here.

**Tests (headless, with a recording batch):**
- Spawning, updating until complete, and freeing back to the pool; the pooled effect is reused.
- No allocation over many spawn-and-complete cycles after warm-up (allocation counter, as the bullet kit's test does).
- `clear()` frees everything; drawing goes only to the configured layer.
- The system follows the entity, frees on removal, and despawns when done if asked.
- The demo's `.p` files load and every emitter is visible (replaces a unit test of the manifest type).

### Decisions in I-510
- **Effect handles are pooled too:** `spawn` returns an `Effect` handle that is reused after the effect is freed. `generation()` tells a caller whether a handle it kept is still its own; `ParticleSystem` uses it.
- **Transparency of hand-built emitters (found on the emulator):** a `ParticleEmitter` made in code starts with a transparency "high" value of 0, so its particles are invisible. The particle editor sets 1 for new emitters. The effect generator sets it, and the shooter's data test checks every emitter.
- **The manifest type** is checked through the demo's data test, which parses the `.p` files, rather than a headless load through `AssetManager` (that needs the GL stubs to load the images as textures).
- **Checked on the emulator** (API 23, debug build):
  - Dust puffs behind the running player.
  - A log showed the effects spawning and being freed (two to three active while running, none when standing).
  - A spark glow at the boss when hit, faint over the pink boss; the sparks were made larger afterwards (not checked again on the device).
- **Not checked:** a real phone, a release (R8) build, hot reload of a `.p` file.
