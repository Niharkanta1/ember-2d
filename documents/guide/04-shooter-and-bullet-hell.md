# 04 — Shooter and bullet hell

This chapter shows how to build a top-down shooter with what Phase 4 delivers: the common gameplay kit (health, hits, triggers, spawners, pools), top-down movement, aiming and weapons, enemy AI, and the bullet kit with its pattern files. Examples come from the shooter demo (`demos/demo-shooter`): a twin-stick dungeon crawl with three waves of enemies and a three-phase bullet-hell boss. Chapters 02 and 03 cover maps, prefabs, collision, UI and saves.

> Status: draft for the developer's review at the Phase 4 gate. Not approved yet.

## 1. What you get in Phase 4
- **`kit-common`:** health and damage with invulnerability, hit boxes and hurt boxes with teams, trigger zones with filters, spawners with waves and live caps, lifetimes, and entity pools.
- **`kit-topdown`:**
  - Movement: 8 directions with acceleration, wall sliding, knockback, or tile-by-tile grid steps.
  - The `shooter` package: aiming (mouse, keys or the touch aim stick) and JSON weapons with fire rate, automatic mode, magazines and reloads.
  - The `ai` package: seek, flee and arrive steering, and A* paths around walls, both through gdx-ai.
- **`kit-bullets`:** thousands of bullets in plain arrays, JSON bullet types and patterns (rings, spirals, arcs, aimed, random, waves, bullets that burst into more bullets), hits and grazes, and clearing bullets into pickups.
- **Engine additions:**
  - a scrolling camera for shoot-'em-ups;
  - a second on-screen stick for twin-stick aiming;
  - `PrefabLibrary.reset` for pools.

## 2. The common kit
Register its components once in your `ComponentSetup`:
```java
CommonKit.register(registry);    // Health, Team, Hitbox, Hurtbox, Lifetime, TriggerZone, Spawner
BulletsKit.register(registry);   // BulletTarget
TopDownKit.register(registry);   // TopDownBody, Steering
ShooterKit.register(registry);   // Aim, Weapon
```
### Health and damage
```json
"Health": { "max": 5, "invulnerableSeconds": 1.0 },
"Team":   { "id": 1 }
```
- **Causing damage:** all damage goes through `HealthSystem.damage(target, amount, source, knockbackX, knockbackY)`. It returns false while the target is invulnerable or already dead.
- **Events:** each hit publishes `Damaged`. `Died` is published exactly once, when health reaches 0.
- **Other calls:** `heal` and `kill`.
- **Despawning on death:** `destroyOnDeath` despawns the entity itself.
- **Starting health:** `current` -1 means "start full", so a prefab only needs `max`.

### Hit boxes and hurt boxes
```json
"Hitbox":  { "box": { "x": -0.4, "y": 0, "width": 0.8, "height": 0.8 }, "damage": 1, "knockback": 8 },
"Hurtbox": {}
```
- **What hits what:** an active hit box damages every overlapping hurt box of another team, once per activation.
- **A new activation:** call `hitbox.restart()`, or turn `active` off and on. For a sword swing, `activeSeconds` closes the window by itself.
- **Hurt box shape:** an empty hurt box box uses the entity's `Collider`.
- **Knockback:** it points from the hit box to the hurt box. The top-down kit applies it to the body automatically.
- **Contact damage:** the demo restarts its enemies' hit boxes once a second for contact damage, and the player's one-second invulnerability keeps that fair.

### Triggers, spawners, lifetimes
- **`TriggerZone`** (with a trigger `Collider`):
  - It publishes `ZoneEntered` and `ZoneExited` for entities that pass its filters: `tag` (a world tag such as `player`) and `team`.
  - `once` fires a single time; the demo's coins use it.
- **`Spawner`:** spawns `prefab` every `interval` seconds, `burst` at a time, up to `total`, never more than `maxAlive` alive. `waitFor` holds it until a zone with that name fires. The demo's waves are spawner objects placed in Tiled with plain property overrides:
  ```xml
  <object name="wave2_a" type="spawner" x="56" y="320">
    <properties>
      <property name="Spawner.prefab" value="enemy/skeleton"/>
      <property name="Spawner.delay" type="float" value="12.0"/>
      <property name="Spawner.total" type="int" value="2"/>
    </properties><point/></object>
  ```
- **`Lifetime`:** despawns its entity after `seconds`.

### Pools and despawning
```java
EntityPool splats = EntityPool.ofPrefab(world, context.prefabs(), "fx/splat", 8);   // 8 made now
splats.obtain(x, y);                 // reset to the prefab's values and placed
Despawn.despawn(world, entity);      // pooled entities go back to their pool; others are destroyed
```
- **Use `Despawn`:** use `Despawn.despawn` instead of `world.destroy`. The kit's own systems do (deaths, `Lifetime`, `destroyOnHit`), so pooled effects with a `Lifetime` return to their pool by themselves.
- **No new entities:** after warm-up a pool creates no new entities or components.

## 3. Top-down movement
```json
"TopDownBody": { "maxSpeed": 6 }
```
- **The system:** `TopDownSystem(world, collision, input, events)`, in the fixed step.
- **Input:** it reads the `move` 2D axis. Diagonals are no faster than straight lines.
- **Movement:** it accelerates (`acceleration`), slows down (`friction`) and slides along walls and solids.
- **Facing:** the body flips its `Sprite` to face left or right, and keeps `facingX`/`facingY` for aiming.
- **Grid mode:** `gridStep` moves exactly one tile per step and publishes `StepFinished`, for RPG-style movement.
- **`locked`:** a locked body ignores input (dialogue, cutscenes).
- **Driving from code:** `useInput` false with `moveX`/`moveY` moves a body from code; the AI does this.

### Scrolling camera (shoot-'em-ups)
Put a `CameraScroll` (`speedX`, `speedY`, `bounds`) on a camera entity instead of `CameraFollow`, and add a `CameraScrollSystem`. It moves the view at a constant speed, keeps entities tagged `player` inside it, and publishes `ScrollFinished` at the end of `bounds`.

## 4. Aiming and weapons
### Weapons
A weapon is a JSON asset:
```json
{ "version": 1, "fireRate": 10, "auto": true, "magazine": 24, "reload": 1.0, "muzzle": 0.6,
  "emit": { "type": "random", "bullet": "player", "spread": 6, "speed": 14 } }
```
- **`emit`:** any `kit-bullets` emitter (section 6). Its angle is replaced by the aim angle when the weapon fires.
- **The component:** on the entity, `"Weapon": { "weapon": "weapon.smg", "fireAction": "fire", "reloadAction": "reload" }`. `WeaponSystem` handles the cooldown, ammo and reloads; an empty magazine reloads by itself.
- **Enemies:** leave `fireAction` empty and set `weapon.trigger` from code.
- **Events:** `ShotEvents.Fired`, `ReloadStarted` and `Reloaded` are reused objects, so shooting allocates nothing. Read them inside the handler and don't keep them:
  ```java
  context.events().on(ShotEvents.Fired.class, e -> { if (e.entity == player) audio.playVaried("sfx.shoot", 0.1f); });
  ```

### Aiming
- **`Aim` and `AimSystem`:** an entity with `Aim` aims along the `aim` axis when it is held (the touch aim stick, or the arrow keys in the demo). Otherwise it aims at the mouse when `mouse` is true, or keeps its last aim.
- **Mouse on touch screens:** the demo sets `mouse` only on desktop, so a touch on a phone never becomes the aim.

### Twin-stick touch controls
Add an aim stick to the `touchControls` section of the bindings file:
```json
"aimStick": { "axis": "aim", "x": 150, "y": 110, "radius": 52, "deadZone": 0.15, "fire": "fire", "fireAt": 0.5 }
```
- **Behaviour:** it floats on the right half of the screen, while buttons there still get their touches first. It drives the `aim` axis, and pushed past half its radius it also holds `fire`.
- **Moving:** the left-half joystick moves as before.

## 5. Enemy AI
```json
"Steering": { "mode": "FOLLOW_PATH", "sight": 30 }
```
Add the systems before `TopDownSystem`:
```java
PathPlanner paths = new PathPlanner(map.collision());   // A* over the map's free tiles
world.addSystem(new AiSystem(world, paths));
world.addSystem(new TopDownSystem(world, collision, input, events));
```
- **Modes:**
  - `SEEK`, `FLEE` and `ARRIVE` move towards or away from the target. `ARRIVE` slows down within `slowRadius`.
  - `FOLLOW_PATH` walks a path around walls, planned again every `repathSeconds`.
- **Movement:** AI bodies move through the same `TopDownSystem` as the player, so they slide and take knockback the same way.
- **Target:** the target is an entity (`target`) or a point. A prefab can't name the player, so the demo gives every enemy without a target the player.
- **Paths without AI:** `PathPlanner.find(fromX, fromY, toX, toY, waypoints)` gives you paths for your own use.

## 6. Bullets and patterns
### Bullet types
```json
{ "version": 1, "types": {
    "player":   { "region": "atlas.shooter/bullet.player", "radius": 0.12, "lifetime": 1.5, "walls": true, "rotate": true },
    "orb_blue": { "region": "atlas.shooter/orb.blue", "radius": 0.16, "lifetime": 9 } } }
```
- **`walls`:** wall bullets stop at tiles and colliders on the wall mask, `setWallMask(1, collision)`, which means layer 0.
- **Collision layers:** keep actors on another collision layer (the demo uses 1), or bullets treat them as walls.
- **Other fields:** `pierce` survives hits and hits each target once. `graze` false cannot be grazed.

### Setting up
```java
BulletTypes types = BulletTypes.parse(context.assets().get("bullets", JsonValue.class), context.assets().path("bullets"));
BulletWorld bulletWorld = new BulletWorld(4096, types);
BulletSystem bullets = new BulletSystem(world, bulletWorld, new BulletPatterns(context.assets(), types),
        context.events(), health, context.time());
world.addSystem(bullets);
sprites.addLayerContent(new BulletRenderer(bulletWorld, context.assets(), "bullets", ppu));   // one draw pass
```
- **Targets:** entities with `BulletTarget` (`radius`, `grazeRadius`, an offset to the sprite's centre) and a `Team`.
- **Hits:** a hit queues `BulletHit` and damages the target through `health`. A near miss within the graze radius queues `Grazed` once per bullet.

### Pattern files
```json
{ "version": 1, "emit": [
    { "type": "spiral", "bullet": "orb_magenta", "count": 4, "speed": 3.5, "repeat": 80, "interval": 0.12, "angleStep": 13 },
    { "type": "aimed", "bullet": "orb_magenta", "speed": 2.5, "repeat": 6, "interval": 1.4, "delay": 0.5,
      "child": { "type": "ring", "bullet": "orb_blue", "count": 16, "speed": 3 }, "childAfter": 1.2 } ] }
```
- **Angles:** in degrees, with 0 pointing right and 90 up.
- **Emitter types:**

  | `type` | One volley is |
  | --- | --- |
  | `single` | one bullet at `angle` |
  | `ring` / `spiral` | `count` bullets around the circle (a spiral turns by `angleStep` each volley) |
  | `arc` | `count` bullets across `spread`, centred on `angle` |
  | `aimed` | an arc centred on the target |
  | `random` | `count` bullets at random angles within `spread` (deterministic per seed) |
  | `wave` | an arc whose angle swings by `amplitude` over `frequency` volleys |
- **Fields:**
  - Volleys: `repeat`, `interval` and `delay`.
  - Changes per volley: `angleStep` and `speedStep`.
  - Bullet movement: `accel` and `turn`.
  - Spawn position: `offset`, a distance along the angle.
  - Bullets that burst: `child` with `childAfter`.

Running patterns:
```java
int handle = bullets.run("pattern.boss1", bossEntity, player, ENEMY_TEAM);    // follows the boss, aims at the player
bullets.runAt("pattern.skeleton", x, y + 0.5f, player, ENEMY_TEAM);           // from a fixed point
if (!bullets.running(handle)) { ... }   bullets.stop(handle);
bullets.clear(ENEMY_TEAM, true);        // remove the boss's bullets; BulletCleared events become coins
```
- **The demo's boss:** `BossScene` picks a pattern by the boss's health (above 66 %, above 33 %, below) and runs it again whenever it ends. Each phase change clears the screen into coins.
- **Hot reload:** pattern files hot-reload in dev builds. Save one and the next run of the pattern uses it.

### Budget
- **No allocation:** the bullet world allocates nothing per bullet. The kit's tests measure no allocation over 600 steps with 3,000 bullets.
- **Measured:** on the developer's desktop a step with 3,000 turning bullets takes about 85 µs. The demo's **Bullet stress test** shows the count and frame rate on any device and logs the bullet step time every 2 seconds.
- **Emulator:** it kept 3,000 bullets at about 140 µs per step.

## 7. Running the demo
- **Desktop:**
  ```
  ./gradlew :demos:demo-shooter:desktop:run
  ```
  WASD moves, the mouse aims, the left button or Space fires, R reloads, P or Escape pauses. Dev builds have **Skip to boss** in the menu.
- **Android:**
  ```
  ./gradlew :demos:demo-shooter:android:installDebug
  ```
  Use the left stick to move and the right stick to aim and fire.
- **Making the data again:**
  - `assets-src/demo-shooter/make_assets.py` builds the atlas, maps, prefabs, patterns and bindings from the developer's art pack.
  - `assets-src/demo-shooter/sound/make_sounds.py` makes the sounds.
  - After the first run, edit the files directly or in Tiled.

## 8. Common mistakes
- **Bullets vanish when they touch an enemy without hurting it:** the enemy's collider is on the wall layer. Move actors to another collision layer.
- **Enemies stand still:** their `Steering` has no target (a point at 0, 0 inside a wall has no path); `pathFound` is false. Also check `sight`.
- **Contact damage happens only once:** hit boxes hit each target once per activation. Restart them, or turn them off and on.
- **A `ShotEvents` object has changed later:** the events are reused. Copy the fields you need inside the handler.
- **"A system of class X is already registered":** a world takes one system per class. Use two classes.
- **The boss crashes the game when it dies to code outside a system:** `world.destroy` is immediate outside a system. Check the entity again after anything that can kill it.

## 9. Where to look when something breaks
- **Logs:**
  - `shooter` (stage ready, boss phases, bullet counts, stress numbers, scores) and `bullets` (capacity warnings).
  - On Android: `adb logcat | grep -E "shooter|scene"`.
- **F3 / dev overlay:** colliders, hit boxes and triggers; entity count and frame times.
- **Tests:**
  - `BulletsTest` shows each emitter's exact angles.
  - `ShooterTest` shows fire rates and reloads.
  - `AiTest` has an enemy walking around a wall.
  - `CommonKitTest` covers damage, triggers, spawners and pools.
  - The demo's `ShooterDataTest` checks every map, prefab, pattern and weapon file.
