// German translation of test.ink (I-440): same knots, variables, choices and tags; only the text differs.
EXTERNAL give(item)
VAR gold = 3
VAR met_guard = false

Wache: Halt! Wer da? #speaker:guard #mood:stern
* [Ich bin ein Reisender.]
    ~ met_guard = true
    Wache: {greet("Reisender")} Ihr dürft passieren.
* [Bestechen ({gold} Gold)]
    ~ gold = gold - 2
    Wache: Nun gut... #mood:greedy
- Ihr habt noch {gold} Gold.
-> END

=== elder ===
Ältester: Nimm das, du wirst es brauchen. #speaker:elder
~ give("potion")
-> END

=== function greet(name) ===
~ return "Hallo, " + name + "."

=== function give(item) ===
~ return false
