# kit-common

Module `kits/kit-common`, package `kit.common`. Spec F-021. These are shared gameplay building blocks used by every genre. Register them with `CommonKit.register(registry)`, which covers `Health`, `Team`, `Hitbox`, `Hurtbox`, `Lifetime`, `TriggerZone`, `Spawner` and `HitFlash`.

## 1. Health and damage
```json
"Health": { "max": 5, "invulnerableSeconds": 1.0 },
"Team":   { "id": 1 }
```
| `Health` field | Default | Meaning |
| --- | --- | --- |
| `max` | 3 | maximum health |
| `current` | -1 | -1 starts full |
| `invulnerableSeconds` | 0.5 | no damage for this long after a hit |
| `destroyOnDeath` | false | despawn when it dies |
| `invulnerableLeft`, `dead` | runtime | |

```java
HealthSystem health = new HealthSystem(world, context.events());   // FIXED
world.addSystem(health);
boolean hurt = health.damage(target, 1, sourceEntity, knockbackX, knockbackY);  // false while invulnerable or dead
health.heal(target, 1);
health.kill(target, source);
health.isInvulnerable(target);
```
- Every hit publishes `Damaged(target, source, amount, healthLeft, knockbackX, knockbackY)`.
- `Died(entity, killer)` is published once, when health reaches 0.
- kit-topdown's bodies apply knockback from `Damaged` automatically.

## 2. Hit boxes and hurt boxes
```json
"Hitbox":  { "box": { "x": -0.4, "y": 0, "width": 0.8, "height": 0.8 }, "damage": 1, "knockback": 8 },
"Hurtbox": {}
```
`HitSystem(world, health)` (`FIXED`) runs these rules:
- **What hits what:** an active `Hitbox` damages every overlapping `Hurtbox` of **another team**, once per activation.
- **Activations:** `hitbox.restart()`, or turning `active` off and on, starts a new activation. `activeSeconds` closes it by itself (a sword swing). `destroyOnHit` removes the hitbox's entity after a hit (projectiles).
- **Shapes:** an empty `Hurtbox.box` uses the entity's `Collider`.
- **Knockback** points from the hit box to the hurt box.
- **Contact damage:** the shooter restarts its enemies' hit boxes once a second. The player's one-second invulnerability keeps that fair.

## 3. Trigger zones
```json
"Collider":    { "box": { "x": -1, "y": 0, "width": 2, "height": 1 }, "trigger": true },
"TriggerZone": { "tag": "player", "once": true }
```
`TriggerZoneSystem(world, events)` (`FIXED`; it listens to the engine's `TriggerEntered`, so the collider must be a trigger and a `CollisionSystem` must run) publishes `ZoneEntered(zone, entity)` and `ZoneExited` for entities that pass the filters:
- `tag`: a world tag such as `player` (`world.tag(player, "player")`);
- `team`: a `Team` id; -1 means any team;
- `once`: fires a single time.

Pickups, exits, travel zones and checkpoints are all trigger zones.

## 4. Spawners and lifetimes
```json
"Spawner": { "prefab": "enemy/slime", "delay": 1.0, "interval": 1.5, "total": 3, "burst": 1, "maxAlive": 3 }
```
`SpawnerSystem(world, prefabs, events)` (`UPDATE`) handles spawning:
- it spawns `prefab` every `interval` seconds, `burst` at a time, up to `total` (0 = unlimited), never more than `maxAlive` alive;
- `delay` waits before the first spawn, and `waitFor` holds the spawner until a zone with that name fires;
- `offsetX` and `offsetY` shift the spawn point;
- events: `Spawned(spawner, entity)` and `SpawnerFinished(spawner)`.

The shooter's waves are spawner objects placed in Tiled with property overrides.

`Lifetime` (`seconds`) despawns its entity after that time, through `LifetimeSystem(world)` (`UPDATE`).

## 5. Pools and despawning
```java
EntityPool splats = EntityPool.ofPrefab(world, context.prefabs(), "fx/splat", 8);   // 8 made now
int splat = splats.obtain(x, y);     // reset to the prefab's values and placed
Despawn.despawn(world, entity);      // pooled entities go back to their pool; others are destroyed
splats.freeCount(); splats.inUse();
```
- Always use `Despawn.despawn` instead of `world.destroy` when pools may be involved. The kit's own systems do this (deaths, `Lifetime`, `destroyOnHit`).
- After warm-up a pool creates no new entities or components.

## 6. Hit flash
```json
"HitFlash": { "seconds": 0.1 }
```
`HitFlashSystem(world, events)` (`UPDATE`) sets the sprite's `flash` to 1 on `Damaged` and back after `seconds`. See [Sprite Materials](Sprite-Materials).

## 7. The Ink dialogue runner
`DialogueRunner` runs Ink stories (compiled by inklecate to `.ink.json`, run by blade-ink 1.2.1) behind a small API:
```java
DialogueRunner story = DialogueRunner.fromAsset(context.assets(), "ink.town");   // a json asset
story.bind("give", args -> inventory.add(items, (String) args[0], ((Number) args[1]).intValue()) > 0);
story.jumpTo("elder");                                  // a knot or knot.stitch
while (story.canContinue()) {
    story.next();
    show(story.speaker(), story.text());                // "Grik: Hello" -> "Grik", "Hello"
    List<String> tags = story.tags();  String mood = story.tag("mood");
}
List<String> choices = story.choices();
story.choose(0);
Object gold = story.variable("gold");  story.setVariable("gold", 5);
story.save(slotData);  story.load(slotData);            // position, variables, visit counts
```
| Method | Meaning |
| --- | --- |
| `fromJson(json, name)`, `fromAsset(assets, id)` | load a compiled story |
| `canContinue()`, `next()`, `isFinished()` | step through lines |
| `line()`, `text()`, `speaker()` | the line as written, without the `Name:` prefix, and the speaker (`speaker:` tag, else the prefix) |
| `tags()`, `tag(key)` | the line's tags |
| `choices()`, `choose(i)` | the current choices |
| `jumpTo(path)` | go to a knot or stitch; an unknown path fails, naming it |
| `variable`, `setVariable` | Ink variables |
| `bind(name, DialogueFunction)` | game code for an `EXTERNAL` function |
| `saveState()`/`loadState(json)`, `save`/`load` | the story state |
| `name()` | the story's file, for messages |

- **Fallbacks:** an unbound `EXTERNAL` function runs its Ink fallback, as in Inky.
- **Functions run once:** bound functions are registered as not safe for Ink's lookahead, so game code with side effects runs exactly once per call.
- **Choice texts** are fixed when the choices appear.
- **Android:** blade-ink runs on Android 6; the RPG demo was checked on an API 23 emulator.

Presenting lines: use `ember.ui.DialogueBox` ([UI](UI)), or kit-novel for staged scenes ([kit-novel](Kit-Novel)).

## 8. Events
`Damaged`, `Died`, `ZoneEntered`, `ZoneExited`, `Spawned`, `SpawnerFinished`.
