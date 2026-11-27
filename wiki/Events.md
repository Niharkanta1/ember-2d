# Events

Package `ember.event` (`EventBus`, `Subscription`). Spec F-019.

## 1. One bus, two delivery modes
```java
record CoinCollected(int coin, int value) {}

context.events().on(CoinCollected.class, e -> score += e.value());   // subscribe (scene-scoped)
context.events().queue(new CoinCollected(coin, 10));                  // delivered at the next flush point
context.events().publish(new MenuOpened());                           // delivered now
```
| Mode | Delivered | Use for |
| --- | --- | --- |
| `publish` | immediately, in the caller's stack | UI, input, anything that must react now |
| `queue` | at the next flush: after each fixed step, after `UPDATE`, after `LATE` | gameplay, between systems |

- **Matching:** handlers match the event's **exact** class and run in subscription order.
- **Lifetime:** `on(...)` made while a scene is active is removed when the scene exits. `onGlobal(...)` stays until you call `cancel()` on the returned `Subscription`.
- **Errors:** an exception in a handler is logged and the other handlers still run. In dev mode, the first one per frame is rethrown after delivery, so it is not missed.
- **Loops:** events that keep queuing more events are cut off after 8 passes per flush, with a warning.
- **Monitoring:** `monitor(consumer)` sees every event. The dev tools' Events panel uses it.

## 2. Events the engine and kits publish
| Event | Package | When |
| --- | --- | --- |
| `AppSuspended`, `AppResumed` | `event` | platform suspend and resume |
| `InputContextChanged` | `input` | the input context stack changed |
| `AssetReloaded`, `AssetReloadFailed` | `assets` | hot reload |
| `EntitySpawned` | `data` | a prefab was spawned |
| `TriggerEntered`, `TriggerExited`, `ActorSquished` | `collision` | trigger contacts, an actor crushed by a solid |
| `AnimationEvent`, `AnimationStateEntered`, `AnimationStateExited` | `animation` | clip frame events, state machine changes |
| `ScrollFinished` | `camera` | a scrolling camera reached its end |
| `FlagChanged` | `save` | a world flag changed value |
| `LanguageChanged` | `i18n` | the language changed |
| `Damaged`, `Died`, `ZoneEntered`, `ZoneExited`, `Spawned`, `SpawnerFinished` | kit-common | damage, death, trigger zones, spawners |
| `Jumped`, `Landed`, `Dashed` | kit-platformer | movement moments |
| `StepFinished` | kit-topdown | a grid step ended |
| `ShotEvents.Fired`, `ReloadStarted`, `Reloaded` | kit-topdown `shooter` | weapons (reused objects) |
| `Interacted`, `InteractionFocus`, `ItemPickedUp` | kit-topdown `rpg` | interaction, pickups |
| `BulletHit`, `Grazed`, `BulletCleared` | kit-bullets | bullets |

## 3. Patterns
**Sounds and juice from events.** Gameplay code doesn't call audio. The scene wires events to effects:
```java
context.events().on(Jumped.class, e -> audio.playVaried("sfx.jump", 0.06f));
context.events().on(Damaged.class, e -> {
    if (e.target() == player) {
        audio.play("sfx.hurt");
        input.vibrate(60);
        context.renderer().camera().addTrauma(0.4f);
    }
});
```

**Reacting to persistent state** (the RPG demo opens doors when a story sets a flag):
```java
context.events().on(FlagChanged.class, e -> showFlags(true));
```

**Your own events:** records are ideal: immutable, with a short declaration. Queue them from systems, and publish them from UI code.

## 4. Common mistakes
| Symptom | Cause | Fix |
| --- | --- | --- |
| A handler never fires | subscribed to a supertype or interface | subscribe to the exact class |
| A handler stops after a scene change | `on` is scene-scoped | `onGlobal` for app-wide handlers |
| Reaction one step late | `queue` where immediate was needed | `publish` |
| A `ShotEvents` field has a strange value later | these events are reused objects | copy fields inside the handler |
