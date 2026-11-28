# 06 — Sprite materials: hit flash, outline, palette swap

This chapter uses the three built-in sprite effects of F-026 (I-520): a **hit flash**, a **1-pixel outline** and a **palette swap**. They need no GLSL from you. Examples:
- the shooter demo: enemies flash when hit, and red slimes are palette-swapped green slimes;
- the RPG demo: whatever the player can interact with gets an outline.

> Status: draft for the developer's review at the Phase 6 gate. Not approved yet.

## 1. The fields
They are on `Sprite` and can be set in prefabs, Tiled object properties, code or the dev tools' inspector:

| Field | Meaning | Off |
| --- | --- | --- |
| `flash` (0..1) and `flashColor` | mix the sprite towards a colour | `flash = 0` |
| `outline` (a colour) | a 1-pixel outline on transparent pixels next to opaque ones | alpha 0 |
| `paletteId` | swap colours through a palette texture | `null` |

A sprite with none of them set draws exactly as before and keeps batching.

## 2. Hit flash
Add `HitFlash` (kit-common) to anything that can be hurt, and its system once per scene:
```json
"HitFlash": { "seconds": 0.06 }
```
```java
world.addSystem(new HitFlashSystem(world, context.events()));
```
On every `Damaged` event, the target flashes white (`HitFlash.color`) for `seconds` of game time. That is all the shooter does for its enemies and boss.

## 3. Outline
```java
sprite.outline.set(1f, 0.85f, 0.3f, 1f);   // on (yellow)
sprite.outline.a = 0f;                     // off
```
The RPG demo outlines the focused interactable:
```java
context.events().on(InteractionFocus.class, e -> {
    outline(focused, false);
    focused = e.target();
    outline(focused, true);
});
```
- **Margin:** the outline is drawn inside the sprite's own rectangle, so the image needs a transparent 1-pixel border where the outline should show. Most asset packs have one.
- **Shadows:** pixels that are already semi-transparent keep their look.

## 4. Palette swap
Enemy variants, team colours and "hurt" palettes can come from one sprite sheet.
1. **Make the palette:** a texture of at most 16 × 2 pixels.
   - **Row 0:** the exact colours used in the sheet.
   - **Row 1:** what each becomes.
   - Colours not in row 0 stay as they are.
2. **Register it:** put it in `gfx/` and run `assetManifest`. A file `gfx/palette_red_slime.png` gets the ID `tex.palette_red_slime`. The shooter's entry predates the tool and is called `tex.palette.red_slime`; use whatever ID your manifest has.
3. **Use it in the variant's prefab:**
   ```json
   "Sprite": { "regionId": "atlas.shooter/slime", "origin": [8, 0], "paletteId": "tex.palette_red_slime" }
   ```

The shooter builds its red palette in `assets-src/demo-shooter/make_assets.py` from the slime's own colours, by turning their hue.

## 5. Cost and limits
- **Cost:** every sprite with an effect costs one batch flush. Dozens at a time are fine; don't give every bullet an effect.
- **Shader failure:** the shader is compiled on first use. If a driver rejects it, that is logged once and sprites draw without effects; the game keeps running.
- **Limits:** outlines are 1 pixel and palettes have up to 16 colours. Custom shaders and post-processing are not part of F-026.
- **Checked:** on the Android API 23 emulator (GLES 2.0), all three effects.

## 6. Checklist
- [ ] `HitFlashSystem` added to scenes with `HitFlash` entities.
- [ ] Outlined images have a transparent border.
- [ ] Palette row 0 uses the sheet's exact colours.

Reference: wiki page Sprite Materials; spec `specs/F-026-MATERIALS.md`.
