# 01 — Core loop, ECS and scenes

This chapter shows how to build a game on what Phase 1 delivers: starting the engine, scenes, entities and systems, time, events, assets, sprites and the camera, the debug overlay, and the Android build. Examples come from the platformer demo (`demos/demo-platformer`).

> Status: approved by the developer at the Phase 1 gate (2026-11-26).

## 1. What you get in Phase 1
- `EmberGame`, the single entry point, configured with `EngineConfig` and running on desktop and Android.
- A fixed-step game loop with render interpolation, pause and time scale; timers, tweens and a small state machine.
- An entity component system (ECS): entities, components, systems, families.
- An event bus with immediate and queued events.
- Assets by ID from a manifest, with groups and fallbacks.
- Sprites, render layers, a pixel-perfect pipeline and a following camera.
- Scenes with their own world, timers and subscriptions.
- A debug overlay (F3) with frame metrics and debug shapes.

Everything a game uses is reached through the `EngineContext` (`ctx` below). Game code never extends `EmberGame`; it gives it a configuration and a first scene.

## 2. Start a game
The demo's shared setup (`PlatformerDemo.java`, in the game's `core` module) returns an `EngineConfig`:
```java
public static EngineConfig config(boolean devMode) {
    EngineConfig config = new EngineConfig();
    config.virtualWidth = 320;                        // pixel-perfect world size in virtual pixels
    config.virtualHeight = 180;
    config.backgroundColor.set(0.08f, 0.06f, 0.10f, 1f);
    config.firstScene = DemoScene::new;               // started at boot
    config.devMode = devMode;
    return config;
}
```
Other fields and their defaults: `pixelsPerUnit = 16` (virtual pixels per world unit), `fixedStep = 1/60` s, `maxCatchUpSteps = 5`, `renderMode = PIXEL_PERFECT`, `assetManifest = "manifest.json"`, `debugOverlayInRelease = false`, `debugOverlayVisibleAtStart = false`. The config is checked once at boot; every invalid field is listed in one `EngineConfigException`.

The launchers are short:
```java
// desktop (demos/demo-platformer/desktop): run with  .\gradlew.bat :demos:demo-platformer:desktop:run
new Lwjgl3Application(new EmberGame(PlatformerDemo.config(devMode)), configuration);

// Android (demos/demo-platformer/android)
public class AndroidLauncher extends EmberAndroidLauncher {
    @Override
    protected EngineConfig config(boolean devMode) {
        return PlatformerDemo.config(devMode);
    }
}
```
`EmberGame` and `EngineConfig` live in `dev.ember2d.app`. On Android, dev mode is the app's debuggable flag; on desktop, `gradlew run` turns it on.

**Lifecycle.** The platform can suspend the app (window minimised, Android app sent to the background). That is not game pause: the game's own pause state is untouched, and on resume the loop ignores the long gap instead of fast-forwarding. Services you register can react by implementing `AppLifecycleListener`; the event bus also receives `AppSuspended` and `AppResumed`.

## 3. Scenes
A scene is one screen: a level, a menu, a cutscene. It owns a `World` (entities and systems), its own timers and tweens, and the event subscriptions it makes. All of them are released when the scene exits.

The demo's scene, shortened:
```java
final class DemoScene extends Scene {
    @Override
    protected void enter(EngineContext context) {
        World world = world();

        int player = world.create();
        world.add(player, Transform.class).teleport(0f, 0f);
        Sprite sprite = world.add(player, new Sprite());
        sprite.regionId = "tex.player";
        sprite.origin.set(8f, 0f);                     // anchor at the feet, centred
        world.add(player, new Patrol());

        int camera = world.create();
        CameraFollow follow = world.add(camera, new CameraFollow());
        follow.target = world.ref(player);
        follow.deadZone.setSize(4f, 2f);

        world.addSystem(new PatrolSystem(world));
        world.addSystem(new CameraFollowSystem(world, context.renderer().camera(), context.time()));
        world.addSystem(new SpriteRenderSystem(world, context.renderer(), context.assets(), context.time()));
    }
}
```
Optional overrides: `exit()` (before the scene is released), `update(float delta)` (every frame, after the world's systems), `assetGroup()` (an asset group loaded before `enter` and unloaded after `exit`).

Switching scenes:
```java
ctx.scenes().replace(new LevelScene());   // exit the current scene, start another
ctx.scenes().push(new PauseScene());      // start on top; the scene below stops running
ctx.scenes().pop();                       // back to the scene below
```
- Changes take effect at the start of the next frame, never in the middle of one.
- Only the top scene runs. A scene below keeps its world; its systems, timers and `update` stop, but its event handlers still receive events.
- `pop()` that would leave no scene throws; use `replace`.

## 4. Entities, components and systems
### Entities and components
An entity is an `int` handle. Components are plain Java objects with public fields, one of each type per entity.
```java
int e = world.create();
Transform t = world.add(e, Transform.class);   // created from a registered factory
Patrol p = world.add(e, new Patrol());         // or add an instance
p.speed = 4f;
Patrol same = world.get(e, Patrol.class);      // null if absent
world.has(e, Patrol.class);
world.remove(e, Patrol.class);
world.destroy(e);
```
`add(entity, Type.class)` needs a factory: `Transform` is registered already; register your own types with `world.registerComponent(Patrol.class, Patrol::new)` (no reflection is used, so this works in minified Android builds). Adding an instance always works.

**Keep references with `EntityRef`, not raw handles.** A handle may be reused after the entity is destroyed; a reference knows:
```java
follow.target = world.ref(player);
int target = follow.target.entity(world);      // World.NONE (-1) once the player is destroyed
```
Names and tags help find entities: `world.setName(e, "player")`, `world.findByName("player")`, `world.tag(e, "spawn")`, `world.findByTag("spawn", intArray)`.

### Transform and interpolation
`Transform` holds `position` and `rotation` (in world units and degrees), their `previous` values and `scale`. The engine copies current to previous before every fixed step, and sprites are drawn between the two using `time.alpha()`, so motion stays smooth on 60, 120 or 144 Hz screens. To move without interpolating across the jump (spawning, respawning), use `transform.teleport(x, y)`.

### Systems
A system runs over the entities of one family in one phase:
```java
public final class PatrolSystem extends EntitySystem {
    public PatrolSystem(World world) {
        super(Phase.FIXED, world.family(Transform.class, Patrol.class));
    }

    @Override
    protected void process(int entity, float delta) {
        Transform transform = world().get(entity, Transform.class);
        Patrol patrol = world().get(entity, Patrol.class);
        transform.position.x += patrol.direction * patrol.speed * delta;
        // ... bounce at the limits
    }
}
```
- **Families**: `world.family(A.class, B.class)` has every entity with both; `.exclude(C.class)` removes those with `C`. Families are cached and kept up to date.
- **Order**: by phase, then by the order you add systems. One system per class in a world.
- **Changing entities inside a system** (create, destroy, add, remove) is safe: changes made while a system runs are applied after it, so no system sees a half-changed entity. Outside systems, changes apply immediately.
- `begin()` and `end()` run before and after the entities; `enabled = false` skips a system.

## 5. Time and the game loop
Each frame runs these phases:

| Phase | Runs | Delta | Use for |
| --- | --- | --- | --- |
| `INPUT` | once | scaled frame delta | reading input |
| `FIXED` | 0 to `maxCatchUpSteps` times | `fixedDelta` (1/60 s) | movement and simulation |
| `UPDATE` | once | scaled frame delta | game logic; timers and tweens advance first |
| `LATE` | once | scaled frame delta | camera follow, anything needing final positions |
| `RENDER` | once | real frame delta | drawing |

Read time from `ctx.time()`; systems never measure time themselves:
- `delta()` (scaled, 0 while paused), `unscaledDelta()`, `fixedDelta()`, `alpha()` (between fixed steps, 0 to 1), `elapsed()`, `fixedTick()`.
- **Game pause**: `time.setPaused(true)` stops fixed steps and scaled time; `RENDER` keeps running. Systems that must keep working (a pause menu) override `runsWhenPaused()` to return true and get the real delta in `UPDATE`/`LATE`.
- **Slow motion**: `time.setTimeScale(0.5f)` (0 to 10).
- Frames longer than 0.25 s are clamped; if more than `maxCatchUpSteps` fixed steps are due, the rest are dropped and counted in `time.droppedFixedSteps()` (and a warning is logged at most once per second).

### Timers, tweens and state machines
```java
TimerHandle blink = timers().every(0.5f, () -> sprite.tint.a = 1f - sprite.tint.a);
timers().after(2f, () -> ctx.scenes().replace(new TitleScene()));
blink.cancel();

tweens().to(() -> sprite.tint.a, a -> sprite.tint.a = a, 0f, 0.5f, Interpolation.fade)
        .then(() -> world().destroy(enemy));
```
`timers()`/`tweens()` inside a scene belong to the scene (cancelled when it exits); `ctx.timers()`/`ctx.tweens()` are global. Both run in game time, so they freeze while paused.

`StateMachine<S>` is a small helper for enemy behaviour or menu flow:
```java
StateMachine<Guard> brain = new StateMachine<>(Guard.IDLE);
brain.onEnter(Guard.CHASE, () -> patrol.speed = 9f);
brain.change(Guard.CHASE);        // exit actions of IDLE, then enter actions of CHASE
brain.update(delta);              // brain.timeInState()
```

## 6. Events
One bus for the whole engine: `ctx.events()`.
```java
record CoinCollected(int coin, int value) {}

ctx.events().on(CoinCollected.class, e -> score += e.value());   // inside a scene: removed on exit
ctx.events().queue(new CoinCollected(coin, 10));                  // gameplay: delivered at the next flush point
ctx.events().publish(new MenuOpened());                           // immediate: handlers run now
```
- Queued events are delivered after each fixed step, after `UPDATE` and after `LATE`, in the order they were queued. Use queued events for gameplay; immediate for UI and input.
- Handlers match the event's exact class and run in subscription order. `on(...)` in a scene is cleaned up when the scene exits; `onGlobal(...)` stays until you cancel the returned `Subscription`.
- An exception in a handler is logged and the other handlers still run; in dev mode the first one per frame is rethrown after delivery, so it is not missed.
- Events that keep queuing new events are cut off after 8 passes per flush (a warning tells you).

## 7. Assets
Games refer to assets by ID. The manifest lives at the root of the game's assets (`demos/demo-platformer/core/assets/manifest.json`):
```json
{ "version": 1, "assets": [
  { "id": "tex.player",  "path": "gfx/player-placeholder.png", "type": "texture", "group": "global" },
  { "id": "atlas.level", "path": "gfx/level.atlas",            "type": "atlas",   "group": ["level1", "level2"] },
  { "id": "sfx.coin",    "path": "sfx/coin.ogg",               "type": "sound",   "group": "level1", "optional": true }
] }
```
- `id`, `path` (relative to the assets root), `type` and `group` are required. Types: `texture`, `atlas`, `font` (bitmap), `sound`, `music`, `json`.
- An asset can be in several groups; it stays loaded until all of them are unloaded.
- The `global` group is loaded at boot. A scene loads its own group by returning it from `assetGroup()`; you can also call `ctx.assets().loadGroup("level1")` (loads in the background; `progress()` and `isDone()`) and `unloadGroup(...)`.
- Use assets: `ctx.assets().get("sfx.coin", Sound.class)`, and for sprites `regionId` = a texture ID (`"tex.player"`) or `"atlasId/regionName"` (`"atlas.level/coin"`).
- **Missing files**: a missing *optional* asset is replaced by a visible fallback (magenta texture or region, silent sound) with one warning; a missing *required* asset fails its group with a list of every missing file. An unknown ID error suggests the closest IDs.

The demo's assets are shared by both launchers: desktop serves them from the classpath, Android packs them into the APK.

## 8. Sprites, layers and the camera
### Sprites
```java
Sprite sprite = world.add(e, new Sprite());
sprite.regionId = "atlas.level/coin";   // resolved through the assets on first draw
sprite.layer = "world";                 // render layer
sprite.z = 1f;                          // order within a z-sorted layer
sprite.origin.set(8f, 0f);              // anchor and rotation pivot, in region pixels from bottom-left
sprite.tint.set(1f, 1f, 1f, 0.5f);
sprite.flipX = true;
```
A sprite needs a `Transform` and is drawn by `SpriteRenderSystem`. Its size is the region's pixel size divided by `pixelsPerUnit`, times `Transform.scale`: a 16-pixel sprite is 1 world unit at the default 16 pixels per unit.

### Layers and sorting
The default is one `world` layer sorted by `z`. Set your own, drawn in list order:
```java
ctx.renderer().setLayers(
        new RenderLayer("background", SortMode.NONE),
        new RenderLayer("world", SortMode.Y_SORT),    // higher y drawn first (behind), for top-down games
        new RenderLayer("foreground", SortMode.Z));
```
A sprite on an unknown layer is drawn on `world` (one warning per layer name).

### Pixel-perfect output
The world is drawn at the virtual resolution and scaled up by a whole number, with black bars:

| Window | Scale for 320x180 | Bars |
| --- | --- | --- |
| 1280x720 | x4 | none |
| 1920x1080 | x6 | none |
| 1366x768 | x4 | 43 px left/right, 24 px top/bottom |

`renderMode = PIXEL_PERFECT_FILL` widens the visible world instead of adding side bars on wide phones (2400x1080 shows 400x180 virtual pixels at x6). `SMOOTH` fits the window at any scale without snapping. In the pixel-perfect modes, the camera and sprites are snapped to whole virtual pixels.

### Camera
`ctx.renderer().camera()` is the `CameraRig`: `position` (centre of the view, in world units), `zoom`, and shake (`addTrauma(0.5f)` on a hit; it decays by itself). Move it directly, or let `CameraFollowSystem` follow a `CameraFollow` component:
- `target`: the entity to follow (it needs a `Transform`).
- `deadZone`: the target can move inside this area without moving the camera.
- `smoothing`: 0 follows instantly; larger values ease towards the target.
- `bounds`: keep the whole view inside a rectangle (a level's edges).

To convert a click to world coordinates: `ctx.renderer().screenToWorld(screenX, screenY, outVector)`.

## 9. Debug overlay
Press **F3** in a dev build (or set `debugOverlayVisibleAtStart`, which the demo's Android dev build does). The panel shows FPS, frame time and engine time (average and maximum over the last second), fixed steps in the last frame, dropped steps, entities in the current scene and draw calls.

Draw shapes in world units for the current frame; they cost nothing while the overlay is hidden:
```java
ctx.debug().rect(x, y, width, height, Color.GREEN);
ctx.debug().line(x1, y1, x2, y2, Color.RED);
ctx.debug().circle(x, y, radius, Color.YELLOW);
```
In release builds the overlay is off unless `debugOverlayInRelease` is set.

## 10. Android
- The Android launcher is the five-line class in section 2; the app is landscape, runs on Android 6 (API 23) and newer, and its debug build shows the overlay from the start.
- Build and run: chapter 00, section 8 (`assembleDebug`, `adb install`, the smoke test).
- Every build checks that engine, kit and game code only call Java methods that exist on Android 6 (`tools/android-api-check`). For example `String.lines()` fails the build; `String.isBlank()` and `List.of()` are fine.
- In minified release builds class names are changed: do not rely on class or package names at run time.

## 11. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| `Component type ... is not registered in this World` | `world.add(e, Type.class)` for an unregistered type | `world.registerComponent(Type.class, Type::new)`, or add an instance |
| `get(...)` returns null and a warning says "dead entity" | a stored raw handle outlived its entity | store an `EntityRef` and check `isAlive(world)` |
| Sprite jumps across the screen once when spawned | position set without `teleport`, so it interpolates from 0,0 | use `transform.teleport(x, y)` |
| Movement speed depends on frame rate | moving by a fixed amount per frame instead of multiplying by `delta` | multiply by `delta`; put movement in a `FIXED` system (its `delta` is the fixed step) |
| Nothing happens while paused, including a pause menu | `UPDATE`/`LATE` systems are skipped while paused | override `runsWhenPaused()` in systems that must keep running |
| A handler never fires | subscribed to a supertype, or with `on` in a scene that has exited | subscribe to the exact event class; use `onGlobal` for app-wide handlers |
| Event handled too late | `queue` used where an immediate reaction is needed | `publish` for immediate delivery |
| Magenta square instead of a sprite | optional asset file missing, or region not in the atlas | check the warning in the log; fix the manifest path or region name |
| `Asset "x" is not loaded; load its group first` | the asset's group is not loaded | return the group from `assetGroup()`, or `loadGroup(...)` |
| `pop() would leave no scene` | popping the last scene | use `replace(...)` |
| `Call requires API level NN` from `android-api-check` | a Java method missing on Android 6 | use an alternative available on API 23 |

## 12. Where to look when something breaks
1. **Log** — console (desktop) or `adb logcat` (Android); categories `core`, `scene`, `assets`, `ecs`, `events`, `time`, `render`. In dev mode also `logs/platformer-demo-dev.log`.
2. **Debug overlay (F3)** — FPS, frame and engine time, dropped steps, entity count.
3. **Warnings worth reading**: "Dropped N fixed step(s)" (frames too slow), "deferred to the next flush point" (events queuing events), "Unknown render layer", "Optional asset ... is missing".
4. **Tests** — `.\gradlew.bat build` runs the engine's tests and the architecture rules; failures name the rule and the line.
5. **Specs** — the full contracts are in `documents/specs/` (F-001 lifecycle, F-002 time, F-003 ECS, F-004 rendering, F-008 assets, F-010 scenes, F-014 overlay, F-019 events).
