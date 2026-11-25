# F-004 — Rendering, Camera and Text

- Status: Draft
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

## API sketch
```java
@PublicApi public final class Sprite { public String regionId; public TextureRegion region; public Color tint = new Color(Color.WHITE);
    public boolean flipX, flipY; public String layer = "world"; public float z; public final Vector2 origin = new Vector2(); }
@PublicApi public interface Renderer { Batch batch(); OrthographicCamera worldCamera(); Viewport viewport();
    void addLayer(String name, SortMode mode); Vector2 screenToWorld(float sx, float sy, Vector2 out); }
@PublicApi public final class CameraFollow { public EntityRef target; public final Rectangle deadZone = new Rectangle();
    public float lookAhead, smoothing; public Rectangle bounds; }
@PublicApi public interface CameraShake { void addTrauma(float amount); }
@PublicApi public final class Label2D { public String fontId; public String text; public Color color; public int align; }
```

## Acceptance criteria
- Reference scene: layer and z order correct; `ySort` orders by feet position.
- Pixel-perfect: correct integer scale and letterbox at 1280×720, 1920×1080, 2560×1440 and an odd size (e.g. 1366×768) for a 320×180 virtual resolution.
- No sprite jitter when the camera follows a moving target at 60 Hz and 144 Hz.
- Camera dead-zone, bounds and shake behave as configured.
- Text renders from a bitmap font and a FreeType-generated font.
- On Android: rendering survives suspend/resume with context loss; UI stays inside the safe area.
- Resources disposed on shutdown; resize handled.

## Risks/open questions
- Sub-pixel camera smoothing vs pixel snapping: default snaps; offer a "smooth camera" option that renders the frame buffer with a sub-pixel offset if demos need it.
