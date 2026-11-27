# Tilemaps and Tiled

Package `ember.tilemap` (`TileMap`, `TileMapRenderer`) with `MapSpawner` and `TiledTypes` from `ember.data`. Spec F-009, ADR-016. [Tiled](https://www.mapeditor.org) 1.10 or newer is the level editor; Ember2D has no map format of its own.

## 1. Workflow
1. Draw the level in Tiled: orthogonal, not infinite, with tilesets stored in the game's assets folder.
2. List it in the manifest: `{ "id": "map.level1", "path": "maps/level1.tmx", "type": "map", "group": "level1" }`. Its tilesets and images load with it.
3. In the scene:
```java
float ppu = context.renderer().pixelsPerUnit();
TileMap map = TileMap.load(context.assets(), "map.level1", ppu);
Collision collision = new Collision(world, context.events());
collision.addTileGrid(map.collision(), 0);
new MapSpawner(context.prefabs()).spawn(world, map);                    // objects become entities
SpriteRenderSystem sprites = new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time());
world.addSystem(sprites);
new TileMapRenderer(map, context.renderer(), context.time()).attachTo(sprites);
camera.bounds = map.bounds(new Rectangle());
```

## 2. Units and coordinates
- One tile of `pixelsPerUnit` pixels is 1 world unit.
- The map's **bottom-left** corner is world (0, 0), and y grows **upwards**.
- Helpers:
  - `map.width()` and `height()` (tiles); `worldWidth()` and `worldHeight()`;
  - `tileX(worldX)`, `tileY(worldY)` and `tileCenter(x, y, out)`;
  - `tile(layer, x, y)`, `layerNames()` and `properties()`;
  - `tiled()`, the libGDX `TiledMap`.

## 3. Layers and drawing
- Each tile or image layer draws in the render layer named by its **`renderLayer`** custom property (default `world`). Declare the render layers in the scene ([Rendering and Camera](Rendering-and-Camera)).
- Within a render layer, map layers draw before the sprites.
- Tiled's parallax factors, offsets, opacity and image-layer repeat are honoured. Hidden layers are not drawn, and group layers pass their `renderLayer` to their children.
- Animated tiles run on game time.

## 4. Collision from tiles
A cell is **solid** when a tile in it has the bool property `solid`, or when it is on a layer named `collision`. A cell is **one-way** when a tile has `oneWay`. Set these once in the tileset (`.tsx`). The demos mark walls and furniture solid there.

## 5. Placing entities
`MapSpawner` turns every object that has a **Class** (Tiled's Class field, saved as `type`) into an instance of the prefab with that ID:
```xml
<object id="4" name="wave1_a" type="spawner" x="56" y="80">
  <properties>
    <property name="Spawner.prefab" value="enemy/slime"/>
    <property name="Spawner.delay" type="float" value="1.0"/>
  </properties><point/></object>
```
- **Position:** a point object is the position. A rectangle or tile object gives its bottom-centre (the feet).
- **Name:** the object's name becomes the entity's name: `world.findByName("player")`.
- **Overrides:** custom properties named `Component.field` (or a unique bare `field`) override prefab values.
- **References:** object-reference properties fill `EntityRef` fields; the demos point `CameraFollow.target` at the player. They can also fill a `Vector2` (a point), a `Rectangle` or a `float[]` (a polyline).
- **Templates** (`.tx`) work as in Tiled.
- **Errors:** all problems of a map are reported together, naming the object and property.
- `spawn` returns the created entities.

## 6. Class-typed properties: the Tiled project generator
To let Tiled offer your components and prefabs as classes, with fields and defaults:
```sh
./gradlew :demos:demo-platformer:core:tiledTypes
```
- It writes `assets/maps/platformer.tiled-project`: one class per component and one per prefab.
- Open the maps through that project in Tiled. When you give an object the class `enemy/pig`, Tiled lists its fields with the prefab's values.
- Set `config.tiledProject = "maps/platformer.tiled-project"` so the game reads class-typed properties.
- **Left out:** `Transform`, sprite flips, fields set by object references, and fields that start as null. These would overwrite values that come from the object itself.
- **Which wins:** a plain property (`Patroller.speed`) always beats a class-typed one.
- **Staleness:** `TiledProjectTest` fails when the committed file is stale. Rerun the task after adding a component or changing a prefab. Only the platformer demo uses this generator; the others use plain properties.

The task is defined in the platformer demo's `core/build.gradle` and calls `dev.ember2d.data.TiledTypes`. Copy it to use the generator in your game.

## 7. Hot reload
Saving a map, a tileset or a tileset image in a dev run reloads the map and restarts the scene with a fresh world. A scene can handle the reload itself in `onAssetReloaded`.

## 8. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| An object doesn't appear | it has no Class | set the Class to a prefab ID |
| "Tiled 1.10 or later" error | saved with Tiled 1.9 (`class=`) | resave with Tiled 1.10+ |
| "There is no class with name 'X' in given Tiled project file" | stale project file | rerun `tiledTypes` |
| Object fields overwritten by class defaults | a class-typed property sets a field the object also sets | use plain properties for those fields |
| Multi-line text shows `#10` | newline escaped in an attribute | use Tiled's multi-line property |
