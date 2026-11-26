# F-004 — Rendering, Camera and Text

- Status: Part 1 Ready (sprites, layers and sorting, pixel-perfect pipeline, camera follow/dead-zone/bounds/zoom/shake, resize, context loss: expanded and approved in I-060, 2026-11-26); text Ready (I-140: expanded and approved 2026-11-26, see "Text (I-140)"); look-ahead, scrolling and parallax (Phases 2/4), UI layer (I-210), safe area (I-225), debug draw hook (I-080): Draft
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

## Text (I-140)
Status: Ready (expanded for I-140 and approved 2026-11-26; see "Decisions in I-140" for how it was built).

**Fonts are assets.**
- **Bitmap fonts:** stay manifest entries of type `font` (`.fnt` + page images).
- **FreeType fonts (new type `freetype`):** a `.ttf` or `.otf` entry rendered to a bitmap font when its group loads, through libGDX's FreeType loaders (`gdx-freetype`, already in the dependency register).
- **Rendering options:** an optional `font` object on the entry:

```json
{ "id": "font.ui", "path": "fonts/PressStart2P.ttf", "type": "freetype", "group": "global",
  "font": { "size": 8, "mono": true, "color": "#ffffff", "borderWidth": 1, "borderColor": "#000000",
            "shadowOffsetX": 0, "shadowOffsetY": 0, "characters": "default", "markup": false } }
```

- **Font options:**
  - `size` is in pixels (virtual pixels for world text).
  - `mono: true` turns off anti-aliasing for pixel fonts.
  - `characters` is `"default"` (libGDX's default set) or an explicit string.
  - `markup` enables libGDX colour tags (`[RED]...[]`). It is also accepted on `font` entries.
- **Validation:** unknown fields, size ≤ 0, and bad colours fail with the manifest entry and field. A missing optional font falls back to libGDX's built-in font, as before.
- **Platforms:** FreeType needs native libraries. Desktop gets `gdx-freetype-platform:natives-desktop`; the Android launcher module extracts the ABI natives like the core ones (`copyAndroidNatives`).

**`Text` component and `TextRenderSystem`.**
- **Fields:** `Text` has `font` (asset ID), `text`, `color`, `scale` (1 = the font's pixel size), `align` (`LEFT`, `CENTER`, `RIGHT`), `wrapWidth` (world units; 0 = no wrapping), `offset` (world units from the entity's `Transform`) and `layer` (render layer, default `world`).
- **Drawing:** world text is drawn in world units: one font pixel is one virtual pixel (`1 / pixelsPerUnit`). In the pixel-perfect modes the position snaps to whole virtual pixels, so pixel fonts stay crisp.
- **Placement:** `TextRenderSystem` (`RENDER`) draws every entity with a `Transform` and a `Text` at its interpolated position. The anchor is the text's **baseline-left** for `LEFT`, baseline-centre for `CENTER`, and so on.
- **Prefabs:** `Text` is an engine component, so prefabs and Tiled objects can carry it; a sign's text can be a Tiled property, `Text.text`.
- **Layer order:**
  - Text joins the render layers through a new `SpriteRenderSystem.addLayerOverlay(LayerContent)`: overlays draw **after** the sprites of a layer, while `addLayerContent` (tile maps) draws before them.
  - Text is therefore above the sprites of its layer and below later layers. No z-sorting between text and sprites inside one layer.
- **Caching:** the laid-out glyphs are cached per entity and rebuilt only when the text, font, scale, alignment or wrap width changes, so unchanged text allocates nothing per frame.

**Immediate drawing.** `TextDraw.draw(batch, font, text, x, y, align, wrapWidth)` draws a line in world units inside a render system or layer content, for debug labels and one-off text, through a reused layout.

**Not in I-140:**
- HUD and menu text in screen space (the UI layer, Scene2D, I-220)
- signed-distance-field fonts
- right-to-left and complex scripts
- per-character effects
- localisation (later)

## API sketch (I-140)
```java
package dev.ember2d.render;
@PublicApi public enum TextAlign { LEFT, CENTER, RIGHT }
@PublicApi public final class Text {                      // component
    public String font; public String text = "";
    public final Color color = new Color(Color.WHITE); public float scale = 1f;
    public TextAlign align = TextAlign.LEFT; public float wrapWidth; public final Vector2 offset = new Vector2();
    public String layer = "world";
}
@PublicApi public final class TextRenderer implements LayerContent {   // built as a layer overlay, not a system
    public TextRenderer(World world, Renderer renderer, Assets assets, Time time);
    public TextRenderer attachTo(SpriteRenderSystem sprites);   // registers itself as a layer overlay
}
@PublicApi public final class TextDraw {
    public static void draw(Batch batch, BitmapFont font, CharSequence text, float x, float y, TextAlign align,
                            float wrapWidth, float pixelsPerUnit);
}
// SpriteRenderSystem gains: public void addLayerOverlay(LayerContent overlay); removeLayerOverlay(...)
// Assets: get(id, BitmapFont.class) works for both "font" and "freetype" entries.
```

## Tests (I-140)
- **Manifest:** `freetype` entries with every option, validation errors, and `markup` on `font` entries.
- **Loading (headless):** a test `.ttf` loads through the asset service into a `BitmapFont` of the asked size, with `mono` and a border; a bitmap `.fnt` too.
- **Text rendering (recording batch):**
  - glyphs are drawn at the snapped baseline position in world units
  - left, centre and right alignment
  - wrapping
  - `offset`
  - layer order: text after the sprites of its layer and before the next layer's tiles and sprites
  - no glyph re-layout when nothing changed
- **`Text` from prefabs and Tiled:** a `Text.text` override.
- **Demo:** a wooden sign near the start (a Tiled object, prefab `prop/sign`, text from a Tiled property) and a small "jump!" label above the moving platform, in a pixel font. You check desktop; I check the emulator (Android FreeType natives in the minified build).

## Decisions in I-140
- **`TextRenderer`, not `TextRenderSystem`.** Text is drawn only inside the sprite system's layer loop, so it is a `LayerContent` overlay like `TileMapRenderer`, not an `EntitySystem`. A `RENDER` system would have nothing to do on its own and would need a second registration. Usage: `new TextRenderer(world, renderer, assets, time).attachTo(sprites)`.
- **FreeType entries are keyed by ID.** One `.ttf` can back several entries at different sizes, so a `freetype` entry is loaded under the key `ember-freetype/<id>.ttf` (`AssetManifest.Entry.key()`), with the real path in the loader parameter. `font` and other entries keep their path as the key.
- **Font option defaults:** size 16, white, no border or shadow, `characters` `"default"`, `markup` false, nearest filtering (pixel fonts stay crisp under the integer scale). Shadow offsets are whole pixels. On `font` (bitmap) entries only `markup` is accepted.
- **Colour is part of the layout cache key** (libGDX bakes the colour into the glyph vertices), together with text, font, scale, alignment and wrap width.
- **Snapping:** the anchor (position + offset, then shifted to the baseline by the cap height) is snapped to whole virtual pixels in the pixel-perfect modes, not each glyph; with integer-sized fonts every glyph then lands on whole pixels (tested).
- **Wrapping:** with `wrapWidth` > 0 the box is placed so the anchor is its left edge, centre or right edge by `align`; without it libGDX aligns around the anchor itself.
- **Unknown layer:** warned once per name and drawn on `world` (or the first layer), as for sprites.
- **Tests:** `.fnt` loading was already covered by the asset tests; `markup` on a `font` entry is covered by validation only (accepted; other options rejected).
- **Demo:** the moving platform's label reads "ride me!" instead of "jump!"; the sign's post is the placeholder texture tinted brown and its text floats above it (no sign art yet).

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
