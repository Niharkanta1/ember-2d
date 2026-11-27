# Guide: Building a Bullet-Hell Game

This guide builds a bullet-hell boss game with **kit-bullets**:
- a small player hitbox;
- grazing for points;
- a boss with three phases of patterns;
- bullet clearing into coins;
- thousands of bullets at 60 fps.

Movement and health come from kit-topdown and kit-common. The finished reference is the shooter demo's boss stage (`BossScene`) and its patterns (`patterns/boss1..3.json`). A scrolling shoot-'em-up variant is in section 9.

Reference pages: [kit-bullets](Kit-Bullets), [kit-common](Kit-Common), [kit-topdown](Kit-Topdown).

---

## 1. Project bootstrap
Start from `demos/demo-shooter` (it has every piece) or from the shooter guide's project. The bullet-hell parts of the component setup:
```java
CommonKit.register(registry);    // Health, Team, Hitbox, Hurtbox, ...
BulletsKit.register(registry);   // BulletTarget
TopDownKit.register(registry);   // TopDownBody
ShooterKit.register(registry);   // Aim, Weapon (the player's gun)
registry.register(CameraFollow.class, CameraFollow::new).register(Boss.class, Boss::new);
```
`Boss` is your component: `public int phase;`.

## 2. Asset management
Bullets need four kinds of data, all `json` assets:
```json
{ "id": "bullets", "path": "bullets.json", "type": "json", "group": "global" },
{ "id": "pattern.boss1", "path": "patterns/boss1.json", "type": "json", "group": "global" },
{ "id": "pattern.boss2", "path": "patterns/boss2.json", "type": "json", "group": "global" },
{ "id": "pattern.boss3", "path": "patterns/boss3.json", "type": "json", "group": "global" },
{ "id": "weapon.smg", "path": "weapons/smg.json", "type": "json", "group": "global" }
```
Bullet images are atlas regions. Small round orbs of 8 × 8 pixels read well:
```json
{ "version": 1, "types": {
    "player":      { "region": "atlas.shooter/bullet.player", "radius": 0.12, "damage": 1, "lifetime": 1.5, "walls": true, "rotate": true },
    "orb_magenta": { "region": "atlas.shooter/orb.magenta", "radius": 0.16, "lifetime": 9 },
    "orb_red":     { "region": "atlas.shooter/orb.red", "radius": 0.16, "lifetime": 9 },
    "orb_blue":    { "region": "atlas.shooter/orb.blue", "radius": 0.16, "lifetime": 9 },
    "orb_green":   { "region": "atlas.shooter/orb.green", "radius": 0.16, "lifetime": 9 } } }
```
The boss arena is a small walled Tiled map (`maps/boss.tmx`) with `player`, `camera` and `boss/eye` objects.

## 3. Audio and music
```java
audio.playMusic("music.boss", 0.5f);                                            // in built()
context.events().on(Grazed.class, e -> { run.score += 1; audio.play("sfx.graze"); });
// phase change:
audio.play("sfx.boss_phase");
context().renderer().camera().addTrauma(0.6f);
```
Give `sfx.graze` and `sfx.hit` `"maxInstances": 3`. Grazes can fire dozens of times a second.

## 4. Animations and effects
- **Hit flash on the boss:** `"HitFlash": { "seconds": 0.06 }` with `HitFlashSystem`. While it's being hit, the boss flickers white ([Sprite Materials](Sprite-Materials)).
- **Hit sparks:** `particles.spawn("fx.hit", ...)` on `Damaged` ([Particles](Particles)).
- **Screen shake** on phase changes and when the player is hit.
- **Rotating bullets:** `"rotate": true` makes the player's bullets face their direction.

## 5. Screen transitions
```java
context.scenes().replace(new BossScene(run), Transition.fade(0.8f));   // from the room, or straight from the menu
context.scenes().preload("boss");                                       // while the previous stage runs
```
The demo's menu has "Skip to boss (dev)" in dev builds, so you can test the fight quickly:
```java
if (context.config().devMode) {
    menu.add("Skip to boss (dev)", () -> context.scenes().replace(new BossScene(new Run()), Transition.fade(0.4f)));
}
```

## 6. Scoring
| Event | Points |
| --- | --- |
| a graze (`Grazed`, once per bullet) | +1 |
| a cleared bullet: every 6th becomes a coin | +5 per coin |
| killing the boss | +500 |
The best score goes to global data ([Guide: Top-Down Shooter](Guide-Top-Down-Shooter), section 6).

```java
context.events().on(BulletCleared.class, e -> {
    if (clearedCount++ % 6 == 0) context.prefabs().spawn(world, "pickup/coin", e.x(), e.y());
});
```

## 7. UI
- **Boss health bar** across the top:
```java
Table top = new Table(ui().skin());
top.setFillParent(true);
top.top().padTop(4f);
top.add(new Label("THE EYE", ui().skin(), "title")).padRight(6f);
bar = new ProgressBar(0f, 1f, 0.01f, false, ui().skin());
top.add(bar).width(110f);
ui().stage().addActor(top);
// tick: bar.setValue(health.current / (float) health.max);
```
- **HUD:** HP, score and ammo (shared with the room stage).
- **End panels:** Victory and You died, with the score and the best score.

## 8. Gameplay
### Player: a small hitbox and a graze radius
```json
"BulletTarget": { "radius": 0.18, "grazeRadius": 0.9, "offsetY": 0.45 },
"Health": { "max": 5, "invulnerableSeconds": 1.0 },
"Team": { "id": 1 }
```
The bullet hit radius is far smaller than the sprite, which is the genre's rule. `grazeRadius` rewards near misses.

### The boss prefab
```json
{ "version": 1, "components": {
    "Sprite": { "regionId": "atlas.shooter/boss", "origin": [24, 0], "z": 1 },
    "Health": { "max": 300, "invulnerableSeconds": 0 },
    "Team": { "id": 2 },
    "BulletTarget": { "radius": 1.2, "offsetY": 1.5 },
    "Hitbox": { "box": { "x": -1.2, "y": 0.3, "width": 2.4, "height": 2.4 }, "damage": 1, "knockback": 10 },
    "Boss": {}, "HitFlash": { "seconds": 0.06 } } }
```

### Three phases by health
```java
@Override
protected void tick(float delta) {
    Health h = world.get(boss, Health.class);
    if (h == null || h.dead) return;
    float ratio = h.current < 0 ? 1f : h.current / (float) h.max;
    int phase = ratio > 0.66f ? 1 : ratio > 0.33f ? 2 : 3;
    Boss b = world.get(boss, Boss.class);
    if (phase != b.phase) {
        if (b.phase != 0) {
            bullets.clear(ENEMY_TEAM, true);        // clear the screen into coins
            audio.play("sfx.boss_phase");
            context().renderer().camera().addTrauma(0.6f);
        }
        b.phase = phase;
        bullets.stop(handle);
        handle = -1;
    }
    if (handle < 0 || !bullets.running(handle)) {    // run the phase's pattern again whenever it ends
        Transform t = world.get(boss, Transform.class);
        handle = bullets.runAt("pattern.boss" + b.phase, t.position.x, t.position.y + 1.5f, player, ENEMY_TEAM);
    }
}

@Override
protected void died(int entity) {
    if (entity == boss) {
        bullets.stop(handle);
        bullets.clear(ENEMY_TEAM, true);
        audio.stopMusic(1f);
        run.score += 500;
        world.destroy(boss);
        won();
    }
}
```

### The patterns
Phase 1, a spiral with aimed fans (`patterns/boss1.json`):
```json
{ "version": 1, "emit": [
  { "type": "spiral", "bullet": "orb_magenta", "count": 4, "speed": 3.5, "repeat": 80, "interval": 0.12, "angleStep": 13 },
  { "type": "aimed", "bullet": "orb_red", "count": 5, "spread": 40, "speed": 5, "repeat": 6, "interval": 1.5, "delay": 0.6 } ] }
```
Phase 2, rings and a wave:
```json
{ "version": 1, "emit": [
  { "type": "ring", "bullet": "orb_blue", "count": 24, "speed": 3, "repeat": 6, "interval": 1.6 },
  { "type": "wave", "bullet": "orb_green", "count": 3, "spread": 60, "speed": 4, "repeat": 50, "interval": 0.18,
    "amplitude": 35, "frequency": 12 } ] }
```
Phase 3, random sprays and aimed bullets that burst into rings:
```json
{ "version": 1, "emit": [
  { "type": "random", "bullet": "orb_red", "count": 6, "spread": 140, "speed": 4, "repeat": 60, "interval": 0.15 },
  { "type": "aimed", "bullet": "orb_magenta", "speed": 2.5, "repeat": 6, "interval": 1.4, "delay": 0.5,
    "child": { "type": "ring", "bullet": "orb_blue", "count": 16, "speed": 3 }, "childAfter": 1.2 } ] }
```
**Designing patterns:** run the game with `gradlew run`, open the pattern file, change it and save. The next run of the pattern uses it. Phases rerun their pattern when it ends, so changes show within seconds.

### Bullets and walls
- Enemy orbs ignore walls (no `walls`), so they fly off screen and are culled by `setCullArea`.
- The player's bullets have `"walls": true`.
- Keep actors on collision layer 1 (`setWallMask(1, collision)` means layer 0 is the wall layer).

## 9. Additional features
- **A scrolling shoot-'em-up:** put a `CameraScroll` (`speedY` 2, `bounds`) on the camera entity instead of `CameraFollow`, and add `CameraScrollSystem(world, rig, events)`. The view moves at a constant speed, keeps entities tagged `player` inside it (`clampTag`, `margin`), and publishes `ScrollFinished` at the end. Spawn waves with kit-common spawners placed along the map. *`CameraScroll` is covered by the engine's tests, but none of the demos uses it.*
- **Emitters from code:** `bullets.fire(emitter, x, y, angle, target, team)` fires one volley; `BulletPatterns.parseEmitter` builds an emitter from JSON.
- **Stress testing:** the shooter's `StressScene` spawns 3,000 bullets and logs the bullet step time every 2 seconds. Use it on target phones.
- **Determinism:** `bullets.setSeed(seed)` makes `random` patterns repeatable.

## 10. The complete game
A standalone bullet-hell boss game:
```text
MyBulletHell, MyComponents      config + registry (section 1)
MenuScene                       Play, best score
BossScene                       arena map, player (TopDownBody + Aim + Weapon), boss, BulletSystem, HUD, boss bar
Boss                            phase
patterns/boss1..3.json          the three phases
bullets.json, weapons/smg.json  bullet types, the player's gun
```
Checklist:
- [ ] Every pattern is readable: there is always a gap to dodge through. Play each phase at 0.5 × time scale (`time().setTimeScale(0.5f)`) in a dev build.
- [ ] The frame rate holds on a mid-range phone during phase 3 (use the stress scene).
- [ ] Bullets clear on phase changes, so the player gets a breather.
