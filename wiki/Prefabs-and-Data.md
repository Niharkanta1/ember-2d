# Prefabs and Components from Data

Package `ember.data` (`ComponentRegistry`, `ComponentSetup`, `PrefabLibrary`, `MapSpawner`, `EntitySpawned`, `TiledTypes`, `KeepRules`). Spec F-010.

## 1. The component registry
Every component that data can create is registered once, in your `ComponentSetup`:
```java
public final class ShooterComponents implements ComponentSetup {
    @Override
    public void register(ComponentRegistry registry) {
        CommonKit.register(registry);    // Health, Team, Hitbox, Hurtbox, Lifetime, TriggerZone, Spawner, HitFlash
        BulletsKit.register(registry);   // BulletTarget
        TopDownKit.register(registry);   // TopDownBody, Steering
        ShooterKit.register(registry);   // Aim, Weapon
        registry.register(CameraFollow.class, CameraFollow::new)
                .register(Enemy.class, Enemy::new);
    }
}
// config.components = new ShooterComponents();
```
- **Engine components registered already:** `Transform`, `Sprite`, `Text`, `Collider`, `Actor`, `Solid`, `Animator`, `AnimationStateMachine`, `ParticleSource`.
- **Not pre-registered:** `CameraFollow` and `CameraScroll`. Register them if your data uses them; all the demos do.
- **Settable fields:** public non-static fields of type `int`, `long`, `float`, `double`, `boolean`, `String`, enum, `Color`, `Vector2`, `Rectangle` (these three also when `final`), `float[]` and `EntityRef`.
- **Android:** the release build runs your setup (`emberKeepRules` task, `dev.ember2d.data.KeepRules`) to write R8 keep rules, so registered components survive minification. **An unregistered component works in debug and breaks in release.**

## 2. Prefabs
A prefab is a JSON file in the manifest (`"type": "prefab"`); its ID is its name. The shooter's slime (`prefabs/enemy/slime.json`):
```json
{ "version": 1, "components": {
  "Sprite":   { "regionId": "atlas.shooter/slime", "origin": [8, 0], "z": 1 },
  "Collider": { "box": { "x": -0.35, "y": 0, "width": 0.7, "height": 0.6 }, "layer": 1 },
  "Actor": {},
  "TopDownBody": { "maxSpeed": 2.6 },
  "Steering": { "mode": "FOLLOW_PATH", "sight": 30 },
  "Health": { "max": 3, "invulnerableSeconds": 0.05 },
  "Team": { "id": 2 },
  "BulletTarget": { "radius": 0.45, "offsetY": 0.45 },
  "Hitbox": { "box": { "x": -0.4, "y": 0, "width": 0.8, "height": 0.8 }, "damage": 1, "knockback": 8 },
  "Hurtbox": {},
  "Enemy": { "score": 10 },
  "HitFlash": {} } }
```
- **Values:** `Vector2` fields take `[x, y]`, `Rectangle` fields take `{x, y, width, height}`, and `Color` fields take `"#RRGGBB"`, `"#RRGGBBAA"` or `{ "r": 1, "g": 0.5, "b": 0, "a": 1 }`.
- **Spawning from code**, with optional overrides keyed `Component.field`:
```java
int slime = context.prefabs().spawn(world, "enemy/slime", x, y);
int fast = context.prefabs().spawn(world, "enemy/slime", x, y, Map.of("TopDownBody.maxSpeed", 4f));
```
- Every spawned entity gets a `Transform` at the position, and `EntitySpawned(entity, prefab)` is published.
- **Errors** name the file, the component and the field, and list the registered components.
- **Versions:** rename fields with prefab versions and migrations: `prefabs.setVersion(2)` and `prefabs.addMigration(1, json -> ...)`.
- **Pools:** `prefabs.reset(prefabId, components)` resets pooled components to the prefab's values (kit-common's `EntityPool` uses it).

## 3. Variants without code
A variant is another prefab that changes a few values. The red slime is the slime with a palette, more health and a higher score. Generators (`assets-src/*/make_assets.py`) write such families of prefabs from one function. By hand, copy the file and edit it.

## 4. Placing prefabs in maps
See [Tilemaps and Tiled](Tilemaps-and-Tiled): an object's Class is the prefab ID, and its properties are overrides.

## 5. Hot reload and the inspector
- Saving a prefab in a dev run affects entities spawned afterwards.
- The dev tools' **Inspector** edits live components and has **Copy as prefab JSON**, so you can tune in-game and paste the result into the file.

## 6. Best practices
- Put tuning values (speeds, health, damage) in prefabs, not in code.
- Name prefabs by path (`enemy/slime`, `pickup/coin`, `fx/splat`) and mirror that in folders.
- Keep runtime-only fields (timers, counters) public but documented as runtime. They are settable from data, which is harmless.
- Run your data tests: every demo has one that checks every prefab's components are registered.
