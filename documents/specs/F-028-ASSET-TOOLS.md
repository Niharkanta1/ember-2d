# F-028 — Asset tools: manifest and clip generation

- Status: Ready (written in I-590, 2026-11-28: the developer asked how to make the manifest and clip files without writing them by hand, as part of the I-550 editor review)
- Phase: 6 (tooling)
- Module: `tools/asset-tools` (build-time tool; not part of any game)
- Depends on: F-008 (manifest), F-007 (clip sets), F-027 (game layout)
- Tasks: I-590

## Goal
Remove the two most repetitive hand-written files:
- `manifest.json`: every asset needs an entry.
- `*.clips.json`: every animation needs frames, sizes and timings.

Both are generated from the files themselves. The output is the same format the engine reads, so a generated file can still be edited by hand.

## Scope
**Commands** (from the repository root; `<game>` is the folder under `demos/`):
```
./gradlew assetManifest -Pgame=<game> [-Pprune=true]
./gradlew makeClips -Pgame=<game> -Pfrom=<path in assets> [-Pname=<set name>] [-Pframe=WxH] [-Pduration=0.1]
```

### `assetManifest`
- Scans `demos/<game>/core/assets` and updates `manifest.json`. It creates the file if it is missing.
- **Existing entries are kept unchanged** (ID, type, group, options), matched by path. New files are appended, and files that are gone are reported.
- With `-Pprune=true`, entries for missing files are removed.
- The file is rewritten only when something changes. The tool prints what was added, missing and removed.

**Type rules** (first match wins):

| File | Type | ID |
| --- | --- | --- |
| `prefabs/**.json` | `prefab` | the path under `prefabs/` without `.json` (`enemy/slime`) |
| `*.clips.json` | `clips` | `anim.<name>` |
| `*.asm.json` | `asm` | `asm.<name>` |
| `*.json` with an `.atlas` of the same name next to it | `skin` | `skin.<name>` |
| `*.json` under `i18n/`, `strings/` or `lang/` | `json` | `strings.<language>` (F-025) |
| other `*.json` (including `*.ink.json` and Aseprite exports) | `json` | see the ID rule |
| `*.tmx`, `*.tmj` | `map`, in a group named after the map | `map.<name>` |
| `*.atlas` | `atlas` | `atlas.<name>` |
| `*.p` | `particle` | `fx.<name>` |
| `*.ttf`, `*.otf` | `freetype` (size 16; edit the options) | `font.<name>` |
| `*.fnt` | `font` | `font.<name>` |
| `*.wav`, `*.ogg`, `*.mp3` under a `music` folder | `music` | `music.<name>` |
| other `*.wav`, `*.ogg`, `*.mp3` | `sound` | `sfx.<name>`, or `<folder>.<name>` outside `sfx/` |
| `*.png`, `*.jpg` | `texture` | `tex.<name>` under `gfx/`, else `<folder>.<name>` |

**Skipped:**
- images an atlas, a particle effect, a Tiled map or tileset, or a skin already loads (atlas pages, effect images, tileset and image-layer images);
- `*.tsx` (loaded with their maps);
- text and licence files (`*.txt`, `*.md`);
- the manifest itself;
- dot files.

**ID rule** (outside the special cases above):
- The prefix is the top folder in the singular: `maps` → `map`, `weapons` → `weapon`, `patterns` → `pattern`. `gfx` → `tex`.
- The name is the rest of the path, with `/` and spaces turned into `_`, a `(WxH)` frame size and the extensions removed, other punctuation except `.` and `-` dropped and lower-cased: `gfx/frog/idle.png` → `tex.frog_idle`, `gfx/frog/Run (32x32).png` → `tex.frog_run`.
- A file at the top of `assets/` uses its name alone (`input.json` → `input`).
- A `.ink.json` story under `ink/` → `ink.<name>` (`ink/town.de.ink.json` → `ink.town.de`, the translated story's ID in F-025).

These are the demos' own conventions. Running the tool on any of the four demos changes nothing (tested).

**Groups:** maps get their own group; everything else `global`.

**Checks:**
- An ID that is already used gets `_2`, `_3` and so on, and a warning.
- A missing assets folder is an error.
- Nothing is written when the existing `manifest.json` cannot be parsed.

### `makeClips`
`-Pfrom` is either a folder of images or an Aseprite JSON export.

**From a folder of images (sprite strips):**
- Each `*.png` in the folder (not in subfolders) becomes one clip, sorted by name.
- **Clip name:** the file name, lower-cased, with a `(…)` size part removed and spaces and `-` turned into `_`: `Double Jump (32x32).png` → `double_jump`.
- **Frame size**, in order:
  1. a `(WxH)` in the file name;
  2. `-Pframe=WxH`;
  3. square frames of the image's height.

  An image whose width or height is not a whole number of frames is refused.
- Every frame is used (`frames: [0, n-1]`); the frame duration is `-Pduration` (default 0.1 s); the mode is `loop`.
- The images are added to the manifest first (the same rules as `assetManifest`), and each clip refers to its image's ID.

**From an Aseprite export** (`-Pfrom=anim/hero.json`, exported with frame tags: `aseprite -b hero.aseprite --sheet hero.png --data hero.json --format json-array --list-tags`):
- One clip per frame tag, named after the tag.
- Each clip refers to the export and its image (`aseprite`, `texture`, `tag`); the engine reads frame durations, direction, repeat and frame events from the export.
- The export and its image (`meta.image`) are added to the manifest first.

**Output:** `anim/<name>.clips.json`.
- `<name>` is `-Pname`, else the folder's or export's name, lower-cased.
- The output is added to the manifest as `anim.<name>`.
- An existing clips file is refused, so hand edits are never overwritten. Delete it, or pass another `-Pname`.

## Non-goals
- Packing atlases (use TexturePacker or Free Texture Packer with the libGDX format, or keep separate images).
- Writing state machines (`*.asm.json`), prefabs or particle effects. Those workflows are documented in the wiki page "Working Without an Editor".
- Watching files; run the command again after adding files.

## API sketch
```java
package dev.ember2d.tools.assets;
public final class ManifestTool {
    public record Result(List<String> added, List<String> missing, List<String> removed, boolean written) { }
    public static Result update(Path assetsDir, boolean prune) throws IOException;
}
public final class ClipsTool {
    public record Result(Path file, List<String> clips) { }
    public static Result fromFolder(Path assetsDir, String folder, String name, int[] frame, float duration) throws IOException;
    public static Result fromAseprite(Path assetsDir, String exportPath, String name) throws IOException;
}
public final class AssetTools { public static void main(String[] args); }   // manifest|clips --assets= ...
```

## Acceptance criteria
- On each demo, `assetManifest` reports no added, missing or removed entries, and leaves `manifest.json` byte-identical.
- On a new folder of assets it writes entries that load: the generated game's tests and a desktop run pass.
- `makeClips` on the platformer's frog strips writes the same frames and sizes as the demo's hand-written `frog.clips.json`; on an Aseprite export it writes one clip per tag. Generated clip sets load and play in a game (checked by hand in I-590).

## Tests
- Type and ID rules, skipped files, duplicates, pruning, unchanged files on the four demos.
- Clips from strips (file-name size, square frames, `-Pframe`, bad sizes, refusing to overwrite) and from an Aseprite export (one clip per tag), on copies of the demos' and engine tests' real files.
