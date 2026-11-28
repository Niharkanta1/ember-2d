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
- **Construction:** `new VisionField(collision, sightMask, settings)`.
- **Update:** `update(x, y, aimDegrees)` casts the rays through Ember2D's `Collision`, against non-trigger colliders and solid tile grids on the `sightMask` layers, and stores each ray's length plus `wallPeek`.
- **What blocks sight:** a game decides per wall by its collision layer. In Gold in Shadows, walls that block sight are on layer 3 and low props (barrels, crates, chests) on layer 0, so props block movement but not sight. This uses the collision index the game already has, and it follows the original's exact wall boxes instead of whole cells.
- **Queries:**
  - `isVisible(x, y)` and `isVisible(x, y, radius)`: the original's rule (any part of the circle in range, its centre not behind a wall);
  - `visibility(x, y)` (0..1, with the soft edge);
  - `rangeAt(angle)`;
  - per-ray `distance(i)` and directions.
- **`enabled = false`** shows everything.

**`FogLayer`** (`LayerContent` in a render layer above the world):
- **No frame buffer:** the fog is geometry drawn in the world pass. Per ray, a band fades from clear to `darkness` over the last `edgeSoftness` of the ray, then solid fog reaches past the edge of the view. It draws through the batch with per-vertex colours and a 1×1 white managed texture.
- **Pixelation:** in the pixel-perfect render modes the world pass is drawn at the virtual resolution, so the fog edge is pixelated like the original's low-resolution mask.
- **Why no frame buffer:** libGDX 1.14 frame buffers do not nest, so a mask frame buffer would break Ember2D's own pixel-perfect pass. Geometry also needs no recreation after an Android context loss.
- **Off:** `enabled = false` draws nothing.
- **Disposal:** dispose it with the scene.

**`Viewer`** (component: `angle`, `eyeY`) and **`VisionSystem`** (LATE phase, after movement and aiming):
- **Updating the field:** from the entity that has `Viewer`. It uses its `Aim.angle` if it has kit-topdown's `Aim`, else `Viewer.angle`.
- **`HiddenInFog`** (component: `radius`, `centerY`): the system sets the entity's `Sprite.visible` (a new engine field; `SpriteRenderSystem` skips hidden sprites), and queues `CameIntoView(entity)` and `WentOutOfView(entity)` when that changes. The first state sends no event.
- **Without a viewer:** nothing is hidden.

**`VisionKit.register(registry)`** registers `Viewer` and `HiddenInFog`. It is a separate kit call, so games that don't use vision keep their component lists.

**Enemy bullets in the dark:** a visibility hook in kit-bullets, added with the creatures' bullets in I-630.

**Android:** plain batch geometry; no extensions, no frame buffer.

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
    public VisionField(Collision collision, int sightMask, VisionSettings settings);
    public void update(float x, float y, float aimDegrees);
    public boolean isVisible(float x, float y);
    public boolean isVisible(float x, float y, float radius);
    public float visibility(float x, float y);
    public float rangeAt(float angleRadians);
    public float distance(int ray); public float directionX(int ray); public float directionY(int ray);
}
public final class FogLayer implements LayerContent, Disposable {
    public FogLayer(VisionField field, CameraRig camera, String layer);
    public boolean enabled;
}
public final class Viewer { public float angle, eyeY; }
public final class HiddenInFog { public float radius, centerY; }
public final class VisionKit { public static void register(ComponentRegistry registry); }
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
- `VisionField` with real `Collision` worlds:
  - an open room (circle and cone);
  - smooth cone sides;
  - wall shadows and the peek;
  - low props on another layer;
  - solid tile grids;
  - soft edge values;
  - disabled;
  - settings validation.
- `VisionSystem`: hide and show, the events, no `Viewer`; `VisionKit` registration.
- `FogLayer`: checked by screenshots (Gold in Shadows level 1 on desktop in I-610; the emulator in I-650).
