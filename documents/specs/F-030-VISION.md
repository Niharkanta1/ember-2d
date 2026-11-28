# F-030 — Field of view and fog (kit-topdown `vision`)

- Status: Ready (approved 2026-11-28; the developer chose "engine feature" over demo-only code)
- Phase: 6 (pulled in by the Gold in Shadows demo, F-029)
- Module / package: `kit-topdown` / `dev.ember2d.kit.topdown.vision`
- Depends on: F-004 (render layers), F-006 (collision and tile grids), F-009 (tile maps)
- Tasks: I-610

## Goal
A top-down game can show only what its hero can see:
- a small circle of sight all around;
- a longer cone in the aim direction;
- both blocked by walls.

Everything else is under a dark fog, and things standing in it (enemies, pick-ups, enemy bullets) are hidden. Gold in Shadows is built on this, and the shooter and RPG demos could use it with a few lines.

## Scope
**`VisionSettings`** (plain data, prefab- and JSON-friendly; defaults are Gold in Shadows' values in world units at 16 pixels per unit):

| Field | Default | Meaning |
| --- | --- | --- |
| `nearRadius` | 34 px | full sight all around |
| `coneRange` | 120 px | how far the cone reaches |
| `coneHalfAngle` | 38° | half the cone's width |
| `coneSoftness` | 14° | the cone's sides fade over this angle |
| `rayCount` | 240 | rays per update (resolution) |
| `edgeSoftness` | 10 px | the fog's edge fades over this distance |
| `wallPeek` | 6 px | how far sight enters a wall, so its face is visible |
| `darkness` | 0.9 | fog opacity |
| `fogColor` | the palette's darkest colour | |

**`VisionField`** (pure logic, no GL; tested headless):
- `update(x, y, aimDegrees)` casts the rays from the viewer against **opaque cells** and stores each ray's length.
- Opaque cells come from a `TileGrid` (solid cells) plus any cells marked opaque (`setOpaque(x, y, true)`).
- Low props (barrels, crates, chests) are solid but not opaque: they block movement but not sight. The map decides this per tile with a tile property `opaque` (default: solid tiles are opaque).
- **Queries:** `isVisible(x, y)`, `isVisible(x, y, radius)` and `visibility(x, y)` (0..1, with the soft edges).
- **Polygon:** `polygon()` gives the visible fan for drawing.

**`FogLayer`** (`LayerContent`, drawn in a render layer above the world):
- **Mask:** each frame it draws the visible fan into a low-resolution mask, one texel per virtual pixel, so the fog edge stays pixelated like the art. The mask is a `FrameBuffer` the size of the virtual screen, made once and remade after a GL context loss.
- **Drawing:** it composites the mask over the world.
- **Off:** with `enabled = false` it draws nothing.

**`Viewer`** (component) and **`VisionSystem`** (LATE phase, after movement and aiming):
- The system updates the field from the entity that has `Viewer`. It uses the entity's `Transform` and its `Aim` angle if it has one (kit-topdown shooter), else `Viewer.angle`.
- **`HiddenInFog`** (component): the system hides its `Sprite` (alpha to 0, keeping the previous tint) while the entity is not visible, and shows it again when it is.
  - Bullets: the system also hides kit-bullets bullets whose type is marked `hideInFog` (a new optional bullet-type field), so enemy shots are hidden in the dark.
- **Events:** `CameIntoView(entity)` and `WentOutOfView(entity)` are queued for `HiddenInFog` entities, for "!" reactions and sounds.

**Android:** the fog is a GLES 2.0 frame buffer and one textured quad; no extensions.

## Non-goals
- Dynamic lights, coloured lighting, normal maps (a separate "Lighting" extra in the roadmap).
- Several viewers (split screen, allies sharing sight).
- Sight blocked by entities (only tiles and opaque cells block it).

## API sketch
```java
package dev.ember2d.kit.topdown.vision;
public final class VisionSettings { public float nearRadius, coneRange, coneHalfAngle, coneSoftness, edgeSoftness,
        wallPeek, darkness; public int rayCount; public final Color fogColor; }
public final class VisionField {
    public VisionField(TileGrid grid, VisionSettings settings);
    public void setOpaque(int cellX, int cellY, boolean opaque);
    public void update(float x, float y, float aimDegrees);
    public boolean isVisible(float x, float y);
    public boolean isVisible(float x, float y, float radius);
    public float visibility(float x, float y);
    public FloatArray polygon();           // x0, y0, x1, y1, ... around the viewer
}
public final class FogLayer implements LayerContent, Disposable {
    public FogLayer(Renderer renderer, VisionField field, String layer);
    public boolean enabled;
}
public final class Viewer { public float angle; }
public final class HiddenInFog { public float radius; }
public final class VisionSystem extends EntitySystem {
    public VisionSystem(World world, VisionField field, EventBus events);
}
public record CameIntoView(int entity) { }
public record WentOutOfView(int entity) { }
```

## Acceptance criteria
- With the defaults, the field matches Gold in Shadows' behaviour: the near circle, the cone in the aim direction, walls casting shadows, low props not blocking sight, and a peek into wall faces.
- Fog is drawn with pixelated, softly faded edges and hides `HiddenInFog` entities and marked enemy bullets outside the view.
- Runs at 60 fps on the API 23 emulator in Gold in Shadows' largest level.
- Headless tests cover the field (rays, occlusion, cone, soft edges, peek, opaque cells) and the system (hide/show, events).

## Tests
- `VisionField` on small grids:
  - an open room;
  - a wall shadow;
  - the cone direction;
  - the near circle behind a wall;
  - low props;
  - wall peek;
  - soft edge values.
- `VisionSystem`: hide and show, the events, no `Viewer`.
- `FogLayer`: a mask size test where possible headless; the drawing itself is checked by screenshots on desktop and the emulator.
