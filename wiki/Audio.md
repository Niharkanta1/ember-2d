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
