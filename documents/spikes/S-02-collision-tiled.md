# S-02 — Kinematic collision and Tiled loading on a platformer map

- Time box: 2 days (used: about half a day, plus one play check by the developer)
- Phase: 2
- Related spec/ADR: F-006, F-009, F-010; tasks I-110, I-112, I-120
- Spike code: branch `spike/s-02-collision-tiled` (pushed for reference, never merged)
- Date: 2026-11-26

## Question
1. Does a kinematic mover handle a Tiled platformer map (tile seams, one-way tiles, moving platform) without snagging?
2. Does the libGDX loader resolve object templates and class-typed properties, or must Ember2D resolve them?

## Approach
- Hand-written sample map in both formats (`s02-level.tmx` and `s02-level.tmj`, 40×12 tiles of 16 px) with:
  - a floor of single tiles (a seam at every column)
  - a wall of stacked tiles
  - a one-way platform
  - a ceiling block
  - tile properties `solid` and `oneWay`
  - a map property
  - an embedded and an external tileset
  - a template (`.tx` / `.tj`) used twice, once with an override
  - objects with `type=` and with `class=`
  - a class-typed property (`DoorStats`, defined in `s02.tiled-project`)
  - an object reference
  - a point, a polyline, a horizontally flipped tile object and a rectangle
- `SpikeMover`, a minimal mover:
  - AABB actors with float positions in world units (1 tile = 1 unit, y up)
  - moves along x first, then y, in steps of at most half a tile
  - checks the moved box against a tile grid built from the tile properties
  - also checks against moving platforms, which carry riders and push other actors
  - uses a contact tolerance (`EPS` = 1e-4 tile), so a box that only touches a tile edge does not count as overlapping it
- Automated scenario tests run headless on the TMX map, plus a measurement with 500 actors.
- A loader inspection test loads both formats and reports what each Tiled feature becomes in libGDX.
- A playable check, started with `--spike-s02` (arrow keys, Space, Down+Space, R), was played by the developer on Windows 11.

## Out of scope
- Slopes
- Triggers and spatial-hash queries
- Actor-to-actor collision
- Infinite (chunked) maps
- Animated tiles and parallax
- The ECS integration
- Android

## Finding
**Answer 1: yes.** Float positions with a small contact tolerance and axis-by-axis movement do not snag on tile seams and give exact contacts. Positions do not need to be whole pixels.

**Answer 2: libGDX resolves almost everything**, including templates and class properties, with three conditions:
- class properties need the Tiled project file
- the `class=` object attribute is dropped
- JSON maps need JSON tilesets and templates

**Collision evidence** (13 tests, all passing)

| Scenario | Result |
| --- | --- |
| Run across 20 floor tiles at 8 tiles/s | Never blocked at a seam, no drop at a seam (y stays exactly 1.0), always moving forward |
| Fall along a wall of stacked tiles while pushing into it | Stops exactly at the wall (x = 29.25); keeps sliding down every step, no catch at seams |
| Fall onto the floor from a non-aligned height | Lands at exactly y = 1.0, `vy` = 0 |
| Move 10 tiles in one call toward a 1-tile wall | Stops at the wall, no tunnelling (half-tile sub-steps) |
| Jump into a ceiling | Top lands at exactly y = 7.0 |
| One-way platform | Jump up through it, land on top (y = 5.0), drop through to the floor with `dropThroughTicks`; walking sideways through it is not blocked |
| Moving platform | Carries a standing actor 2 tiles sideways and 1 tile up; pushes an actor in its path along the floor |
| Same seam run against one box per tile instead of the grid | No snag either |
| Developer play check (`--spike-s02`) | Done, no issues reported |

**Cost**: 500 actors, 600 fixed steps, same solids in three forms.

| Solids as | Time per step |
| --- | --- |
| Tile grid lookup | 0.035 ms |
| One box per tile (48 boxes, linear scan) | 0.340 ms |
| Merged boxes (7 boxes, linear scan) | 0.064 ms |

**Loader evidence (libGDX 1.14.2)**: `TmxMapLoader` and `TmjMapLoader` gave identical results.

| Tiled feature | libGDX result |
| --- | --- |
| Map, tile, object properties | Typed values (`Float`, `Integer`, `Boolean`, `String`) in `MapProperties` |
| Template (`template="….tx"` / `.tj`) | Resolved: template properties merged, instance overrides win (`Health.max` 5 vs template 3), template `type` and tile carried over, template tileset gid remapped to the map's firstgid |
| `type="pickup/coin"` | Property `type` |
| `class="pickup/coin"` (Tiled 1.9 style) | **Dropped**: no `type` property, the class is lost |
| Class property (`type="class" propertytype="DoorStats"`) | Nested `MapProperties` with the values, member defaults from the project file (`label = "door"`) and `type = "DoorStats"`; **throws** when `projectFilePath` is not set |
| Object reference (`type="object"`) | Resolved after loading to the same `MapObject` instance |
| Point | `PointMapObject` |
| Polyline | `PolylineMapObject`, vertices relative to the object position |
| Flipped tile object (gid with flip bits) | `TiledMapTileMapObject` with `isFlipHorizontally()` true and the right tile |
| Coordinates | Converted to y-up (bottom-left of the object in pixels) |
| JSON map pointing at `.tsx` / `.tx` | Not run; the libGDX source parses external tilesets and templates of a `.tmj` as JSON, so they must be `.tsj` / `.tj` |

**Surprises**
- **Project file:** class properties fail without the project file, and the error appears only at load time, from the first class property.
- **Class defaults:** the loader also fills in an object's class member defaults from the project file when its `type` names a project class (`loadMapPropertiesClassDefaults`). This was seen in the source, not tested.
- **Test mistake:** the first one-way test failed because the test jump was too weak to reach the platform, not because of the mover.

## Consequences (proposed; each needs approval)
1. **I-120: build on this mover.**
   - **Movement:** float positions, `EPS` contact tolerance, x then y, steps of at most half a tile.
   - **Tile collision:** solid and one-way tiles come from a tile grid lookup, not one box per tile.
   - **Other shapes:** solids that are not tiles and moving platforms use boxes. The spatial hash planned for I-120 is needed only for those and for actors; at 500 actors the grid costs 0.035 ms per step.
   - **Allocations:** remove the spike's per-call allocations, such as the rider list in `movePlatform`.
2. **I-110: build the tile grid from tile properties** (`solid`, `oneWay`) while loading the map. Use libGDX's TMX/TMJ loaders as they are; no own parser.
3. **I-110 / I-112: the Tiled project file is part of the map's assets.**
   - **Loading:** Ember2D passes `projectFilePath` (a per-game setting, for example `maps/<game>.tiled-project`) to the loader.
   - **Errors:** a missing project file gives a clear error that names it.
   - **Hot reload:** I-150 watches the project file too.
4. **I-112 reads the class from both places:** the `type` property, or the `class` attribute.
   - **The gap:** libGDX drops the `class` attribute.
   - **The fix:** Ember2D either reads it with a small loader subclass, or makes the map check reject `class=` with an error that names the object.
   - **Recommendation:** the error, plus a note to save maps in Tiled 1.10 or later. Tiled 1.10 and later should write `type`, which I have not checked yet; check this against a real Tiled save in I-112.
5. **I-112 can use libGDX's resolution:**
   - templates and overrides
   - object references (same instance)
   - class properties, as nested `MapProperties` with a `type`

   That leaves only mapping properties to components, such as `Health.max`, and the two-pass spawn for references.
6. **Supported formats:**
   - **TMX** with `.tsx`/`.tx`, or **TMJ** with `.tsj`/`.tj`.
   - **Mixed setups:** document them as unsupported, or use Tiled's JSON export options "Embed tilesets" and "Detach templates".
7. **Still to check:**
   - slopes, if a game needs them
   - infinite maps
   - collision objects defined per tile in Tiled's collision editor
   - Android loading of the project file (a plain JSON read, so low risk)
