// S-03 spike story: lines, tags, choices, a variable and an external function.
EXTERNAL greet(name)
VAR gold = 3
VAR met_guard = false

Guard: Halt! Who goes there? #speaker:guard #mood:stern
* [I'm a traveller.]
    ~ met_guard = true
    Guard: {greet("traveller")} You may pass. #speaker:guard
* [Bribe him ({gold} gold)]
    ~ gold = gold - 2
    Guard: Very well... #speaker:guard #mood:greedy
- You have {gold} gold left.
-> END

=== function greet(name) ===
~ return "Hello, " + name + "."
