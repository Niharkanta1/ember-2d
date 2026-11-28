# Rendering and Camera

Packages `ember.render` (`Renderer`, `Sprite`, `RenderLayer`, `SortMode`, `RenderMode`, `SpriteRenderSystem`, `LayerContent`, `Text`, `TextRenderer`, `TextDraw`) and `ember.camera` (`CameraRig`, `CameraFollow`, `CameraFollowSystem`, `CameraScroll`, `CameraScrollSystem`). Spec F-004.

## 1. The pipeline
- The world is drawn with one libGDX `SpriteBatch` into a frame buffer at the **virtual resolution** (`virtualWidth` × `virtualHeight`, default 320 × 180).
- The frame buffer is scaled to the window by a whole number, with black bars.
- The camera and sprites are snapped to whole virtual pixels, and textures use `Nearest` filtering.
- The UI draws after the world.

| Window | Scale for 320 × 180 | Bars |
| --- | --- | --- |
| 1280 × 720 | × 4 | none |
| 1920 × 1080 | × 6 | none |
| 1366 × 768 | × 4 | 43 px left/right, 24 px top/bottom |

`RenderMode`:
- `PIXEL_PERFECT` (default): as above.
- `PIXEL_PERFECT_FILL`: widens the visible world instead of side bars. A 2400 × 1080 phone shows 400 × 180 virtual pixels at × 6.
- `SMOOTH`: fits any scale, without snapping or a frame buffer.

## 2. Sprites
```java
Sprite sprite = world.add(e, new Sprite());
sprite.regionId = "atlas.shooter/slime";   // resolved through Assets on first draw (or set sprite.region)
sprite.layer = "world";                     // render layer
sprite.z = 1f;                              // order in a Z-sorted layer
sprite.origin.set(8f, 0f);                  // anchor and pivot in region pixels from bottom-left: feet, centred
sprite.tint.set(1f, 1f, 1f, 0.5f);
sprite.flipX = true;
```
- **Requirements:** a sprite needs a `Transform` and is drawn by `SpriteRenderSystem(world, renderer, assets, time)` in the `RENDER` phase.
- **Size:** the region's pixel size ÷ `pixelsPerUnit` × `Transform.scale`. A 16-pixel sprite is 1 unit.
- **Rotation:** `Transform.rotation`, around the origin.
- **Interpolation:** the position is interpolated between fixed steps.
- **Atlas trimming:** whitespace-stripped regions keep their original frame, so origins stay correct.
- **Material effects:** a hit flash, an outline and a palette swap are fields on the sprite ([Sprite Materials](Sprite-Materials)).

## 3. Layers and sorting
```java
context.renderer().setLayers(
        new RenderLayer("floor", SortMode.NONE),
        new RenderLayer("world", SortMode.Y_SORT),   // top-down: higher y is drawn first (behind)
        new RenderLayer("fx", SortMode.NONE),
        new RenderLayer("bullets", SortMode.NONE));
```
- Layers draw in list order. Sort modes: `NONE` (creation order), `Z` (by `sprite.z`, stable), `Y_SORT` (by `-position.y`).
- In each layer, the system draws first the **layer content** (tile layers, bullets, particles), then the sprites, then **overlays** (world text).
- A sprite on an unknown layer is drawn on `world`, with one warning per name.
- The default is a single `world` layer sorted by `z`.

### Drawing your own things in a layer
Implement `LayerContent` and attach it:
```java
sprites.addLayerContent((layer, batch) -> {
    if (layer.equals("fx")) {
        // draw with the batch; it is begun with the world camera's projection (world units)
    }
});
```
The tile map renderer, the bullet renderer and particles all work this way. `addLayerOverlay` draws after a layer's sprites.

## 4. Text
Fonts are assets (`freetype` or `font`). World text is a component, drawn by a `TextRenderer` attached to the sprite system:
```java
new TextRenderer(world, context.renderer(), context.assets(), context.time()).attachTo(sprites);

Text text = world.add(entity, new Text());
text.font = "font.pixel";
text.text = "Level complete!\n1:23 - 2 deaths";
text.align = TextAlign.CENTER;
text.offset.set(0f, 4.5f);       // world units from the entity
text.wrapWidth = 10f;            // world units; 0 = no wrap
```
- **Scale:** one font pixel is one virtual pixel, and `scale` multiplies it.
- **Re-layout:** text is laid out again only when it changes.
- **One-off text:** `TextDraw.draw(batch, font, text, x, y, align, wrapWidth, pixelsPerUnit)` inside your own render code.
- **UI text:** use Scene2D `Label`s ([UI](UI)).

## 5. The camera
`context.renderer().camera()` returns the `CameraRig`:
- `position`: the centre of the view, in world units;
- `zoom`;
- shake: `addTrauma(0.4f)` on a hit, which decays by itself (`traumaDecay`, `maxShakeOffset`);
- `viewWidth()` and `viewHeight()`.

### Following
```java
CameraFollow follow = world.add(cameraEntity, new CameraFollow());
follow.target = world.ref(player);
follow.deadZone.setSize(3f, 2f);              // the target moves freely inside this box
follow.smoothing = 8f;                        // 0 = instant
follow.bounds = map.bounds(new Rectangle());  // keep the view inside the level
world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));   // LATE
```
The demos place a `camera` object in Tiled whose `CameraFollow.target` property points at the player object. The scene then only sets `bounds`. To start without a visible pan, set `camera().position` to the player's position in `enter`.

### Scrolling (shoot-'em-ups)
`CameraScroll` (`speedX`, `speedY`, `bounds`) with `CameraScrollSystem(world, rig, events)` moves the view at a constant speed. It keeps entities tagged `player` inside the view and publishes `ScrollFinished` at the end.

### Coordinates
- `renderer.screenToWorld(screenX, screenY, out)` converts a window position (pixels, top-left origin) to world units.
- `renderer.safeArea(out)` gives the part of the window clear of notches.
- `renderer.scale()` is the current whole-number scale, and `renderer.pixelsPerUnit()` the configured value.

## 6. Best practices
- Put movement in `FIXED` and drawing in `RENDER`, and let interpolation do the smoothing.
- Set sprite origins at the feet (`origin.y = 0`) for top-down games, so Y-sort and collision boxes line up.
- Use a separate layer for effects that should be drawn above every character (`fx`, `bullets`).
- Keep tile layers in Tiled on named render layers (`renderLayer` property) rather than drawing them yourself.

Lighting, shadows, fog, mist, glow and bloom built on render layers and `LayerContent` are in [Lighting and Atmosphere](Lighting-and-Atmosphere).
