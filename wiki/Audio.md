# Audio

Package `ember.audio` (`Audio`, `AudioCategory`). Spec F-011.

## 1. Assets
```json
{ "id": "sfx.jump",    "path": "sfx/jump.wav",    "type": "sound", "group": "global", "maxInstances": 2 },
{ "id": "music.level", "path": "music/level.wav", "type": "music", "group": "global" }
```
Sounds load fully into memory; keep them short. Music streams. WAV and OGG work on desktop and Android. The demos generate their WAVs with Python scripts in `assets-src/*/sound/`.

## 2. Playing
```java
Audio audio = context.audio();
audio.play("sfx.stomp");
audio.play("sfx.hit", 0.8f, 1.1f, 0f);       // volume, pitch, pan
audio.playVaried("sfx.jump", 0.06f);         // random pitch +-6 %, so repeats don't sound identical
audio.playAt("sfx.hit", worldX, worldY);     // panned by screen position; skipped when far off screen
audio.stop("sfx.alarm");
audio.setLimit("sfx.shoot", 3);              // or "maxInstances" in the manifest: a 4th stops the oldest

audio.playMusic("music.level", 0.5f);        // crossfade from the current track over 0.5 s (loops)
audio.playMusic("music.jingle", 0f, false);  // once
audio.stopMusic(1f);
audio.pauseMusic();  audio.resumeMusic();
String playing = audio.currentMusic();
```

## 3. Volumes
```java
audio.setVolume(AudioCategory.MUSIC, 0.4f);  // MASTER, MUSIC, SFX
float v = audio.volume(AudioCategory.SFX);
```
Volumes are engine settings (`audio.master`, `audio.music`, `audio.sfx`). They are applied at boot and saved by `context.saveSettings()`. A settings menu needs only:
```java
menu.addValue("Music", () -> Math.round(audio.volume(AudioCategory.MUSIC) * 100),
        v -> audio.setVolume(AudioCategory.MUSIC, v / 100f), 0, 100, 10);
```

## 4. Behaviour you can rely on
- **Suspend:** when the app is suspended, the engine pauses all audio and resumes it afterwards.
- **Game pause:** this is yours. Call `pauseMusic` and `resumeMusic` in your pause menu, as the demos do.
- **Fades:** they run on real time, so they work while the game is paused.
- **Missing files:** a missing optional sound plays silently and logs one warning.

## 5. Patterns
**Drive sounds from events**, not from gameplay code:
```java
context.events().on(Jumped.class, e -> audio.playVaried("sfx.jump", 0.06f));
context.events().on(ShotEvents.Fired.class, e -> { if (e.entity == player) audio.playVaried("sfx.shoot", 0.1f); });
```

**Music per area** (the RPG demo):
```java
if ("town".equals(game.area)) audio.playMusic("music.town", 1f); else audio.stopMusic(1f);
```

**Rapid-fire sounds:** give them `maxInstances` 2–3 so they don't pile up into noise.

## 6. Positional sound and ambience
The engine gives you:
- `play(id, volume, pitch, pan)`: any pan from -1 (left) to 1 (right);
- `playAt(id, x, y)`: pans by the position relative to the camera, and skips the sound when it is more than a view
  away. It does **not** get quieter with distance.

There is no audio listener, emitter component or ambient-zone API yet. The recipes below add them in game code. The
class `SpatialAudio` is at the end of this section; it was compiled, and its maths unit-tested, while this page was
written.

### Panning and distance falloff for one-shot sounds
```java
// a creature growls: full volume within 2 units, silent beyond 10, panned left/right by position
SpatialAudio.playAt(audio, "sfx.growl", x, y, listener.x, listener.y, 2f, 10f);

// or by hand
float volume = SpatialAudio.falloff(distance, 2f, 10f);   // 1 near, 0 far, a smooth curve between
float pan = SpatialAudio.pan(x, listener.x, 10f);          // -1..1
audio.play("sfx.growl", volume, 1f, pan);
```
**The listener** is whatever position you pass: the camera centre (`context.renderer().camera().position`) for a
side view, the player for a top-down game, where you want to hear what the hero hears.

### Looping ambience: a point source or a zone
`SpatialAudio.AmbientLoop` plays a looping sound straight on libGDX's `Sound`, so its volume and pan can change
while it plays. It fades in and out, and it respects the player's master and sound volumes.
```java
// a fire: louder as you approach, panned
AmbientLoop fire = new AmbientLoop(context.assets(), audio, "sfx.fire");
fire.x = 12f;  fire.y = 4f;  fire.near = 1.5f;  fire.far = 8f;  fire.volume = 0.8f;

// a zone: the river room hums while the player is inside it, fading over 1.5 s
AmbientLoop river = new AmbientLoop(context.assets(), audio, "sfx.river");
river.zone = new Rectangle(30f, 0f, 20f, 12f);
river.fadeSeconds = 1.5f;

// update(delta)
fire.update(delta, player.x, player.y);
river.update(delta, player.x, player.y);

// exit(): stop them, or they keep looping into the next scene
fire.stop();  river.stop();
```
- **Zones from Tiled:** draw rectangles on an object layer (for example `audio`), with the sound in a property, and
  make one `AmbientLoop` per rectangle when the level loads.
- **Several zones overlapping:** each fades on its own, which gives a crossfade when you walk from one into the next.
- **A loop must be short:** Android plays sounds from memory (libGDX `SoundPool`, about 1 MB decoded per sound), so
  keep loops to a few seconds. For long ambience (wind, rain, a crowd), play a `music` asset alongside the
  soundtrack with libGDX's `Music` (`assets.get("music.rain", Music.class)`, `setLooping`, `setVolume`, `setPan`).
  The engine's `playMusic` has one track, for the soundtrack.
- **Pausing:** the engine pauses the sounds it played itself when the app is suspended. On Android, libGDX also
  pauses every sound when the app goes to the background. For your own pause menu, stop or quiet the loops
  yourself.

### Music by place
A music change at an area's border is `audio.playMusic("music.cave", 2f)`, a 2 s crossfade, when the player
enters a zone. Combine it with an ambient loop for the area's sound bed.

### SpatialAudio.java
```java
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import dev.ember2d.assets.Assets;
import dev.ember2d.audio.Audio;
import dev.ember2d.audio.AudioCategory;

/**
 * Positional sound as game code (wiki recipes), on top of the engine's {@link Audio}: distance falloff and panning
 * for one-shot sounds, and looping ambience that follows a point or fills a zone.
 */
public final class SpatialAudio {

    private SpatialAudio() {
    }

    /** 1 within {@code near}, 0 beyond {@code far}, a smooth curve between. */
    public static float falloff(float distance, float near, float far) {
        if (distance <= near) {
            return 1f;
        }
        if (distance >= far) {
            return 0f;
        }
        float t = 1f - (distance - near) / (far - near);
        return t * t;
    }

    /** -1 (left) .. 1 (right) from the horizontal offset to the listener; {@code spread} = offset for full pan. */
    public static float pan(float sourceX, float listenerX, float spread) {
        return MathUtils.clamp((sourceX - listenerX) / spread, -1f, 1f);
    }

    /**
     * A one-shot sound at a world position, quieter with distance and panned. Returns the instance ID, or -1 when it
     * is out of earshot.
     */
    public static long playAt(Audio audio, String soundId, float x, float y, float listenerX, float listenerY,
            float near, float far) {
        float volume = falloff(Vector.distance(x, y, listenerX, listenerY), near, far);
        if (volume <= 0.01f) {
            return -1;
        }
        return audio.play(soundId, volume, 1f, pan(x, listenerX, far));
    }

    /** The volume the engine would use for a sound: master x sfx. Loops played straight on libGDX need it. */
    public static float sfxVolume(Audio audio) {
        return audio.volume(AudioCategory.MASTER) * audio.volume(AudioCategory.SFX);
    }

    /**
     * A looping sound (a fire, a waterfall, wind) that follows a point or fills a zone. It plays straight on libGDX's
     * {@link Sound} so its volume and pan can change while it plays, and it fades in and out. Call {@link #update}
     * every frame and {@link #stop} in the scene's {@code exit()}.
     */
    public static final class AmbientLoop {
        public float volume = 1f;
        /** Seconds to fade fully in or out. */
        public float fadeSeconds = 1.5f;
        /** For a point source: full volume within near, silent beyond far. */
        public float near = 2f;
        public float far = 10f;
        /** A zone instead of a point (null = point source at x, y). Inside = full volume. */
        public Rectangle zone;
        public float x;
        public float y;

        private final Audio audio;
        private final Sound sound;
        private long id = -1;
        private float level;

        public AmbientLoop(Assets assets, Audio audio, String soundId) {
            this.audio = audio;
            this.sound = assets.get(soundId, Sound.class);
        }

        /** Updates the loop from the listener's position (usually the camera or the player). */
        public void update(float delta, float listenerX, float listenerY) {
            float target;
            float panValue;
            if (zone != null) {
                target = zone.contains(listenerX, listenerY) ? 1f : 0f;
                panValue = 0f;
            } else {
                target = falloff(Vector.distance(x, y, listenerX, listenerY), near, far);
                panValue = pan(x, listenerX, far);
            }
            float step = fadeSeconds <= 0f ? 1f : delta / fadeSeconds;
            level = target > level ? Math.min(target, level + step) : Math.max(target, level - step);
            float v = level * volume * sfxVolume(audio);
            if (v > 0.001f && id == -1) {
                id = sound.loop(v, 1f, panValue);
            } else if (v <= 0.001f && id != -1) {
                sound.stop(id);
                id = -1;
            } else if (id != -1) {
                sound.setPan(id, panValue, v);
            }
        }

        /** The loop's current fade level, 0..1. */
        public float level() {
            return level;
        }

        public void stop() {
            if (id != -1) {
                sound.stop(id);
                id = -1;
            }
            level = 0f;
        }
    }

    private static final class Vector {
        static float distance(float x1, float y1, float x2, float y2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
    }
}
```

Lighting, fog, glow and bloom for the same scenes are in [Lighting and Atmosphere](Lighting-and-Atmosphere).
