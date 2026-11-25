# F-011 — Audio

- Status: Draft
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
