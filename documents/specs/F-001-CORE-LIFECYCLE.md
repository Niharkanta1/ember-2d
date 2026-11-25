# F-001 — Engine Core and App Lifecycle

- Status: Draft — expanded to template depth; becomes Ready when the API sketch is approved in I-010
- Phase: 1
- Module / package: `ember-core` / `ember.core`
- Depends on: F-019 (events, `EngineContext`), F-002 (time)
- Tasks: I-010

## Goal
Provide one predictable entry point that boots the engine, owns the app lifecycle, builds the `EngineContext`, runs the active scene, and shuts down cleanly.

## Scope
- `EmberGame`, an implementation of libGDX `ApplicationListener`, that games extend only by configuration (not by overriding loop logic).
- `EngineConfig`: validated startup configuration.
- App lifecycle mapping: libGDX `create/resize/render/pause/resume/dispose` → Ember2D `boot/resize/frame/suspend/resume/shutdown`.
- Construction of `EngineContext` and its services in a fixed order.
- Ownership-ordered disposal.
- Fatal error handling and reporting.

## Non-goals
- Gameplay pause (owned by `Time`, F-002).
- Multiple engine instances in one process (not supported; documented).
- Hot-swapping services at runtime.
- Scene management details (F-010).

## User-facing behaviour
- A game launches with a config and a first scene; nothing else is required.
- Alt-tab, window minimise, or the Android app going to the background (`onPause`) triggers `suspend`: audio pauses and the fixed-step accumulator is reset on `resume` so the game does not fast-forward. The game's own pause state is not changed.
- Closing the window disposes all owned resources in reverse creation order and logs any disposal failure without aborting the rest.
- Invalid config fails at startup with a message naming the field and the expected value.

## API sketch
```java
@PublicApi
public final class EngineConfig {
    public int virtualWidth = 320, virtualHeight = 180;   // pixel-perfect resolution
    public float pixelsPerUnit = 16f;
    public float fixedStep = 1f / 60f;
    public int maxCatchUpSteps = 5;
    public boolean devMode = false;                      // enables hot reload, dev tools hooks
    public String assetManifest = "assets/manifest.json";
    public Supplier<Scene> firstScene;
    public void validate();                              // throws EngineConfigException
}

@PublicApi
public class EmberGame implements ApplicationListener {
    public EmberGame(EngineConfig config);
    public final EngineContext context();                // available after create()
    protected void configureContext(EngineContext.Builder builder) {} // add game services
}

@PublicApi
public enum AppState { CREATED, RUNNING, SUSPENDED, DISPOSED }

@PublicApi
public interface AppLifecycleListener {                  // published as immediate events too
    default void onSuspend() {}
    default void onResume() {}
    default void onResize(int width, int height) {}
}
```

## Data model
`EngineConfig` is a plain object; it may also be loaded from `ember.json` in the game's assets (same field names, `version` field required).

## Dependencies
`ember.core`, `ember.event`; libGDX core. No backend types.

## Lifecycle/resource ownership
Boot order (each step's resources are disposed in reverse order on shutdown). Services whose specs are not yet implemented (audio, saves and settings arrive in Phase 3) are simply absent until then:
1. Validate config.
2. Logger.
3. `Time`.
4. `EventBus`.
5. `Assets` (loads manifest).
6. `Input`.
7. `Renderer` (batch, frame buffer, camera).
8. `Audio`.
9. `SaveStore` and `Settings`.
10. Game services from `configureContext`.
11. `SceneManager`, then push `firstScene`.

State transitions: `CREATED → RUNNING ⇄ SUSPENDED → DISPOSED`. Any other transition is rejected with `IllegalStateException`. `dispose()` after `DISPOSED` is a no-op.

## Error handling
- `EngineConfigException` for invalid config, listing every invalid field.
- An exception escaping a frame is logged with scene name, phase and system name, then rethrown in dev mode; in release mode it shows a fatal error screen and writes a crash log.
- Disposal errors are logged and collected; shutdown continues.

## Performance considerations
No per-frame allocation in `EmberGame.render`. Boot time is logged in dev mode.

## Worked example
```java
public class DesktopLauncher {
    public static void main(String[] args) {
        EngineConfig cfg = new EngineConfig();
        cfg.virtualWidth = 320; cfg.virtualHeight = 180;
        cfg.firstScene = TitleScene::new;
        new Lwjgl3Application(new EmberGame(cfg), DesktopConfig.window("My Game"));
    }
}
```
The Android launcher (`ember-android`, F-022) builds the same `EngineConfig` (usually from a shared `GameSetup` class in the game's core module) and passes the same `EmberGame` to `AndroidApplication.initialize`. Game code is identical on both platforms.

## Acceptance criteria
- Boot order and reverse disposal order are observable in logs and asserted by tests.
- Suspend/resume does not change game pause state and resets the accumulator.
- Invalid transitions rejected; repeated dispose is safe.
- Invalid config produces a clear multi-field message.
- Demo launches and shuts down with no errors in the log.

## Tests
- Unit: state machine transitions; config validation; disposal order with fake services that record calls.
- Integration (headless backend): boot → run N frames → dispose.

## Risks/open questions
- Should `EngineConfig` be immutable (builder) instead of public fields? Decide in I-010.
