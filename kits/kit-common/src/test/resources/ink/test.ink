// DialogueRunner test story (I-420).
EXTERNAL give(item)
VAR gold = 3
VAR met_guard = false

Guard: Halt! Who goes there? #speaker:guard #mood:stern
* [I'm a traveller.]
    ~ met_guard = true
    Guard: {greet("traveller")} You may pass.
* [Bribe him ({gold} gold)]
    ~ gold = gold - 2
    Guard: Very well... #mood:greedy
- You have {gold} gold left.
-> END

=== elder ===
Elder: Take this, you will need it. #speaker:elder
~ give("potion")
-> END

=== function greet(name) ===
~ return "Hello, " + name + "."

=== function give(item) ===
~ return false
