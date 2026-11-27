# kit-bullets

Module `kits/kit-bullets`, package `kit.bullets`. Spec F-024, ADR-020. Thousands of bullets, defined in JSON, kept **outside the ECS** in plain arrays for speed. Register `BulletTarget` with `BulletsKit.register(registry)`.

## 1. Bullet types
A `json` asset:
```json
{ "version": 1, "types": {
    "player":   { "region": "atlas.shooter/bullet.player", "radius": 0.12, "damage": 1, "lifetime": 1.5, "walls": true, "rotate": true },
    "pellet":   { "region": "atlas.shooter/orb.red", "radius": 0.16, "lifetime": 6, "walls": true },
    "orb_blue": { "region": "atlas.shooter/orb.blue", "radius": 0.16, "lifetime": 9 },
    "stress":   { "region": "atlas.shooter/orb.blue", "radius": 0.16, "lifetime": 6, "graze": false } } }
```
Fields:
- `region`: the image;
- `radius` (world units), `damage` and `lifetime` (seconds);
- `walls`: the bullet stops at walls;
- `rotate`: the sprite faces the direction of travel;
- `pierce`: the bullet survives hits and hits each target once;
- `graze` (default true): false means it can't be grazed.

## 2. Setting up
```java
BulletTypes types = BulletTypes.parse(context.assets().get("bullets", JsonValue.class), context.assets().path("bullets"));
BulletWorld bulletWorld = new BulletWorld(4096, types);                    // capacity
bulletWorld.setCullArea(new Rectangle(bounds.x - 2f, bounds.y - 2f, bounds.width + 4f, bounds.height + 4f));
HealthSystem health = new HealthSystem(world, context.events());
BulletSystem bullets = new BulletSystem(world, bulletWorld, new BulletPatterns(context.assets(), types),
        context.events(), health, context.time());
bullets.setWallMask(1, collision);                                         // bit mask: layer 0 = tiles
world.addSystem(health);
world.addSystem(bullets);                                                  // FIXED
sprites.addLayerContent(new BulletRenderer(bulletWorld, context.assets(), "bullets", ppu));   // one draw pass
```
- **Targets:** entities with `BulletTarget` (`radius`, `grazeRadius`, `offsetX`, `offsetY` to the sprite's centre) and a kit-common `Team`.
- **Hits:** a hit damages through `HealthSystem` and queues `BulletHit`. A near miss within `grazeRadius` queues `Grazed` once per bullet.
- **Walls:** wall bullets stop at tiles and colliders on the wall mask. **Keep actors on another collision layer** (the shooter uses layer 1), or bullets treat them as walls.

## 3. Patterns
A pattern file (`json` asset, ID `pattern.*`) holds one emitter or a list:
```json
{ "version": 1, "emit": [
    { "type": "spiral", "bullet": "orb_magenta", "count": 4, "speed": 3.5, "repeat": 80, "interval": 0.12, "angleStep": 13 },
    { "type": "aimed", "bullet": "orb_magenta", "speed": 2.5, "repeat": 6, "interval": 1.4, "delay": 0.5,
      "child": { "type": "ring", "bullet": "orb_blue", "count": 16, "speed": 3 }, "childAfter": 1.2 } ] }
```
Angles are in degrees, with 0 pointing right and 90 up.

| `type` | One volley |
| --- | --- |
| `single` | one bullet at `angle` |
| `ring` | `count` bullets evenly around the circle |
| `spiral` | a ring that turns by `angleStep` each volley |
| `arc` | `count` bullets across `spread`, centred on `angle` |
| `aimed` | an arc centred on the target |
| `random` | `count` bullets at random angles within `spread` (deterministic per seed, `setSeed`) |
| `wave` | an arc whose angle swings by `amplitude` over `frequency` volleys |

| Field | Meaning |
| --- | --- |
| `bullet`, `count`, `speed`, `angle`, `spread` | what and where |
| `repeat`, `interval`, `delay` | volleys |
| `angleStep`, `speedStep` | change per volley |
| `accel`, `turn` | bullet movement over time |
| `offset` | spawn distance along the angle |
| `child`, `childAfter` | each bullet bursts into a child emitter after a time |

## 4. Running patterns
```java
int h = bullets.run("pattern.boss1", bossEntity, player, ENEMY_TEAM);    // follows the origin, aims at the target
bullets.runAt("pattern.skeleton", x, y + 0.5f, player, ENEMY_TEAM);      // from a fixed point
bullets.fire(emitter, x, y, angleDeg, target, team);                     // one volley now (weapons use it)
bullets.running(h);  bullets.stop(h);  bullets.stopAll(bossEntity);
int removed = bullets.clear(ENEMY_TEAM, true);                           // true: BulletCleared events (coins)
```
- **Phases:** the shooter's boss picks a pattern by its health (above 66 %, above 33 %, below), runs it again whenever it ends, and clears the screen into coins at each phase change.
- **Hot reload:** pattern files reload in dev runs; the next run of a pattern uses the new file.

## 5. Performance
- No allocation per bullet; a test measures no allocation over 600 steps with 3,000 bullets.
- About 85 µs per step with 3,000 turning bullets on the developer's desktop, and about 140 µs on the emulator.
- The shooter's **Bullet stress test** shows the count and the frame rate on any device.
- Render: one `BulletRenderer` pass per layer; no ECS entities for bullets.

## 6. Events
`BulletHit`, `Grazed`, `BulletCleared` (queued). Complete game: [Guide: Bullet Hell](Guide-Bullet-Hell).
