# kit-topdown

Module `kits/kit-topdown`. Specs F-016, F-017. It provides top-down movement plus three packages:

| Package | Use | Register with |
| --- | --- | --- |
| `kit.topdown` | `TopDownBody`, `TopDownSystem`, `StepFinished` | `TopDownKit.register` (also registers `Steering`) |
| `kit.topdown.shooter` | `Aim`, `AimSystem`, `Weapon`, `WeaponDef`, `WeaponLibrary`, `WeaponSystem`, `ShotEvents` | `ShooterKit.register` |
| `kit.topdown.ai` | `Steering`, `AiSystem`, `PathPlanner`, `TileGraph` | (with `TopDownKit`) |
| `kit.topdown.rpg` | `Interactable`, `Interactor`, `InteractionSystem`, `Interacted`, `InteractionFocus`, `Inventory`, `ItemCatalog`, `ItemPickup`, `ItemPickupSystem`, `ItemPickedUp` | `RpgKit.register` |

## 1. Movement
```json
"TopDownBody": { "maxSpeed": 6 }
```
`TopDownSystem(world, collision, input, events)` (`FIXED`):
- reads the `move` 2D axis; diagonals are no faster than straight lines;
- accelerates (`acceleration` 60) and slows down (`friction` 50); slides along walls and solids;
- flips the `Sprite` (`flipSprite`) and keeps `facingX`/`facingY` (for aiming and interaction);
- applies knockback from kit-common's `Damaged` (`knockbackScale`, `knockbackDecay`);
- grid mode: `gridStep` moves exactly one tile per step at `stepsPerSecond` and publishes `StepFinished`;
- `locked` ignores input (dialogue, cutscenes); `moving` tells whether it moves;
- `useInput = false` with `moveX`/`moveY` drives it from code. The AI does this.

## 2. Aiming and weapons (`shooter`)
### Weapons
A weapon is a JSON asset (`"type": "json"`):
```json
{ "version": 1, "fireRate": 10, "auto": true, "magazine": 24, "reload": 1.0, "muzzle": 0.6,
  "emit": { "type": "random", "bullet": "player", "spread": 6, "speed": 14 } }
```
```json
"Weapon": { "weapon": "weapon.smg", "fireAction": "fire", "reloadAction": "reload" }
```
```java
world.addSystem(new WeaponSystem(world, bullets, new WeaponLibrary(context.assets(), types), context.events(), input));
```
- `emit` is a kit-bullets emitter; its angle is replaced by the aim angle.
- `WeaponSystem` (`FIXED`) handles cooldown, ammo and reloads; an empty magazine reloads by itself.
- Enemies: leave `fireAction` empty and set `weapon.trigger` from code.
- Events: `ShotEvents.Fired`, `ReloadStarted` and `Reloaded` are **reused objects**, so read them inside the handler.

### Aiming
`Aim` + `AimSystem(world, input)` (`UPDATE`): aims along the `aim` axis when held (the touch aim stick or keys). Otherwise it aims at the mouse when `mouse` is true, or keeps its last aim. Set `mouse` only on desktop, so a touch never becomes an aim.

## 3. AI (`ai`)
```json
"Steering": { "mode": "FOLLOW_PATH", "sight": 30 }
```
```java
PathPlanner paths = new PathPlanner(map.collision());   // A* (gdx-ai) over free tiles
world.addSystem(new AiSystem(world, paths));            // FIXED, before TopDownSystem
world.addSystem(new TopDownSystem(world, collision, input, events));
```
| `Steering` field | Default | Meaning |
| --- | --- | --- |
| `mode` | `FOLLOW_PATH` | `NONE`, `SEEK`, `FLEE`, `ARRIVE`, `FOLLOW_PATH` |
| `target` or `targetX`/`targetY` | — | an entity, or a point |
| `arriveRadius`, `slowRadius` | 0.3, 2 | `ARRIVE` stops and slows |
| `fleeRadius` | 0 | `FLEE` distance |
| `sight` | 0 | how far it notices the target |
| `repathSeconds` | 0.5 | how often paths are planned again |
| `pathFound`, `waypoint`, `repathLeft` | runtime | |

- AI bodies move through the same `TopDownSystem` as the player.
- A prefab can't name the player, so the shooter gives every `Steering` without a target the player each frame.
- `paths.find(fromX, fromY, toX, toY, waypoints)` gives paths for your own use; `paths.walkable(x, y)`.

## 4. RPG (`rpg`)
### Interaction
```json
"Interactable": { "prompt": "Talk", "action": "elder", "range": 1.5 }
"Interactor": {}
```
`InteractionSystem(world, input, events)` (`UPDATE`):
- **Target:** the nearest enabled interactable within `range` and inside a `cone` (120°) around the facing. Anything closer than 0.3 counts from every direction. Score = distance × (1 + angle / 180).
- **Prompt:** `InteractionFocus(interactor, target, prompt)` when the target changes.
- **Using it:** pressing `inputAction` (`interact`) publishes `Interacted(interactor, target, action)`. `interaction.interact(player)` does the same from code.
- **Locked bodies:** have no target.

### Items and inventory
```json
{ "version": 1, "items": {
    "key.cellar": { "name": "Cellar key", "icon": "atlas.rpg/key", "tags": ["key"] },
    "tonic": { "name": "Tonic", "icon": "atlas.rpg/tonic", "stack": 9, "tags": ["consumable"] } } }
```
```java
ItemCatalog items = ItemCatalog.parse(assets.get("items", JsonValue.class), assets.path("items"));
Inventory inv = new Inventory();                     // or a component from a prefab
int added = inv.add(items, "tonic", 2);             // fills stacks, then free slots (capacity 12)
boolean taken = inv.remove("tonic", 1);             // all or nothing
inv.count("tonic"); inv.has("key.cellar", 1); inv.hasTag(items, "key");
for (int i = 0; i < inv.size(); i++) { inv.item(i); inv.count(i); }
inv.save(data);  inv.load(data, items);             // unknown items skipped, stacks clamped
```
**Pickups:** `ItemPickup` (`item`, `count`) on a `TriggerZone` entity. `ItemPickupSystem(world, items, events)` adds the items to the entering entity's `Inventory`, publishes `ItemPickedUp`, and despawns the pickup (or leaves the rest).

The complete RPG is in [Guide: RPG](Guide-RPG); the shooter in [Guide: Top-Down Shooter](Guide-Top-Down-Shooter).
