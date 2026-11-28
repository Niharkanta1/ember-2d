# 06 — Project and asset tools

Ember2D has no standalone editor (ADR-009, confirmed in I-550). This chapter shows the generator commands that replace an editor's tedious parts:
- `newGame` (F-027, I-580): a new project;
- `assetManifest` and `makeClips` (F-028, I-590): the manifest and animation clips;
- `tiledTypes` (F-009): prefab placement in Tiled.

The wiki page "Working Without an Editor" covers every other job (prefabs, enemies, cameras, particles) in the same way.

> Status: draft for the developer's review at the Phase 6 gate. Not approved yet.

## 1. A new game in one command
```sh
./gradlew newGame -PgameName=forest-run -PgamePackage=com.example.forest -PgameTitle="Forest Run" -Ptemplate=blank
```
- **What it creates:** `demos/forest-run/core`, `desktop` and `android`, in your package, with the classes `ForestRunGame` (the config) and `ForestRunComponents` (the component setup). It adds the three `include` lines to `settings.gradle`.
- **Templates:**
  - `blank`: one scene that shows the title, a font, input bindings (with gamepad), an architecture test and a `tiledTypes` task.
  - `platformer`, `shooter`, `rpg`, `novel`: a full renamed copy of that demo.
- **Checks first:** bad names, bad packages, a title with quotes and an existing folder are all reported together, and nothing is written.
- **Next:**
  ```sh
  ./gradlew :demos:forest-run:desktop:run
  ./gradlew :demos:forest-run:android:installDebug
  ```

## 2. The manifest without typing
Drop files into `core/assets/` (images, sounds, maps, prefabs, fonts, effects) and run:
```sh
./gradlew assetManifest -Pgame=forest-run
```
```text
added    tex.hero_run (gfx/hero/Run (32x32).png, texture)
added    sfx.jump (sfx/jump.wav, sound)
added    map.level1 (maps/level1.tmx, map)
manifest.json updated
```
- **Your edits are kept:** existing entries (IDs, groups, font options) are never changed.
- **Types and IDs:** they follow the demos' conventions. Running the tool on any demo changes nothing; that is a test.
- **Left out:** images that an atlas, an effect or a Tiled map already loads.
- **Missing files:** they are reported; `-Pprune=true` removes their entries.

## 3. Animation clips without typing
From sprite strips (one image per animation, as asset packs ship them):
```sh
./gradlew makeClips -Pgame=forest-run -Pfrom=gfx/hero -Pduration=0.08
```
- **Clips:** every image in the folder becomes a clip. `Run (32x32).png` becomes `run`, with 32 × 32 frames.
- **Frame size:** from the file name, else `-Pframe=WxH`, else square frames.
- **Output:** `anim/hero.clips.json`, registered as `anim.hero`.

From Aseprite, export a sheet with JSON data and tags, then:
```sh
./gradlew makeClips -Pgame=forest-run -Pfrom=anim/hero.json
```
This makes one clip per tag. Frame times, direction, play-once and frame events are read from the export, so later re-exports need no new command.

What's left by hand is optional: `"mode": "once"` for a death clip, or footstep events.

## 4. Prefabs in Tiled
```sh
./gradlew :demos:forest-run:core:tiledTypes
```
- **What it writes:** `assets/maps/forest-run.tiled-project` (in games made from the blank template) with every registered component and prefab.
- **In Tiled:** open the project, and an object's **Class** offers your prefabs, with their fields as properties to override per object.
- **When to rerun:** after adding a component or prefab.

## 5. A worked example
Going from nothing to an animated hero with these tools:
1. `newGame -PgameName=forest-run -PgamePackage=com.example.forest`.
2. Copy `Idle (32x32).png` and `Run (32x32).png` into `demos/forest-run/core/assets/gfx/hero/`.
3. `makeClips -Pgame=forest-run -Pfrom=gfx/hero`.
4. Give an entity an `Animator` (`clipSet` `anim.hero`, `clip` `run`), a `Sprite` and a `Transform`, and add `AnimationSystem` and `SpriteRenderSystem` to the scene.

This exact flow was checked in I-590 with the Pixel Adventure frog strips: five animated frogs, each playing a generated clip.

## 6. Still by hand
These are short, validated when they load (errors name the file and field), and hot reloaded in dev runs:
- animation state machines (`*.asm.json`);
- bullet patterns;
- `input.json`;
- prefabs not made with the inspector's **Copy as prefab JSON**.

Reference: specs `specs/F-027-PROJECT-GENERATOR.md`, `specs/F-028-ASSET-TOOLS.md`; wiki pages "Creating a New Game Project" and "Working Without an Editor".
