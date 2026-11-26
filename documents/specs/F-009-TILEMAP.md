# F-009 — Tilemaps and Entity Placement (Tiled)

- Status: map part Ready (I-110, approved 2026-11-26; see "Decisions in I-110"). Entity placement (I-112) and the type generator (I-245): Draft.
- Phase: 2 (loading, collision, entity placement), 3 (Tiled project type generator)
- Module / package: `ember-core` / `ember.tilemap`; generator as a Gradle task in the build
- Depends on: F-004, F-006, F-008, F-010
- Tasks: S-02, I-110, I-112, I-245
- Decision: ADR-016

## Goal
Use Tiled as the level editor: load and render maps, turn tiles into collision, and place props, enemies, pickups, triggers and paths as objects that become prefab instances.

## Scope
**Maps and tiles**
- Load TMX and Tiled JSON via libGDX loaders (through F-008).
- Render tile layers with libGDX's orthogonal tiled map renderer inside Ember2D render layers and the pixel-perfect pipeline. Layer → render layer mapping by a layer property (`renderLayer`); parallax factors honoured.
- Animated tiles defined in Tiled play at runtime.
- Tile collision: tiles with property `solid=true` or `oneWay=true`, or any tile on a layer named `collision`, become a `TileGrid` for F-006.
- Map-level properties: camera bounds, music ID, gravity or other game values (passed through to the scene sidecar).
- Grid helpers: tile ↔ world coordinates, tile lookup.

**Entity placement workflow**
1. Each placeable thing is a **prefab** JSON file (F-010), e.g. `prefabs/enemy/slime.json`.
2. In Tiled, objects go on object layers (convention: `entities`, `triggers`, `paths`; any object layer works).
3. An object's **Class** (saved as `type` in TMX for compatibility) is the prefab ID, e.g. `enemy/slime`.
4. **Tile objects** from an "editor icons" tileset (one tile per prefab, using the real sprite) show what is placed. The tile image is editor-only unless the prefab has no sprite.
5. **Custom properties override prefab fields**: `Component.field` (e.g. `Health.max = 5`) or a bare field name when unique across the prefab's components.
6. **Object templates** (`.tx`): one template per prefab so objects are drag-and-drop with the right class, icon and default properties.
7. **Object references**: Tiled `object`-type properties link objects (a door's `switch`, an enemy's `patrolPath`) and become `EntityRef` values after spawning (two-pass spawn).
8. **Shapes**: rectangles become trigger/collider areas; points become spawn points; polylines/polygons become paths and zones.
9. Object rotation and flip map to `Transform`/`Sprite`; object name becomes the entity name (findable from code and dev tools).

**Tiled project type generator (Phase 3)**
- Gradle task `tiledTypes` reads all prefab files and the component registry and writes custom class types (fields, types, defaults) into the `.tiled-project` file, so Tiled offers the correct fields when a class is chosen. Re-run after changing prefabs.

**Loader compatibility** (decided by S-02, `spikes/S-02-collision-tiled.md`)
- libGDX's TMX and TMJ loaders resolve templates, class properties, object references, points, polylines and flipped tile objects. Ember2D uses them as they are.
- **Conditions:**
  - Class properties need the Tiled project file (`projectFilePath`).
  - A TMJ map needs `.tsj` tilesets and `.tj` templates.
  - The Tiled 1.9 `class=` object attribute is dropped. This is handled in I-112.

## Map design (I-110)
Status: Ready (approved 2026-11-26).

**Loading (F-008).**
- **Manifest:** maps are manifest entries of a new type `map`. The loader follows the extension: `.tmx` uses `TmxMapLoader`, `.tmj` uses `TmjMapLoader`. Both are registered with the `AssetManager`, so maps load synchronously or in groups like other assets, and their tilesets and images are dependencies.
- **Project file:** a new `EngineConfig.tiledProject` (asset path, default `null`) is passed to every map load as `projectFilePath`. The runtime copy of the `.tiled-project` file lives in the game's assets; the editing copy stays in `assets-src/`.
- **Errors:** a class property without a project file fails with a message naming the map and `EngineConfig.tiledProject`. These also fail when the map loads, naming the map path:
  - non-orthogonal maps
  - infinite maps
  - malformed files
- **Textures:** tileset textures load with nearest filtering, which is the libGDX default and right for pixel art.

**Units.**
- A map is drawn at `1 / pixelsPerUnit`, so a 16 px tile is 1 world unit at the default 16 px per unit.
- World y grows upwards, and map row 0 is the bottom row (libGDX already flips TMX rows).
- The map's bottom-left corner is world (0, 0).

**`TileMap` (`ember.tilemap`).**
- A thin wrapper around libGDX's `TiledMap`, built once per loaded map.
- **What it gives:** size in tiles and world units, tile size in world units, map properties, layer names, tile lookup, tile and world conversion, world bounds, and the collision grid.
- **Raw map:** `tiled()` returns the underlying `TiledMap` for anything else.
- **Ownership:** the `AssetManager` owns the map; `TileMap` never disposes it.

**Collision (`ember.collision.TileGrid`).**
- **Source:** built once from the map. A cell is
  - **solid** if any tile in it has the property `solid=true`, or the tile is on a layer named `collision`;
  - **one-way** if a tile has `oneWay=true` (and no layer makes the cell solid).
- **Location:** `TileGrid` is pure data (size, cell size in world units, flags, lookups). It lives in `ember.collision` because collision code (I-120) reads it and must not depend on `ember.tilemap`. I-110 creates `ember.collision` with only this class.
- **Hidden layer:** the `collision` layer is never drawn.

**Rendering (`ember.render`, `ember.tilemap`).**
- **How layers interleave:** tile layers must sit between sprite layers. `SpriteRenderSystem` gets a small hook, `addLayerContent(LayerContent)`: for each render layer, in the renderer's layer order, it first calls every registered content for that layer, then draws that layer's sprites.
- **`TileMapRenderer`** (`ember.tilemap`) is such a content.
  - It draws each visible tile layer and image layer in Tiled order.
  - It draws through the renderer's batch and camera, using libGDX's `OrthogonalTiledMapRenderer` (one draw pass, no extra batch).
- **Render layer:** a Tiled layer property `renderLayer` (string) picks the render layer; the default is `world`. Unknown names warn once and fall back to `world`, as for sprites. Something drawn above the player goes on a later render layer, for example `foreground`, which the game adds to `renderer.setLayers(...)`.
- **Parallax:** Tiled's per-layer parallax factors and layer offsets are applied by libGDX's renderer; opacity and tint are honoured. In the pixel-perfect modes the camera is already snapped. Parallax offsets are not snapped; see the risks.
- **Image layers:** drawn too, with parallax. These are Tiled image layers, typically backgrounds.

**Animated tiles.**
- libGDX's `AnimatedTiledMapTile` runs on one static real-time clock, so tiles would keep animating while the game is paused.
- After loading, `TileMap` replaces each animated tile with an Ember tile that has the same frames and durations, driven by **game time** (`Time.elapsed()`).
- They pause with the game and follow time scale.

**Grid helpers.** These are on `TileMap`:
- `tileX(worldX)` and `tileY(worldY)`
- `tileCenter(x, y, out)`
- `tile(layer, x, y)` (null when empty or out of range)
- `bounds(out)` (world rectangle, for `CameraFollow.bounds`)

## API sketch (I-110)
```java
package dev.ember2d.tilemap;

@PublicApi
public final class TileMap {
    public static TileMap of(TiledMap map, String source, float pixelsPerUnit); // checks orientation, swaps animated tiles
    public static TileMap load(Assets assets, String assetId, float pixelsPerUnit); // tilemap must not see ember.app
    public TiledMap tiled();
    public String source();
    public int width(); public int height();                                    // tiles
    public float tileWidth(); public float tileHeight();                        // world units
    public float worldWidth(); public float worldHeight();
    public MapProperties properties();
    public List<String> layerNames();
    public TiledMapTile tile(String layer, int x, int y);                       // null if empty or outside
    public int tileX(float worldX); public int tileY(float worldY);
    public Vector2 tileCenter(int x, int y, Vector2 out);
    public Rectangle bounds(Rectangle out);
    public TileGrid collision();                                                // built once, cached
}

@PublicApi
public final class TileMapRenderer implements LayerContent {
    public TileMapRenderer(TileMap map, Renderer renderer, Time time);
    public TileMapRenderer attachTo(SpriteRenderSystem sprites);                // registers itself, returns this
}

package dev.ember2d.collision;

@PublicApi
public final class TileGrid {
    public TileGrid(int width, int height, float cellWidth, float cellHeight);  // origin (0,0), row 0 at the bottom
    public int width(); public int height(); public float cellWidth(); public float cellHeight();
    public boolean solid(int x, int y);  public boolean oneWay(int x, int y);  // false outside
    public void setSolid(int x, int y, boolean solid); public void setOneWay(int x, int y, boolean oneWay);
    public int cellX(float worldX); public int cellY(float worldY);
}

package dev.ember2d.render;

@PublicApi
public interface LayerContent {
    /** Draws this content's part of a render layer; the batch is begun with the world camera. */
    void drawLayer(String layer, Batch batch);
}
// SpriteRenderSystem: public void addLayerContent(LayerContent content); removeLayerContent(...)
```
The `app` wiring:
- the asset service registers the map loaders
- `EngineConfig.tiledProject` is validated and passed to the loaders
- a scene creates `TileMap`, `TileMapRenderer` and its systems in `enter`

Worked example:
```java
protected void enter(EngineContext ctx) {
    TileMap map = TileMap.load(ctx.assets(), "map.test", ctx.renderer().pixelsPerUnit());
    SpriteRenderSystem sprites = new SpriteRenderSystem(world(), ctx.renderer(), ctx.assets(), ctx.time());
    world().addSystem(sprites);
    new TileMapRenderer(map, ctx.renderer(), ctx.time()).attachTo(sprites);
    follow.bounds = map.bounds(new Rectangle());
    TileGrid solids = map.collision();   // for I-120
}
```

## Decisions in I-110
The defaults were approved as proposed. Details settled while implementing:
- **Batch colour:** `SpriteRenderSystem` sets the batch colour to white before each `LayerContent` call.
  - libGDX's tile renderer multiplies tiles by the batch colour.
  - Without the reset, a transparent sprite drawn last in `world` made the `foreground` tiles invisible. This was found on the emulator, and a test now covers it.
- **No projection reset:** `TileMapRenderer` sets only the tile renderer's culling bounds from the world camera. libGDX's `setView(camera)` would also reset the shared batch's projection and flush the batch every frame.
- **Loader checks:**
  - The engine's map loaders (`assets.internal.MapLoaders`) check orientation and infinite maps when loading. Their errors are `GdxRuntimeException`s that name the map, wrapped by the `AssetManager` like other load errors.
  - `TileMapException` is thrown by `TileMap.of` for maps that did not come through those loaders: no orientation, not orthogonal, or empty.
- **Project file:** `GdxAssets.setTiledProject(path)` fails at boot when the file is missing (`AssetLoadException` naming `EngineConfig.tiledProject`). A class property without a project file fails, naming the map and `EngineConfig.tiledProject`.
- **Group layers:** they are flattened in Tiled order. A layer without `renderLayer` takes its group's value. A hidden group hides its children.
- **Hidden layers:** they still count for collision (a `collision` layer is usually hidden in Tiled), but are not drawn.
- **Small additions:**
  - `TileGrid.solidCount()` and `oneWayCount()` (for logs and tests)
  - `TileMap.COLLISION_LAYER`, `SOLID`, `ONE_WAY`
  - `TileMapRenderer.RENDER_LAYER`, `DEFAULT_LAYER`
- **Demo:**
  - The old marker sprites are replaced by a 40×12 test level (`maps/demo-level.tmx`) with generated placeholder art, ground at y = 2, and render layers `background`, `world` and `foreground`.
  - The sprites do not collide yet (I-120).

## Error handling (I-110)
- **Missing map or tileset image:** an `AssetLoadException` from F-008, with the path.
- **Wrong map shape:** a non-orthogonal or infinite map fails with `TileMapException(source, reason)`.
- **Missing project file:** a class property with no project file fails with a message naming `EngineConfig.tiledProject`.
- **Bad layer name:** `tile(layer, ...)` with an unknown layer name throws `IllegalArgumentException`, listing the layers.
- **Wrong collision property type:** `solid` or `oneWay` that is not a boolean is reported once per tileset tile, with the map and tile ID, and ignored.

## Performance (I-110)
- **Drawing:** libGDX culls tile layers to the camera view. Tile drawing goes through the shared batch, one texture per tileset, so render calls grow with the number of tilesets, not tiles.
- **Allocation:** nothing is allocated per frame.
- **Collision grid:** built once per map, as two boolean arrays.
- **Measured in S-02:** grid lookups for 500 actors cost 0.035 ms per step.

## Acceptance criteria (I-110)
- Sample TMX and TMJ maps load through the manifest, and their layer order and parallax match Tiled.
- Tile and world conversion is correct for `pixelsPerUnit` 16 and 8, with y flipped.
- Collision: `solid` and `oneWay` tiles and a `collision` layer give the right `TileGrid`.
- Animated tiles animate in game time and stop while paused.
- `renderLayer` places tile layers between sprite layers.
- Error cases give clear messages: missing project file, isometric map, infinite map, unknown layer.
- Demo: the demo scene uses a small test map with a parallax background, a foreground layer in front of the player, an animated tile and camera bounds from the map. Desktop is checked by you, and Android on the emulator.

## Tests (I-110)
- **Headless:** loading through `GdxAssets` (TMX and TMJ), `TileMap` geometry and lookups, `TileGrid` from properties and from a `collision` layer, animated-tile frames over game time and pause, and errors.
- **Rendering:** `SpriteRenderSystem` content order, with a recording batch: background tile layer, sprites, foreground layer.
- **Visual:** the parallax rendering is checked visually in the demo.

## Non-goals
- A custom map format or editor.
- Isometric/hexagonal maps (orthogonal only for now).
- Runtime auto-tiling (Tiled handles it at edit time).
- LDtk (possible later as a second loader).

## Worked example
TMX object:
```xml
<object id="12" name="slime_cave_1" type="enemy/slime" gid="301" x="96" y="160" width="16" height="16">
  <properties>
    <property name="Health.max" type="int" value="5"/>
    <property name="patrolPath" type="object" value="13"/>
  </properties>
</object>
<object id="13" name="slime_path" x="80" y="176"><polyline points="0,0 64,0"/></object>
```
Result: an entity named `slime_cave_1` from prefab `enemy/slime`, `Health.max = 5`, and a `PatrolPath` component referencing the polyline's points.

## Acceptance criteria
- Sample TMX and JSON maps load; layer order and parallax match Tiled.
- Tile-to-world mapping correct with the project's `pixelsPerUnit` (Y axis flipped correctly).
- Collision tiles produce solids; one-way honoured; animated tiles animate.
- Objects spawn prefab instances with overrides; templates resolved; object references become valid `EntityRef`s.
- Unknown prefab, unknown property or wrong property type reported with map, object name and ID.
- Empty and malformed maps produce clear errors.
- Hot reload of a map rebuilds the scene in dev builds.
- `tiledTypes` output loads in Tiled and shows prefab fields.

## Risks/open questions
- Whether to merge adjacent solid tiles into larger boxes for performance: answered by S-02. A tile-grid lookup is faster than boxes (0.035 ms vs 0.34 ms per step, 500 actors); boxes are only for solids that are not tiles.
- **Parallax in pixel-perfect mode:** parallax layer offsets are not snapped to whole pixels, so slow parallax layers may shimmer by one pixel. If the demo shows it, snap the offset in `TileMapRenderer` (a follow-up inside I-110).
- **Smooth mode seams:** in `SMOOTH` mode, tile seams can show thin lines from texture bleeding. Tilesets should have 1 px spacing or extruded edges; this is documented in the guide.
