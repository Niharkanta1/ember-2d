# F-029 — Gold in Shadows demo

- Status: Ready (approved 2026-11-28)
- Phase: demos (after Phase 6)
- Module: `demos/demo-gold-in-shadows` (`core`, `desktop`, `android`), package `dev.ember2d.demo.gold`
- Depends on: kit-common, kit-topdown (shooter, steering, **vision F-030**), kit-bullets; F-027 (made with `newGame`), F-028 (asset tools)
- Tasks: I-600 to I-660
- Source: the developer's own libGDX game "GoldInShadows" (`D:\Coding\LibGDX\goldinshadows\gold`): its design, values, levels and art. The art and the game are the developer's, under their own licence; the developer asked for this port on 2026-11-28.

## Goal
Gold in Shadows rebuilt on Ember2D, played the same way: a dark dungeon top-down shooter where you see only your field of view, fight four creatures, collect gold, find each level's key and escape through the exit door, over a three-level campaign.

It also shows how a complete original game is moved onto Ember2D, and it is the best-documented demo:
- a README;
- a wiki game guide;
- a mapping from the original classes to Ember2D.

## Scope
**Presentation:**
- **Resolution:** 160 × 144 virtual pixels (the original's), integer-scaled. One tile is 16 pixels, so 1 world unit = 1 tile. The world is y-up (Ember2D), not the original's y-down; every value is converted once.
- **Palette:** the original's four-colour palette, and its pixel font (`fonts/pixel.fnt`) with the icon glyphs (coin, heart, skull, bullet, key) for the HUD.
- **Art:** the original sheets, unchanged: `Character-Sheet.png`, `Enemy-Sheet.png`, `TileSet-Sheet.png`, `Weapons.png`, `Muzzle.png`, `impact-bullet.png`, `particle.png`, `StartScreen.png`. Animation clip sets come from `makeClips` or hand-checked clip files (the sheets are grids).

**The player** (kit-topdown shooter):
- **Moving:** W A S D or the arrows. Movement speed and the camera follow keep the original's values.
- **Aiming:** the mouse on desktop; the right stick or the aim stick on Android and gamepads.
- **The rifle** (the original's): full-auto at 600 rounds per minute, 30 rounds, a 2-second reload. Bullets travel 300 px/s and deal 1 damage. There is a muzzle flash and bullet impacts. Fire is left mouse or J, reload is R.
- **Health:** hearts. Normal has 5 hearts (10 HP), Easy 6 hearts with half damage, Hard 4 hearts with 1.5× damage.
- **Being hit:** a screen flash, camera shake, knockback, the hit animation, and 0.8 s of blinking invulnerability.
- **Dying:** the death animation, then the death screen.

**Enemies** (one row of `Enemy-Sheet.png` each; the original's values):

| Enemy | Idle | Engaged | Attack | HP | Speed | Gold |
| --- | --- | --- | --- | --- | --- | --- |
| Rat | wanders | chases | bite, 1 damage, range 12 | 3 | 35 | 5 |
| Slug | wanders or patrols | chases slowly | spits a slow glob, range 72 | 8 | 16 | 15 |
| Ghoul | stands faded | wakes when close | dashes through you, 2 damage, range 48 | 5 | 42 | 20 |
| Cultist | wanders | keeps its distance and strafes | a fan of 3 bolts, range 100 | 6 | 28 | 25 |

- **Telegraphs:** every attack is telegraphed (squash, glow or shake).
- **Spotting:** an enemy that spots you shows "!" and alerts allies nearby.
- **Noise:** gunfire carries 110 px; enemies who hear it come to look ("?").
- **Hurt and death:** hurt enemies flash and show a health bar; dead enemies fade out and drop a gold pouch.
- **Patrols:** a level's `patrol` routes become Tiled polylines on the enemy object.
- **Built from:** kit-topdown `Steering` (A* around walls), kit-bullets patterns for spit and bolts, kit-common `Health`/`Hitbox`/`Team`/`HitFlash`, and demo components (`Creature`, `Perception`, `Alert`, `MeleeBite`, `Dash`) with their systems.

**The world:**
- **Field of view:** F-030 with the original's settings. Enemies, pick-ups and enemy bullets are hidden outside the view.
- **Gold:** coins (+10) and gold piles (+50), plus enemy pouches. Gold drifts toward you when close, and every item can be collected once.
- **Medkits:** heal 2 hearts; left in place at full health.
- **Chests:** E (or a pad button) opens one: 4 coins and a pouch spill out.
- **Key and exit:** the key unlocks the exit door (two tiles in a north wall); walking through it ends the level.
- **Spike traps:** cycle up and down in waves and hurt while up.
- **Props:** barrels, crates, vases, a sack and pillars. All block movement; pillars also block sight.
- **Score:** kept across the campaign's levels; the best score is saved.

**Levels:**
- **Conversion:** the three campaign levels (and the original room) are converted once to Tiled maps by `assets-src/demo-gold-in-shadows/ConvertLevels.java`.
  - It runs the original's `AsciiMapCompiler`, so the auto-tiling (brick faces, caps, thin wall overlays, decorative variation from the seed) is identical.
  - It writes `.tmx` files with tile layers, `solid` and `opaque` tile properties, and objects for every spawn (prefab classes), with patrol routes as properties.
- **Afterwards:** the maps are edited in Tiled. A test checks that every pick-up, chest, enemy and the exit can be reached from the start (the original's `CampaignLevelsTest`).

**Screens and settings:**
- **Title:** over `StartScreen.png`, with New game, Continue (the next level of a campaign in progress), Settings and Quit.
- **Settings:** difficulty, music and sound volumes, Controls (`RebindMenu`), fullscreen, vsync and the FPS counter (desktop).
- **In play:** a pause menu; a level-complete screen with gold, kills and time; the death screen; and the campaign-end screen with the total score.
- **Transitions:** fades between screens.
- **HUD:** hearts, gold, ammunition with a reload indicator, and the key.

**Audio:** the original has none. Short original effects (shot, hit, pick-up, chest, door, spikes, enemy alerts) and one music loop are generated by a script (`assets-src/demo-gold-in-shadows/sound/make_sounds.py`), like the other demos.

**Platforms:**
- **Desktop:** mouse and keyboard, and gamepad.
- **Android:** twin-stick on-screen controls (move stick, aim stick that fires past its edge), plus reload and chest buttons, and gamepad. Checked on the API 23 emulator (debug and release).

**Documentation:**
- `demos/demo-gold-in-shadows/README.md`: how to play, how it is built, and a table mapping every original class to its Ember2D counterpart.
- A wiki game guide `Guide-Gold-in-Shadows`.
- Javadoc on every class.
- `ART-CREDITS.txt` naming the developer as the author and owner of the art.

## Non-goals
- The original's own engine code (its animation player, tweens, navigation and collision); Ember2D's are used.
- Godot-exact physics (box shapes and Ember2D collision replace the original's).
- New content beyond the original's three levels and four creatures.
- Changes to the original project; it is only read.

## API sketch (demo classes)
```java
package dev.ember2d.demo.gold;
public final class GoldGame { static EngineConfig config(boolean devMode, String version); }   // 160x144
final class GoldComponents implements ComponentSetup { }   // kits + demo components
final class TitleScene, LevelScene, PauseMenu, EndScene, SettingsMenu;
public final class Creature { public String kind; public int gold; public String state; ... }   // per enemy
public final class Perception { public float sight, hearing; public boolean alerted; }
public final class Pickup { public String kind; public int amount; }   // coin, pile, pouch, medkit, key
public final class Chest { public boolean open; }
public final class SpikeTrap { public float period, offset; public boolean up; }
public final class ExitDoor { public boolean locked = true; }
final class CreatureSystem, PerceptionSystem, PickupSystem, ChestSystem, SpikeSystem, ExitSystem, Hud;
final class Campaign { /* levels, score, saves */ }
```

## Acceptance criteria
- All three levels play from start to exit on desktop and Android; the reachability test passes for each.
- The creatures behave as in the table, including telegraphs, alerts, noise and drops.
- Field of view and fog match the original's look (side-by-side screenshots in the README).
- Difficulty, settings, rebinding, saves (continue, best score) and the gamepad work.
- `./gradlew build` passes, with the demo's tests (data, levels, systems) and the architecture rules.
- Debug and release APKs run on the emulator.

## Tests
- Data:
  - every prefab and clip set loads;
  - creature values match the table;
  - difficulty values.
- Levels: the converted maps parse, spawns are in place, and everything is reachable.
- Systems (headless worlds):
  - pick-ups, chests, key and door, spikes;
  - perception, alerts and noise;
  - each attack (bite, spit, dash, bolt fan);
  - score across levels.
- Manual: desktop and emulator play-throughs, screenshots in the README.
