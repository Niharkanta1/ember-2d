# F-026 — Sprite materials (hit flash, outline, palette swap)

- Status: Ready (written and approved in I-520, 2026-11-27, under the developer's standing approval for Phases 3 to 6)
- Phase: 6
- Module / package: `ember-core` / `ember.render` (with the shader in `ember.render.internal`); `kit-common` for the hit flash
- Depends on: F-004 (rendering), F-008 (assets), F-021 (`Damaged`)
- Tasks: I-520

## Goal
The three sprite effects 2D games ask for most, without writing GLSL:
- a **hit flash** (the sprite turns white for a moment);
- an **outline** (a selected or focused object);
- a **palette swap** (enemy variants and team colours from one sprite sheet).

They run on GLES 2 (Android API 23) and desktop GL with one built-in shader.

## Scope
**Sprite fields** (all off by default; a sprite with none set draws exactly as before, with the default shader):
- `flash` (0..1) and `flashColor` (white): mixes the sprite's colour towards `flashColor` by `flash`.
- `outline` (a `Color`; alpha 0 means none): draws a 1-pixel border in that colour on the fully transparent pixels next to an opaque one (alpha at least 0.5). Semi-transparent pixels such as shadows are left alone.
- `paletteId` (null for none): a texture asset of up to 16 × 2 pixels. Row 0 holds the source colours, row 1 the colours they become. Pixels of other colours are unchanged.

**The shader:** one built-in fragment shader (GLSL ES 1.00) does all three: the palette lookup first, then the outline test (on the texture's own alpha), then the tint and the flash.

**The renderer:**
- `SpriteRenderSystem` uses the material shader only for sprites with an effect.
- It sets the shader's values per sprite and flushes the batch around them. Sprites without effects keep batching as before.
- The shader is created on first use. If it fails to compile (an old driver), the failure is logged once and the effects are skipped; sprites still draw.
- **`SpriteMaterials`** sits between the two, so the render system is tested headless with a recording implementation, and renderers without shaders return `SpriteMaterials.NONE`.

**The hit flash in `kit-common`:** a `HitFlash` component (`seconds` 0.1, `color` white) and a `HitFlashSystem`. On `Damaged`, the system sets the target's `Sprite.flash` to 1, holds it for `seconds`, then sets it back to 0.

## Non-goals
- Custom user shaders and post-processing (a later spec).
- Lighting and normal maps.
- Outlines wider than 1 pixel.
- Palette swaps for more than 16 colours.

## API sketch
```java
// ember.render.Sprite (new fields)
public float flash;                                  // 0..1
public final Color flashColor = new Color(Color.WHITE);
public final Color outline = new Color(0, 0, 0, 0);  // alpha 0: no outline
public String paletteId;                             // texture asset, up to 16 x 2

// ember.render: how SpriteRenderSystem reaches the shader (the engine's GdxRenderer provides it)
@PublicApi public interface SpriteMaterials {
    boolean begin(Batch batch, Sprite sprite, TextureRegion region, Texture palette);  // false: draw plainly
    void end(Batch batch);
}
Renderer.materials();                                 // the built-in implementation

// kit-common
@PublicApi public final class HitFlash { public float seconds = 0.1f; public final Color color = new Color(Color.WHITE); }
@PublicApi public final class HitFlashSystem extends EntitySystem { public HitFlashSystem(World world, EventBus events); }
```

## Rules and limits
- **Outline margin:** the outline is drawn inside the sprite's quad, so the region needs a 1-pixel transparent margin where the outline should show. Samples outside the region count as transparent, so neighbours in an atlas never bleed in.
- **Palette matching:** the match is exact within a small tolerance (0.01 per channel), so the palette must use the sheet's exact colours.
- **Cost:** each sprite with an effect costs a batch flush. That is fine for a few dozen at a time; it is not meant for every sprite in a bullet hell.

## Errors
- An unknown `paletteId` fails like any unknown asset ID (the error names the closest IDs); a palette whose file is missing gets the magenta fallback and a warning. A palette wider than 16 pixels uses its first 16 columns and logs a warning.
- `flash` outside 0..1 is clamped.

## Acceptance criteria
- Sprites without effects draw exactly as before, with no shader switches.
- A sprite with an effect switches to the material shader, gets its values (flash, outline, texel size, region bounds, palette), and switches back.
- The hit flash turns on with `Damaged` and off after `seconds`.
- On the emulator (GLES 2): a hit flash in the shooter, an outline on the RPG's focused object, and a palette-swapped enemy variant in the shooter.

## Tests (headless)
- The render system with a recording materials object: no calls for plain sprites; begin and end around effect sprites; the order of sprites is kept.
- The uniform values the built-in materials compute (texel size, region bounds, palette count) from a sprite and its region, without GL.
- `HitFlashSystem` timing.
- The shader source compiles on a real GL context: checked on the emulator, not in CI.

## Decisions in I-520
- **`Renderer.materials()` is a default method** returning `SpriteMaterials.NONE`, so other renderers need no change; `GdxRenderer` returns the built-in `MaterialShader`.
- **The outline rule (found on the emulator):** the first version outlined every pixel below 50 % alpha next to a visible one, which painted the goblin's semi-transparent shadow (alpha 90) yellow. The outline now uses the texture's own alpha: only fully transparent pixels next to a pixel of at least 50 % alpha.
- **The palette lookup** divides by the palette texture's width, so a palette wider than 16 pixels still finds its first 16 colours at the right places.
- **Demos:**
  - Shooter: every enemy and the boss have `HitFlash`. A red slime (`enemy/slime_red`, 6 HP) uses `tex.palette.red_slime`, made by `make_assets.py` from the slime's own four colours shifted from green to red. Wave 3 spawns it.
  - RPG: the focused interactable gets a yellow outline.
- **Checked on the emulator** (API 23, GLES 2, debug build):
  - The shader compiled (no error logged).
  - The boss turned fully white while being hit.
  - The elder got a clean 1-pixel outline after the fix.
  - Red slimes appeared next to green ones. To reach them, a local build set wave 3's delay to 1 s; that change was not committed.
- **Not checked:** desktop GL by hand (the desktop build compiles; the shader was not run there), a real phone, a release (R8) build.
