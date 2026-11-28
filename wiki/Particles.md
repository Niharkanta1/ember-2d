# Particles

Package `ember.particles` (`Particles`, `ParticleSource`, `ParticleSystem`). Spec F-014 part B, ADR-014. Ember2D wraps libGDX's `ParticleEffect` and adds pooling, world units, render layers and entity attachment.

## 1. Effect files
Effects are libGDX particle editor files (`.p`), with their images next to them. Design them in **GDX Particle Editor** and register them with `assetManifest` ([Working Without an Editor](Working-Without-an-Editor#7-particle-effects)).
```json
{ "id": "fx.hit",  "path": "fx/hit.p",  "type": "particle", "group": "global" },
{ "id": "fx.dust", "path": "fx/dust.p", "type": "particle", "group": "global" }
```
The older "2D Particle Editor" from gdx-tools writes the same format. You can also build effects in code and save them with libGDX itself; the shooter demo does that (`assets-src/demo-shooter/fx/MakeEffects.java`):
```java
ParticleEmitter e = new ParticleEmitter();
e.setName("sparks");
e.setImagePaths(Array.with("dot.png"));
e.setContinuous(false);
e.getTransparency().setHigh(1f);                    // IMPORTANT: a bare emitter starts fully transparent
e.getTransparency().setScaling(new float[] {1f, 0f});
e.getTransparency().setTimeline(new float[] {0f, 1f});
e.setMaxParticleCount(12);
e.getDuration().setLow(60f);                        // ms
e.getEmission().setHigh(150f);                      // per second
e.getLife().setHigh(180f, 300f);                    // ms
e.getXScale().setHigh(5f, 8f);                      // pixels
e.getVelocity().setActive(true);  e.getVelocity().setHigh(40f, 90f);   // pixels per second
e.getAngle().setActive(true);     e.getAngle().setHigh(0f, 360f);
e.getTint().setColors(new float[] {1f, 0.75f, 0.25f, 1f, 0.3f, 0.1f});
e.getTint().setTimeline(new float[] {0f, 1f});
e.setAdditive(true);
ParticleEffect effect = new ParticleEffect();
effect.getEmitters().add(e);
effect.save(writer);                                // the editor's .p format
```
Run it with the libGDX jar on the class path:
```sh
java -cp path/to/gdx-1.14.2.jar assets-src/demo-shooter/fx/MakeEffects.java
```

## 2. Using effects
```java
Particles particles = new Particles(context.assets(), context.renderer().pixelsPerUnit(), "fx");
sprites.addLayerContent(particles);                          // drawn in the "fx" render layer

particles.spawn("fx.hit", x, y);                             // one-shot: freed when complete
Particles.Effect smoke = particles.spawn("fx.smoke", x, y);
smoke.position(nx, ny);  smoke.stop();  smoke.isComplete();  smoke.free();

// every frame (game time; skip while paused):
particles.update(delta);
// in exit():
particles.clear();
```
- **Units:** effects are authored in pixels. Each effect's prototype is scaled once by 1 / pixels-per-unit, so you place effects in world units. The asset itself is not changed.
- **Pools:** there is one libGDX `ParticleEffectPool` per effect, created on first use. Completed effects return to it, and handles are pooled too. After warm-up, a steady stream of hits allocates nothing; a test checks this.
- **Handles:** an `Effect` handle is invalid once freed (`isValid()`). `generation()` tells you whether a kept handle was reused for another effect.

## 3. Effects on entities
```json
"ParticleSource": { "effect": "fx.torch", "offsetY": 0.8 }
```
```java
world.addSystem(new ParticleSystem(world, particles));   // LATE, after movement
```
- **Following:** the system starts the effect, keeps it at the entity plus the offset, and frees it when the entity is removed.
- **One-shots:** with `despawnWhenDone`, the entity is destroyed when its effect completes. Use this for a one-shot effect placed as an entity, such as a prefab spawned at a hit.
- **No restart:** `finished` (runtime) is set when a non-looping effect has completed, so it doesn't start again.

## 4. In the shooter demo
```java
particles = new Particles(context.assets(), ppu, "fx");
sprites.addLayerContent(particles);
context.events().on(Damaged.class, e -> {
    Transform t = world.get(e.target(), Transform.class);
    if (t != null && e.target() != player) particles.spawn("fx.hit", t.position.x, t.position.y + 0.45f);
});
// in update, while playing:
particles.update(delta);
if (body.moving && (dustWait -= delta) <= 0f) {
    dustWait = 0.14f;
    particles.spawn("fx.dust", t.position.x, t.position.y + 0.05f);
}
```

## 5. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| Effects spawn but nothing shows | a code-built emitter has transparency "high" 0 | `getTransparency().setHigh(1f)` (the editor does this) |
| Effects are huge | `Particles` built with the wrong pixels-per-unit | pass `renderer().pixelsPerUnit()` |
| Effects never appear | not attached with `addLayerContent`, or the layer name doesn't exist | attach it; declare the layer |
| Effects freeze | `update` not called | call `particles.update(delta)` every frame |
| Effects stay after a scene change | not cleared | `particles.clear()` in `exit()` |
