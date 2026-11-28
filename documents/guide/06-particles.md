# 06 — Particles

This chapter adds particle effects (sparks, dust, smoke, magic) to a game with `ember.particles` (F-014 part B, I-510). The examples come from the shooter demo, which uses hit sparks and running dust.

> Status: draft for the developer's review at the Phase 6 gate. Not approved yet.

## 1. What you get
- **`Particles`:** loads libGDX particle effects (`.p` files) from the manifest, scales them from pixels to world units, pools them, and draws them inside a render layer.
- **`Particles.Effect`:** a handle to one running effect: move it, stop it, free it.
- **`ParticleSource`** (component) and **`ParticleSystem`:** an effect that follows an entity and is freed with it.

## 2. Make an effect
Effects are libGDX particle files: a `.p` text file plus its images. You have two ways to make one.

1. **Visually:** GDX Particle Editor, libGDX's official editor (a separate desktop app, download it from libgdx.com). Save the `.p` file, then copy it and its images into `core/assets/fx/`.
2. **In code**, as the shooter demo does with `assets-src/demo-shooter/fx/MakeEffects.java`. It is useful when you want exact, reviewable numbers. One trap: an emitter built in code starts fully transparent, so call `getTransparency().setHigh(1f)`.

The demo's effects were made the second way. Files from GDX Particle Editor use the same libGDX format but have not been tried with Ember2D yet.

Then register the files:
```sh
./gradlew assetManifest -Pgame=<game>
```
It adds `fx/hit.p` as `fx.hit` (type `particle`) and leaves the effect's images out of the manifest, because the effect loads them itself.

## 3. Show effects in a scene
```java
// enter()
Particles particles = new Particles(context.assets(), context.renderer().pixelsPerUnit(), "fx");
context.renderer().setLayers(new RenderLayer("world", SortMode.Y_SORT), new RenderLayer("fx", SortMode.NONE));
sprites.addLayerContent(particles);            // drawn in the "fx" layer, above the world

// on a hit
particles.spawn("fx.hit", x, y);               // a one-shot: freed when it completes

// update(delta), while not paused
particles.update(delta);

// exit()
particles.clear();
```
The shooter spawns sparks when anything but the player is damaged:
```java
context.events().on(Damaged.class, e -> {
    Transform t = world.get(e.target(), Transform.class);
    if (t != null && e.target() != player) {
        particles.spawn("fx.hit", t.position.x, t.position.y + 0.45f);
    }
});
```
It also spawns running dust every 0.14 s while the player moves.

## 4. Effects that follow an entity
A torch, a burning enemy or a rocket trail:
```json
"ParticleSource": { "effect": "fx.torch", "offsetY": 0.8 }
```
```java
world.addSystem(new ParticleSystem(world, particles));   // LATE: after movement
```
- **Following:** the effect starts with the entity, follows it, and is freed when the entity goes away.
- **One-shots as entities:** with `"despawnWhenDone": true`, the entity is destroyed when its non-looping effect ends. Use this for an explosion prefab spawned where something died.

## 5. Performance
- **Pools:** there is one pool per effect. After warm-up, a steady stream of hits allocates nothing (tested).
- **Particle counts:** keep `maxParticleCount` small for one-shots (the demo's sparks use 12). Many small effects are cheaper than one huge one.
- **Pausing:** effects run on game time. Don't call `update` while paused, so they freeze with the game.

## 6. Checklist
- [ ] Effect files and images in `assets/fx/`, registered with `assetManifest`.
- [ ] `Particles` made with the renderer's pixels-per-unit and attached to a layer that exists.
- [ ] `particles.update(delta)` every frame; `particles.clear()` in `exit()`.
- [ ] If an effect spawns but nothing shows: check its transparency (section 2).

Reference: wiki page Particles; spec `specs/F-014-*.md`.
