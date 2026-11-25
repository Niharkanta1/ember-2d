# F-009 — Tilemaps and Entity Placement (Tiled)

- Status: Draft
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

**Loader compatibility**
- If libGDX's loader does not resolve object templates or class-typed properties, Ember2D's loader resolves them; fallback is Tiled's export options that detach templates and resolve object types. Spike S-02 decides which.

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
- Whether to merge adjacent solid tiles into larger boxes for performance; measure in S-02.
