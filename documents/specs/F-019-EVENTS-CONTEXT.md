# F-019 — Events and EngineContext

- Status: `EngineContext` part Ready (approved in I-010, 2026-11-25: `config()`, `service()`, `findService()`, `Builder.register()`; typed accessors arrive with each service). `EventBus` part: expanded, API awaiting approval in I-030
- Phase: 1
- Module / package: `ember-core` / `ember.event`, `ember.core`
- Depends on: F-001, F-002
- Tasks: I-010, I-030

## Goal
One messaging model for the whole engine and one explicit object that gives systems access to services, replacing ad-hoc listeners and global service locators.

## Scope
- `EventBus` with immediate and queued delivery.
- Typed events (records or small classes), pooled where high-frequency.
- Scene-scoped subscriptions.
- `EngineContext`: the service container passed to scenes and systems.

## Non-goals
- Cross-thread messaging, priorities, event sourcing or replay.
- Dependency-injection frameworks or annotation scanning.

## User-facing behaviour
- Engine subsystems publish their notifications on the bus: collision/trigger contacts, animation frame events, input context changes, scene changes, asset reloads, app lifecycle.
- Gameplay code subscribes in a scene's `enter` and never needs to unsubscribe manually.
- Queued events published during the `fixed` phase are delivered after that fixed step; those published in `update` are delivered before `late`.

## API sketch
```java
@PublicApi
public interface EventBus {
    <E> Subscription on(Class<E> type, Consumer<E> handler);    // scoped to current scene when called from a scene
    <E> Subscription onGlobal(Class<E> type, Consumer<E> handler);
    void publish(Object event);                                  // immediate: handlers run now
    void queue(Object event);                                    // delivered at next flush point
}

@PublicApi
public interface Subscription { void cancel(); }

@PublicApi
public final class EngineContext {
    public Time time();       public Timers timers();    public Tweens tweens();
    public EventBus events(); public Assets assets();    public Input input();
    public Renderer renderer(); public Audio audio();     public SaveStore saves();
    public Settings settings(); public SceneManager scenes();
    public <T> T service(Class<T> type);                 // game-registered services
    public static final class Builder {
        public <T> Builder register(Class<T> type, T service);
        public EngineContext build();
    }
}
```

## Data model
Example engine events (records):
`ContactBegan(int a, int b, Vector2 normal)`, `ContactEnded(int a, int b)`, `TriggerEntered(int trigger, int other)`, `TriggerExited(int trigger, int other)`, `AnimationEvent(int entity, String name)`, `SceneEntered(String id)`, `AssetReloaded(String assetId)`, `AppSuspended()`, `AppResumed()`.

## Dependencies
`ember.core`; libGDX `utils`.

## Lifecycle/resource ownership
- Flush points: after each fixed step, after `update`, after `late`.
- Subscriptions made through a scene's context are cancelled on scene exit.
- Events queued during a flush are delivered in the same flush, up to 8 passes; further events are deferred to the next flush point and a warning is logged (prevents infinite loops).

## Error handling
- An exception in a handler is logged with event type and handler class; delivery continues to other handlers. In dev mode the first such exception per frame is rethrown after the flush.
- `service(type)` for an unregistered type throws with the type name and the list of registered services.

## Performance considerations
No allocation for delivery; handlers stored in arrays per type. High-frequency events (contacts) are pooled and must not be retained by handlers.

## Worked example
```java
public void enter(EngineContext ctx) {
    ctx.events().on(TriggerEntered.class, e -> {
        if (world.has(e.trigger(), Goal.class) && world.has(e.other(), PlayerTag.class)) ctx.scenes().replace(new LevelComplete());
    });
}
```

## Acceptance criteria
- Immediate events reach handlers before `publish` returns.
- Queued events are delivered at the documented flush points, in publish order.
- Scene exit cancels scene subscriptions; global ones survive.
- Re-entrancy limit works and warns.
- Context builder exposes engine and game services; tests can build one with fakes.

## Tests
Unit tests for delivery order, flush points, scoping, re-entrancy and handler exceptions.

## Risks/open questions
- Whether to deliver contacts as events only, or also as a per-entity contact list component for polling. Decide during I-120.
