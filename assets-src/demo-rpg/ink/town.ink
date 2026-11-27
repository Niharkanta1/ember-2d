// demo-rpg conversations (I-450). Each Interactable's action names a knot here; the game jumps to it.
// The EXTERNAL functions are bound by the game (Game.java); the fallbacks below let the story run in Inky.
EXTERNAL has(item)
EXTERNAL give(item, count)
EXTERNAL take(item, count)
EXTERNAL flag(key)
EXTERNAL set_flag(key)

-> END

=== elder ===
{
- flag("quest.done"):
    Grik: The cellar is quiet again, thanks to you. Mind the stairs!
- has("tonic"):
    Grik: Is that... my tonic! You found it!
    ~ take("tonic", 1)
    ~ set_flag("quest.done")
    Grik: Keep the other bottle, friend. You have earned it.
- has("key.cellar"):
    Grik: The cellar door is in the old hall, just north of here. Bring back my tonic!
- else:
    -> ask
}
-> END

= ask
{ ask > 1:
    Grik: Back again? Changed your mind?
- else:
    Grik: Ah, a knight! Just in time.
    Grik: My cellar is full of strange noises, and my tonic is down there.
}
+ [I'll take a look.]
    Grik: Bless you. Here is the key.
    ~ give("key.cellar", 1)
    You received the cellar key.
+ [Not today.]
    Grik: Pity. Come back if you change your mind.
- -> END

=== cellar_door ===
{
- has("key.cellar"):
    ~ set_flag("door.cellar")
    The key turns. The old door creaks open.
- else:
    The door is locked. Someone in town must have the key.
}
-> END

=== chest ===
~ give("tonic", 2)
~ set_flag("chest.cellar")
You found two bottles of tonic.
-> END

=== chest_empty ===
The chest is empty.
-> END

=== slime ===
{ flag("quest.done"):
    Slime: Blub. Tell the old goblin I said hello.
- else:
    Slime: Blub. Leave me be.
}
-> END

=== function has(item) ===
~ return false

=== function give(item, count) ===
~ return false

=== function take(item, count) ===
~ return false

=== function flag(key) ===
~ return false

=== function set_flag(key) ===
~ return false
