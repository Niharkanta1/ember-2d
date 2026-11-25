# F-004 — Rendering, Camera and Text

- Status: Part 1 Ready (sprites, layers and sorting, pixel-perfect pipeline, camera follow/dead-zone/bounds/zoom/shake, resize, context loss: expanded and approved in I-060, 2026-11-26); text (Phase 2), look-ahead, scrolling and parallax (Phases 2/4), UI layer (I-210), safe area (I-225), debug draw hook (I-080): Draft
- Phase: 1 (sprites, layers, camera, pixel-perfect), 2 (text), 4 (Y-sort)
- Module / package: `ember-core` / `ember.render`, `ember.camera`
- Depends on: F-002, F-003, F-008
- Tasks: I-060, I-140, I-310
- Decisions: D-010 (in `TECHNICAL_DECISIONS.md`), ADR-013

## Goal
Draw sprites, tilemaps and text crisply at any window or phone screen size, with predictable layer order and a camera suited to every supported genre.

## Scope
- `Sprite` component: `TextureRegion` (by asset ID), tint, alpha, flip, origin, layer, z.
- Render layers (ordered, named in project data) with per-layer sort mode: `none`, `z`, `ySort`.
- Render systems interpolate transforms with `time.alpha()`.
- **Pixel-perfect pipeline:** world rendered into a `FrameBuffer` at `virtualWidth × virtualHeight`, then drawn to the window at the largest integer scale that fits, letterboxed; camera snapped to whole virtual pixels; `Nearest` filtering. A non-pixel-perfect mode (`FitViewport`, linear filtering) is also supported.
- UI layer drawn after the world, at window or virtual resolution (project setting).
- Camera: follow target, dead-zone, look-ahead (direction or aim vector), bounds clamp, zoom, shake (trauma-based).
- Text: `BitmapFont` by asset ID; optional FreeType generation at load time; text component and immediate draw helper.
- Debug drawing hook for colliders and rays (used by F-014 and F-018).
- **Android (ADR-019, F-022):** the `FrameBuffer` and any non-managed GL objects are recreated after graphics context loss; `Renderer.safeArea()` exposes notch/cut-out insets; aspect handling offers integer-scale letterbox or "fill" (extend the visible world on wide screens).
- Scrolling camera mode (constant scroll speed with player clamped to the view) for shoot-'em-up stages (F-024), and parallax layers.

## Non-goals
- Lighting, post-processing chains, normal maps (Phase 6 or later).
- Custom batching beyond `SpriteBatch`.
- Room-based camera transitions (add when a demo needs them).

## User-facing behaviour
- Games add a `Sprite` (region by asset ID) and a `Transform` to an entity; `SpriteRenderSystem` draws it at the interpolated transform on its layer.
- The world is drawn at the game's virtual resolution and scaled to the window by whole numbers, letterboxed (`PIXEL_PERFECT`), or extended sideways on wide screens (`PIXEL_PERFECT_FILL`); `SMOOTH` fits the window with linear filtering instead.
- The camera follows a target through a dead zone, stays inside bounds, zooms and shakes (trauma).

## API sketch
```java
// ember.render
@PublicApi public final class Sprite { public String regionId; public TextureRegion region; public final Color tint = new Color(Color.WHITE);
    public boolean flipX, flipY; public String layer = "world"; public float z; public final Vector2 origin = new Vector2(); }
@PublicApi public enum SortMode { NONE, Z, Y_SORT }
@PublicApi public record RenderLayer(String name, SortMode sort) {}
@PublicApi public enum RenderMode { PIXEL_PERFECT, PIXEL_PERFECT_FILL, SMOOTH }
@PublicApi public interface Renderer {                       // context.renderer()
    Batch batch(); OrthographicCamera worldCamera(); Viewport viewport(); CameraRig camera();
    void setLayers(RenderLayer... layers); List<RenderLayer> layers();   // default: one "world" layer, SortMode.Z
    Vector2 screenToWorld(float screenX, float screenY, Vector2 out);
    int scale(); float pixelsPerUnit(); RenderMode mode();
}
@PublicApi public final class SpriteRenderSystem extends EntitySystem {   // RENDER; Transform + Sprite
    public SpriteRenderSystem(World world, Renderer renderer, Assets assets, Time time); }

// ember.camera (no GL types; the renderer applies the rig to its OrthographicCamera)
@PublicApi public interface CameraShake { void addTrauma(float amount); }
@PublicApi public final class CameraRig implements CameraShake {
    public final Vector2 position; public float zoom = 1f; public float maxShakeOffset = 0.5f; public float traumaDecay = 1.5f;
    public final Vector2 viewSize;                           // set by the renderer: visible world size at zoom 1
    public void addTrauma(float amount); public float trauma(); public Vector2 shakeOffset(); public void update(float delta); }
@PublicApi public final class CameraFollow { public EntityRef target; public final Rectangle deadZone = new Rectangle();
    public float smoothing; public Rectangle bounds; }
@PublicApi public final class CameraFollowSystem extends EntitySystem {   // LATE; CameraFollow
    public CameraFollowSystem(World world, CameraRig rig, Time time); }
// EngineConfig: renderMode (default PIXEL_PERFECT), backgroundColor (default black)
```

## Data model
- **Screen layout** (pure function of window size, virtual size and mode): `PIXEL_PERFECT` uses the largest whole-number scale that fits both dimensions, centred with black bars (320x180: 1280x720 at x4, 1920x1080 at x6, 2560x1440 at x8, 1366x768 at x4 with 43/24 px bars). `PIXEL_PERFECT_FILL` uses the same scale and extends the visible world width to fill the window (2400x1080 shows 400x180 virtual pixels at x6). A window smaller than the virtual size scales down (fractional) and `scale()` reports 1. `SMOOTH` fits the virtual aspect ratio at a fractional scale.
- **Sprites:** size = region pixels / `pixelsPerUnit`, multiplied by `Transform.scale`; `origin` is in region pixels from the bottom-left corner and is both the draw anchor and the rotation/scale pivot (default (0, 0): the transform is the sprite's bottom-left, its feet for Y sorting). Position and rotation are interpolated with `time.alpha()`.
- **Layers:** drawn in list order; `Z` sorts by `Sprite.z` ascending (stable), `Y_SORT` by `Transform` y descending (higher y drawn first, behind), `NONE` keeps family order. A sprite on an unknown layer draws on `world` (or the first layer) and warns once per layer name.
- **Camera:** `CameraFollow.deadZone` is in world units, centred on the camera (its x/y offset the zone's centre); the target is followed at its interpolated position, so camera and sprites agree (no jitter). `smoothing` 0 follows instantly, otherwise eases by the factor 1 - exp(-smoothing * delta). `bounds` keeps the view inside a rectangle (centred if the view is larger). Shake offset = `maxShakeOffset` times trauma squared in a random direction; trauma decays by `traumaDecay` per second of scaled time, so it freezes while paused.
- **Snapping:** in the pixel-perfect modes the camera and sprite draw positions are rounded to whole virtual pixels.

## Dependencies
`ember.core`, `ember.ecs`, `ember.assets`, `ember.camera` (render); `ember.core`, `ember.ecs` (camera); libGDX `SpriteBatch`, `FrameBuffer`, `OrthographicCamera`, `Viewport`.

## Lifecycle/resource ownership
- `EmberGame` creates the renderer at boot (none on headless backends: `context.renderer()` then throws), resizes it with the window, and disposes it at shutdown after the game's services.
- `RENDER` phase: begin the world pass (apply rig, shake and snapping to the camera; bind the frame buffer at the layout's virtual size; clear to `backgroundColor`; begin the batch), run render systems, end the world pass (draw the frame buffer to the window scaled and letterboxed, bars black). `SMOOTH` draws directly into the fitted viewport.
- **Android context loss:** libGDX rebuilds managed frame buffers, shaders and textures itself; verified on the emulator. No engine registry until a non-managed GL object needs one.

## Error handling
- Unknown sprite layer: drawn on the default layer, warning once per name. Sprite with neither `region` nor `regionId`: skipped, warning once.
- `setLayers` with no layers or duplicate names: `IllegalArgumentException`.

## Performance considerations
Sorting and drawing allocate nothing per frame (preallocated buckets and a stable merge sort over entity arrays); one `SpriteBatch`, atlases recommended (D-010).

## Worked example
```java
int player = world.create();
world.add(player, Transform.class).teleport(10, 3);
world.add(player, new Sprite()).regionId = "atlas.main/player_idle";
world.addSystem(new SpriteRenderSystem(world, ctx.renderer(), ctx.assets(), ctx.time()));
int cam = world.create();
world.add(cam, new CameraFollow()).target = world.ref(player);
world.addSystem(new CameraFollowSystem(world, ctx.renderer().camera(), ctx.time()));
```

## Acceptance criteria
Part 1 (I-060) covers layer/z order, Y sort, pixel-perfect scaling, camera behaviour, resize, disposal and Android context loss; text and the safe area come later. The no-jitter check at 60/144 Hz is done visually with the demo in I-070.
- Reference scene: layer and z order correct; `ySort` orders by feet position.
- Pixel-perfect: correct integer scale and letterbox at 1280×720, 1920×1080, 2560×1440 and an odd size (e.g. 1366×768) for a 320×180 virtual resolution.
- No sprite jitter when the camera follows a moving target at 60 Hz and 144 Hz.
- Camera dead-zone, bounds and shake behave as configured.
- Text renders from a bitmap font and a FreeType-generated font.
- On Android: rendering survives suspend/resume with context loss; UI stays inside the safe area.
- Resources disposed on shutdown; resize handled.

## Risks/open questions
- Sub-pixel camera smoothing vs pixel snapping: default snaps; offer a "smooth camera" option that renders the frame buffer with a sub-pixel offset if demos need it.

## Tests
Pure tests for the screen layout at every listed window size and mode; renderer tests with a recording `Batch` and a fake render target (a do-nothing OpenGL stand-in cannot compile real shaders); sprite system tests for layer order, each sort mode, interpolation, snapping, flip, tint, origin, size and region lookup; camera tests for follow, dead zone, bounds, smoothing, zoom and shake decay; emulator screenshots for letterbox and fill.
