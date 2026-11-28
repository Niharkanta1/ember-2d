# Working Without an Editor

Ember2D has no standalone editor (decision ADR-009, confirmed in I-550). Instead, every job an editor would do is covered by one of these:
- an **external tool** (Tiled, Aseprite, a particle editor);
- a **generator command** (`newGame`, `assetManifest`, `makeClips`, `tiledTypes`);
- the **in-game dev tools** (F1 in a desktop dev run);
- **a little code**, for game logic.

This page goes through the common jobs, says which way is meant for each, and is honest about what is still hand work.

| Job | Without writing files by hand | Section |
| --- | --- | --- |
| Start a game project | `./gradlew newGame` | [1](#1-projects) |
| Make prefabs (hazards, traps, platforms, pickups) | dev tools → **Copy as prefab JSON**; Tiled per-object overrides | [2](#2-prefabs-hazards-traps-platforms) |
| New enemy types with their own logic | a component, a system and a prefab | [3](#3-custom-enemies-and-behaviour) |
| `manifest.json` | `./gradlew assetManifest` | [4](#4-the-asset-manifest) |
| Animation clips | `./gradlew makeClips` from sprite strips or an Aseprite export | [5](#5-animation-clips) |
| Camera moves and transitions | `CameraFollow`, `CameraRig`, tweens | [6](#6-cameras-and-camera-transitions) |
| Particle effects | GDX Particle Editor | [7](#7-particle-effects) |
| Maps, atlases, fonts, sounds, UI skins, dialogue | Tiled, a texture packer, `assetManifest`, Skin Composer, Ink | [8](#8-everything-else) |

---

## 1. Projects
**Where games live:** inside the Ember2D repository, under `demos/<game>/`. The engine is not published as a library yet, and the build only accepts game modules there. The folder is called `demos` because the four demos live there too. Your games sit next to them and are built and tested the same way.

**Create one:**
```sh
./gradlew newGame -PgameName=forest-run -PgamePackage=com.example.forest -Ptemplate=platformer
```
This creates `demos/forest-run/{core,desktop,android}` and registers it ([Creating a New Game Project](Creating-a-New-Game-Project)).

**Managing several games:**

| Setup | How | Good for |
| --- | --- | --- |
| Several games in one copy of Ember2D | run `newGame` once per game; each is its own `demos/<game>` with its own package, `applicationId` and saves | a solo developer or studio with a few games that should all get engine updates at once |
| One copy (fork) of Ember2D per game | fork or clone the repository per game, run `newGame` in it, and delete the other demos from `settings.gradle` if you want a smaller build | games that need their own history, release cycle or team; pull engine updates from the main repository with `git merge` |
| Ember2D as a library | not available yet | separate repositories that depend on a published version; a separate decision, not taken |

**Build only one game:** `./gradlew :demos:forest-run:desktop:run`, `:demos:forest-run:android:assembleDebug` and `:demos:forest-run:core:test` build only that game and the engine modules it uses. `./gradlew build` builds everything.

**Remove a game:** delete `demos/<game>` and its three `include` lines in `settings.gradle`.

## 2. Prefabs (hazards, traps, platforms)
A prefab is a JSON file in `assets/prefabs/` listing an entity's components and their values ([Prefabs and Data](Prefabs-and-Data)). Most hazards, traps and platforms need **no new code**: they combine existing components.

| Thing | Components (all existing) |
| --- | --- |
| Spikes, saws, lava | `Sprite`, `Collider`, `Hitbox` (damage, knockback), `Team` (kit-common); the platformer demo uses its own small `Hazard` component instead |
| Moving platform | `Sprite`, `Collider`, `Solid`, `MovingPlatform` (kit-platformer) |
| One-way platform | `Solid` with `oneWay` |
| Pickup | `Sprite`, `Collider` (trigger), your `Pickup` component |
| Spawner | `Spawner` (kit-common): what to spawn, how often, how many |
| Fire burst or turret | `Sprite`, `Gunner`-style component + a bullet pattern (kit-bullets) |

**Workflow 1: build it in the running game (no JSON typing).**
1. Start a dev run: `./gradlew :demos:<game>:desktop:run`.
2. Spawn or select a similar entity, then press **F1** to open the **Inspector**.
3. Change the values live: hit box, damage, speed, colours, flash.
4. Press **Copy as prefab JSON** and paste it into a new file, `assets/prefabs/hazard/saw.json`. Only the values that differ from the defaults are written.
5. Run `./gradlew assetManifest -Pgame=<game>`. The prefab is registered as `hazard/saw` (section 4).

**Workflow 2: copy and change.** Copy the closest prefab file and edit a few values. Prefabs are short: the demos' prefabs are 10–40 lines.

**Variants in the map, not in files.** In Tiled, an object's **Class** is the prefab ID, and its **Custom Properties** override fields for that one object (`Health.max = 6`, `MovingPlatform.speed = 3`). For Tiled to offer the prefabs and their fields in drop-downs, generate the types once:
```sh
./gradlew :demos:<game>:core:tiledTypes        # writes assets/maps/<game>.tiled-project
```
Open that `.tiled-project` in Tiled (File → Open File or Project). Run it again after adding a component or prefab. Games made with `newGame` have this task (the `platformer` template writes `platformer.tiled-project`).

**Hot reload:** saving a prefab file in a dev run changes the entities spawned afterwards.

## 3. Custom enemies and behaviour
**New behaviour is code: a component (data) plus a system (logic).** Everything else (look, collision, health, damage, team, AI steering) comes from existing components in the prefab. This is the shooter demo's turret enemy, shortened:

```java
// core/src/main/java/com/example/forest/Gunner.java: the data, editable in prefabs, Tiled and the inspector
public final class Gunner {
    public String pattern = "";      // a bullet pattern asset ID
    public float interval = 3f;      // seconds between volleys
    public float wait = -1f;         // runtime state
}

// GunnerSystem.java: the logic, run every frame for entities that have Gunner and Transform
final class GunnerSystem extends EntitySystem {
    GunnerSystem(World world, BulletSystem bullets, int player) {
        super(Phase.UPDATE, world.family(Gunner.class, Transform.class));
        ...
    }
    @Override
    protected void process(int entity, float delta) {
        Gunner g = world().get(entity, Gunner.class);
        g.wait -= delta;
        if (g.wait > 0f) return;
        g.wait = g.interval;
        Transform t = world().get(entity, Transform.class);
        bullets.runAt(g.pattern, t.position.x, t.position.y + 0.5f, player, 2);
    }
}
```
Then:
1. **Register the component** in your `ComponentSetup`, so prefabs, Tiled and R8 know it:
   ```java
   registry.register(Gunner.class, Gunner::new);
   ```
2. **Add the system** in your scene's `enter`: `world.addSystem(new GunnerSystem(world, bullets, player));`.
3. **Write the prefab** (section 2), combining `Gunner` with the existing parts: `Sprite`, `Collider`, `Health`, `Hitbox`, `Hurtbox`, `Team`, `Steering`, `HitFlash`.
4. Run `assetManifest` and `tiledTypes`, then place the enemy in Tiled.

**What you rarely need to write:**

| Behaviour | Ready-made |
| --- | --- |
| Walk back and forth, turn at edges | `Patroller` (kit-platformer) |
| Chase, flee, wander, follow a path around walls | `Steering` + `TopDownBody` (kit-topdown, gdx-ai A*) |
| Contact damage, knockback, invulnerability | `Hitbox`, `Hurtbox`, `Health` (kit-common) |
| Bullet patterns, waves, bosses | kit-bullets patterns (JSON) |
| Animations by state (idle, run, hit) | an animation state machine (`*.asm.json`) |
| Flash when hit | `HitFlash` (kit-common) |
| Dying, dropping loot, score | the `Died` event + your own small handler |

Different enemy **types** that share logic are just different prefabs (values), not different code. A slime and a red slime are the same components with other numbers. See [Entity Component System](Entity-Component-System) for systems, phases and families, and the genre guides for complete examples.

## 4. The asset manifest
**Don't write `manifest.json` by hand:**
```sh
./gradlew assetManifest -Pgame=<game>                   # add new files
./gradlew assetManifest -Pgame=<game> -Pprune=true      # also remove entries whose file is gone
```
Drop files into `demos/<game>/core/assets/` and run it. It prints what it added:
```text
added    tex.frog_run (gfx/frog/Run (32x32).png, texture)
added    sfx.jump (sfx/jump.wav, sound)
added    map.level1 (maps/level1.tmx, map)
manifest.json updated
```
- **Your entries are never changed.** Entries already in the file keep their ID, type, group and options (font sizes, for example). Only new files are added.
- **Types come from the file and folder:**

  | Files | Type and ID |
  | --- | --- |
  | `prefabs/**.json` | `prefab`, ID `enemy/bat` |
  | `*.clips.json`, `*.asm.json` | `clips` (`anim.<name>`), `asm` (`asm.<name>`) |
  | `*.tmx` | `map.<name>`, in its own group |
  | `*.atlas`, `*.p` | `atlas.<name>`, `fx.<name>` (particle) |
  | `*.ttf`, `*.otf` | `font.<name>` (`freetype`, size 16) |
  | audio under `music/` | `music.<name>` |
  | other audio | `sfx.<name>` |
  | images | `tex.<name>` under `gfx/`, else `<folder>.<name>` (`bg.forest`) |
  | string tables under `i18n/` | `strings.<language>` |
  | other JSON | `<folder>.<name>` (`weapons/bow.json` → `weapon.bow`), `input`, `ink.<name>` |

- **Left out on purpose:** images that another asset loads itself (atlas pages, particle images, Tiled tileset and image-layer images), `.tsx` files, licences and text files.
- **Groups:** maps get their own group, so a level's map is loaded when its scene starts. Everything else is `global`. Change a group in the file if you want an asset loaded only with one scene; the tool keeps your change.
- **Fonts:** new fonts get size 16. Edit `"font": {"size": 8, "mono": true}` once; it is kept from then on.

On the four demos the tool finds nothing to add, so it follows the same conventions as the hand-made manifests.

## 5. Animation clips
**Don't write `*.clips.json` by hand.**

**From sprite strips** (one image per animation, frames side by side, as most asset packs ship them):
```sh
./gradlew makeClips -Pgame=<game> -Pfrom=gfx/frog -Pduration=0.05
```
- Every `.png` in `assets/gfx/frog/` becomes a clip. `Run (32x32).png` gives the clip `run`, with 32 × 32 frames.
- **Frame size:**
  1. from a `(WxH)` in the file name;
  2. else from `-Pframe=32x32`;
  3. else square frames as tall as the image.
- It writes `assets/anim/frog.clips.json` and adds the images and the clip set (`anim.frog`) to the manifest:
  ```json
  {
    "version": 1,
    "defaults": {"frameDuration": 0.05},
    "clips": {
      "idle": {"sheet": "tex.frog_idle", "frameWidth": 32, "frameHeight": 32, "frames": [0, 10]},
      "run": {"sheet": "tex.frog_run", "frameWidth": 32, "frameHeight": 32, "frames": [0, 11]}
    }
  }
  ```
- **What's left to do by hand** is optional: a different `frameDuration` per clip, `"mode": "once"` for hit or death clips, and frame `events` (footsteps).

**From Aseprite** (best: timing, direction and events come from the file):
1. In Aseprite, give each animation a **tag** (Idle, Run, Hit). Set the tag's direction and repeat count, and put frame events in the tag's user data.
2. Export with File → Export Sprite Sheet: Output → **JSON Data**, array, Meta → **Tags**. Or from the command line:
   ```sh
   aseprite -b hero.aseprite --sheet hero.png --data hero.json --format json-array --list-tags
   ```
3. Put `hero.png` and `hero.json` in `assets/anim/` (or any folder), then run:
   ```sh
   ./gradlew makeClips -Pgame=<game> -Pfrom=anim/hero.json
   ```
   This makes one clip per tag. Frame durations, ping-pong, play-once and events are read from the export every time it loads. Re-export from Aseprite and hot reload picks it up, with no need to run `makeClips` again.

**Using the clips:** add `"Animator": {"clipSet": "anim.frog", "clip": "idle"}` to the prefab (or `animator.play("run")` in code). To switch clips by state (on the ground, falling, hurt), write a small state machine `*.asm.json`, as the platformer's `anim/frog.asm.json` does ([Animation](Animation)). State machines are short and still written by hand.

`makeClips` never overwrites an existing clips file, so hand edits are safe. Delete the file or pass `-Pname=` to make a new one.

## 6. Cameras and camera transitions
Each scene has **one world camera**, the `CameraRig` (`context.renderer().camera()`), plus the UI layer drawn on top ([Rendering and Camera](Rendering-and-Camera)).

| Want | How |
| --- | --- |
| Follow the player | a camera prefab with `CameraFollow` (`target`, `deadZone`, `smoothing`, `bounds`) + `CameraFollowSystem` |
| Smoothly move to another target (a boss, a door that opened) | `follow.target = world.ref(boss);`. With `smoothing` > 0, the camera glides there; set it back to return |
| A scripted pan or zoom (cutscene) | stop following (`follow.target = null`), then tween the rig: `tweens().to(() -> rig.position.x, x -> rig.position.x = x, 40f, 1.5f, Interpolation.smooth)`; the same for `rig.zoom`; restore the target in `then(...)` |
| Screen shake | `rig.addTrauma(0.4f)` |
| Auto-scrolling (shoot-'em-up) | `CameraScroll` + `CameraScrollSystem` |
| Change between areas or levels | a new scene with a transition: `context.scenes().replace(new LevelScene(), Transition.fade(0.4f))` |
| Different camera settings per room | set `follow.bounds` to the room's rectangle when the player enters a `TriggerZone` |
| A minimap or portrait | draw it in the UI (a Scene2D widget), not with a second world camera |

**Not supported yet:** several world cameras at the same time (split screen, picture-in-picture) and post-processing per camera. That would need engine work (a renderer change), not a tool.

## 7. Particle effects
Effects are libGDX particle files (`.p`) plus their images.
- **Design them visually** with **GDX Particle Editor**, libGDX's official particle editor (a separate desktop app; download it from the [libGDX tools page](https://libgdx.com/wiki/tools/2d-particle-editor) and run it with Java 11+). Save the effect, and copy the `.p` file and its images into `assets/fx/`.
- Run `assetManifest`. The effect becomes `fx.<name>` (type `particle`), and its images are left out automatically because the effect loads them.
- **Use it:** add `"ParticleSource": {"effect": "fx.spark"}` to a prefab, or `particles.spawn("fx.spark", x, y)` in code ([Particles](Particles)).
- **Note:** the demos' effects were made in code (`assets-src/demo-shooter/fx/MakeEffects.java`), not in the editor. Files saved by GDX Particle Editor are meant to be compatible with libGDX's particle API, which Ember2D uses, but that has not been tested with Ember2D yet. If an effect is invisible, check its transparency values ([Troubleshooting](Troubleshooting)).
- The older "2D Particle Editor" from gdx-tools writes the same format but is deprecated.

## 8. Everything else
| Job | Tool | Notes |
| --- | --- | --- |
| Levels, tile collision, object placement | **Tiled** | `solid`/`oneWay` tile properties; objects with a prefab Class ([Tilemaps and Tiled](Tilemaps-and-Tiled)) |
| Sprite art and animations | **Aseprite** (or any pixel editor) | then `makeClips` |
| Atlases (many images in one texture) | a texture packer that writes the libGDX `.atlas` format (libGDX's TexturePacker, or GUI packers with a libGDX exporter) | optional; separate images work too, and atlases only save texture switches |
| Fonts | any `.ttf`/`.otf` | `assetManifest`, then set the size once |
| Sound effects and music | Audacity, jsfxr-style generators, a DAW | drop into `sfx/` or `music/`, then `assetManifest` |
| UI skins | **Skin Composer** (libGDX Scene2D skin editor) | the `.json` + `.atlas` pair is detected as a `skin` |
| Dialogue, quests, novel scripts | **Ink** (Inky editor) | compile with `compileInk` ([Build Differences per Kit](Build-Differences-per-Kit)) |
| Input bindings | `input.json` | short and hand-written; the player rebinding UI is not built yet |
| Tuning values (speed, jump, damage) | dev tools **Inspector** (F1) | change live, then **Copy as prefab JSON** |
| Bullet patterns | pattern JSON (kit-bullets) | hand-written; reload live while the game runs |
| Text in several languages | JSON string tables in `i18n/` (`strings.<language>`) | [Localisation](Localisation); `assetManifest` names them |

**Still hand-written today:**
- animation state machines (`*.asm.json`);
- bullet patterns;
- `input.json`;
- prefabs that are not made with the inspector.

All of them are short, validated when they load (errors name the file and field), and hot reloaded in dev runs.
