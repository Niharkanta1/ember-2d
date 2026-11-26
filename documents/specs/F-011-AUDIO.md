# F-011 — Audio

- Status: I-210 Ready: expanded and approved 2026-11-26 (see "Audio service (I-210)")
- Phase: 3
- Module / package: `ember-core` / `ember.audio`
- Depends on: F-008, F-013 (volumes in settings), F-001 (suspend/resume)
- Tasks: I-210

## Goal
Simple, central audio with volume categories and music transitions.

## Scope
- Sound effects by asset ID: play, play with pitch/volume variation, stop; optional stereo pan from world position (libGDX supports pan, not true 3D positional audio).
- Music by asset ID: play, stop, loop, crossfade between tracks.
- Volume categories: master, music, sfx. Values stored in settings.
- Pause all on app suspend; resume on app resume. Game pause does not stop music unless the game asks.
- Optional per-sound concurrency limit (e.g. max 4 instances of "coin").

## Non-goals
- Five separate channels, DSP effects, true positional audio, audio buses (add when needed).

## API sketch
```java
@PublicApi public interface Audio {
    long play(String soundId); long play(String soundId, float volume, float pitch, float pan);
    void playMusic(String musicId, float fadeSeconds); void stopMusic(float fadeSeconds);
    void setVolume(Category c, float v); float volume(Category c);
}
```

## Acceptance criteria
- Volumes applied per category and persisted via settings.
- Crossfade completes in the given time.
- Suspend/resume pauses and resumes audio.
- Concurrency limit enforced.
- Demo plays SFX and music; resources released with their asset group.

## Audio service (I-210)
Status: Ready (expanded for I-210 and approved 2026-11-26; see "Decisions in I-210").

**Where it lives.**
- **Public API:** `ember.audio` (tier 3: may use `core`, `event`, `assets`).
- **Implementation:** `ember.audio.internal.GdxAudio` over libGDX `Sound` and `Music`. Both come from the asset service by ID (manifest types `sound` and `music`, already in F-008).
- **Access:** `EmberGame` creates it at boot and exposes it as `ctx.audio()` (ARCHITECTURE: `EngineContext` lists `audio`). Tests use a fake through `EngineContext.Builder.audio(...)`.

**Sound effects.**
```java
Audio audio = ctx.audio();
audio.play("sfx.jump");                               // volume 1, pitch 1, centred
audio.play("sfx.stomp", 0.8f, 1.1f, 0f);              // volume, pitch (0.5–2), pan (-1 left .. 1 right)
audio.playAt("sfx.coin", x, y);                       // pan from the world position relative to the camera
audio.playVaried("sfx.step", 0.1f);                   // random pitch within ±10% (and volume within ±10%)
audio.stop("sfx.alarm");                              // every playing instance of that sound
```
- **Return value:** each call returns the libGDX instance ID, or -1 when nothing played (volume 0, limit reached with nothing to replace, fallback sound).
- **Positional pan:** `playAt` uses a listener that the engine sets from the camera every frame.
  - The pan is `(x - camera.x) / (view width / 2)`, clamped to -1..1.
  - Sounds more than one view width outside the view are not played.
  - This is libGDX stereo pan, not 3D audio (F-011 non-goal).
- **Concurrency limit:** `audio.setLimit("sfx.coin", 4)`, or `"maxInstances": 4` on the manifest entry.
  - A new play beyond the limit stops the oldest instance of that sound.
  - There is no limit by default (libGDX's own mixer limit still applies).

**Music.**
```java
audio.playMusic("music.level1", 1.0f);   // crossfade from the current track over 1 s; loops
audio.playMusic("music.win", 0f, false); // no fade, play once
audio.stopMusic(0.5f);                    // fade out and stop
audio.pauseMusic(); audio.resumeMusic();  // the game's choice, e.g. in a pause menu
String now = audio.currentMusic();        // null when none
```
- **Crossfade:** the old track fades out while the new one fades in (linear volume, both playing), and the old one stops when its fade ends.
  - Playing the track that is already current does nothing.
  - A new `playMusic` during a fade finishes the outgoing fade immediately.
- **Fade clock:** fades run on **unscaled real time** (in the engine's frame update), so they complete while the game is paused or slowed.
- **Game pause:** pausing the game does not touch audio; the game decides.

**Volume categories.**
- `MASTER`, `MUSIC` and `SFX`, each 0–1, default 1:
  - effective sound volume = master × sfx × the call's volume
  - effective music volume = master × music × fade
- Changing a category applies at once to playing music.
- **Persistence:** volumes are kept in memory for now. I-230 (settings) stores and restores them; `setVolume` is the hook it will call. Until then a restart resets them.

**Lifecycle.**
- **Suspend:** on app suspend (minimised window, Android background) all sounds are paused, and the music is paused and remembered.
- **Resume:** everything resumes, unless the game had paused the music itself.
- **Unloading:** before an asset group is unloaded, any of its sounds that are playing are stopped, and its music is stopped at once (no fade). Nothing plays a disposed asset.
- **Missing optional files:** they use the existing silent fallbacks (F-008); playing them does nothing.

**Errors.**
- An unknown ID gives the asset service's error (it suggests the closest IDs).
- A sound ID used as music (or the reverse) is an error naming the actual type.
- Volumes, pitch and pan out of range are clamped, with one DEBUG log line.

**Demo (level 1).** No sound files exist yet: the pack's `SFX` folder holds images. So I **generate small original sounds** with a script (stdlib Python, 22 kHz mono 16-bit WAV, about 600 KB in total, committed with a note):
- **Sound effects:** jump (rising blip), stomp (pop), hurt (buzz), checkpoint (two-note chime), goal (short fanfare)
- **Music:** a 16-second chiptune loop for the level (square lead and triangle bass)
- **Wiring:**
  - `Jumped` and `Landed` events drive the jump sound; stomp, hurt, checkpoint and goal come from `LevelSystem` events.
  - The level music starts on enter. At the goal it fades out over 1 s while the fanfare plays.
  - The demo's pause (P) pauses the music.

You can replace the files with your own any time: the manifest IDs stay the same.

**Not in I-210:**
- settings UI and persistence of volumes (I-220 and I-230)
- an audio panel in the dev tools
- ducking
- streaming from URLs
- DSP effects (non-goal)

## API sketch (I-210)
```java
package dev.ember2d.audio;
@PublicApi public enum AudioCategory { MASTER, MUSIC, SFX }
@PublicApi public interface Audio {
    long play(String soundId);
    long play(String soundId, float volume, float pitch, float pan);
    long playVaried(String soundId, float variation);          // random pitch and volume within ±variation
    long playAt(String soundId, float worldX, float worldY);
    void stop(String soundId);
    void setLimit(String soundId, int maxInstances);           // 0 = no limit
    void playMusic(String musicId, float fadeSeconds);         // loops
    void playMusic(String musicId, float fadeSeconds, boolean loop);
    void stopMusic(float fadeSeconds);
    void pauseMusic(); void resumeMusic();
    String currentMusic();
    void setVolume(AudioCategory category, float volume);
    float volume(AudioCategory category);
}
// ember.app: EngineContext.audio(), EngineContext.Builder.audio(Audio) for tests;
// EmberGame updates the listener from the camera and advances fades each frame; suspend/resume wired.
// assets: optional "maxInstances" on sound entries; GdxAssets tells the audio service before unloading a group.
```

## Tests (I-210)
- **Headless** (libGDX headless audio is mocked, so these use recording fakes of `Sound` and `Music` served by a test asset service):
  - play with category volumes, pitch and pan, and clamping
  - `playVaried` stays within its range (seeded random)
  - `playAt`: the pan from the listener, and culling far away
  - the limit stops the oldest instance
  - crossfade volumes over time, including a new track mid-fade and the same track again
  - `stopMusic` fade, and `pauseMusic`/`resumeMusic`
  - fades advance while the game is paused
  - volume changes apply to playing music
  - suspend/resume pauses and resumes, and keeps a game-paused track paused
  - unloading a group stops its sounds and music
  - errors for wrong types and unknown IDs
  - `EmberGame` wiring: `ctx.audio()` exists and is advanced each frame
- **Manual:**
  - you listen on desktop (jump, stomp, hurt, checkpoint, goal, music loop and crossfade, pause)
  - I check on the emulator that it starts with audio and keeps running after background and resume (by log; the emulator's sound output may not reach you)

## Decisions in I-210
- **Building the service:** `EngineContext.Builder.build()` creates a `GdxAudio` over the asset service unless `Builder.audio(...)` set one. A context with neither throws on `audio()`, like `assets()`.
- **Engine wiring** (`EmberGame`):
  - At boot it applies manifest `maxInstances` limits and connects `GdxAssets.setBeforeRelease(audio::release)`.
  - Each frame it advances fades by the unscaled delta and sets the listener from the `CameraRig` (centre and half view size).
  - `pause()`/`resume()` suspend and resume audio, and `dispose()` stops everything.
- **Before release:** an asset is released to the audio service when its group's last reference is about to be unloaded, and also before hot reload (I-150) replaces it, so a reloaded track or sound is never played after disposal.
- **Instance order:** the per-sound instance list keeps play order. The first version used an unordered libGDX `LongArray`, so `removeIndex(0)` stopped the wrong instance; the limit test found it.
- **While suspended,** `play` returns -1 (nothing starts in the background).
- **Fades during the game's music pause:** they wait (they resume with the music), so a fade-in is not lost while a pause menu is open.
- **Demo:**
  - The demo publishes `EnemyStomped`, `PlayerHurt` and `CheckpointReached` (records in the demo) from `LevelSystem`. `DemoScene` maps them, the kit's `Jumped` and `LevelCompleted` to sounds, so `LevelSystem` stays free of audio.
  - `sfx.jump` has `maxInstances` 2 and uses `playVaried(0.06)`.
  - The music fades in over 0.5 s on enter and is kept through hot reload restarts (same track: nothing happens).
- **Sound files:**
  - Generated by `assets-src/demo-platformer/sound/make_sounds.py` (standard library only): 776 KB of 22 050 Hz mono WAV, of which the 16 s music loop is about 700 KB.
  - `ART-CREDITS.txt` names them as original.
- **Unrelated fix found by this build:** `DirectoryWatcher` (I-150) could miss a file written into a new folder before the folder was registered.
  - It now reports the files already inside a newly watched folder.
  - Its test writes the file immediately instead of waiting, and passed three runs in a row.
- **Checks:**
  - Emulator R8 release: the game starts with the music track open (logcat shows an AudioTrack), suspends and resumes across Home and back without errors.
  - Whether it sounds right was not checked by me; that is the developer's desktop check.
