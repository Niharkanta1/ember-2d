// demo-novel, chapter one (I-470): two characters and a choice that changes the ending.
// Staging tags are kit-novel's (F-023); "ending_..." is a plain game tag the demo records.
# bg: bg.meadow [transition=fade 1.0]
# music: music.meadow fade=1.5
It is a bright morning in the meadow.
# show: kero left
Kero: What a fine day for a walk!
# show: pip surprised right transition=slide
# sfx: sfx.pop
Pip: Kero! Kero! My cherries are gone!
# expr: kero surprised
Kero: Gone? The ones you picked for the festival?
Pip: All of them. I left the basket by the old tree, and now it is empty.
# expr: pip normal
Pip: Will you help me look for them?
* [Of course I will help.]
    # expr: kero happy
    Kero: Of course! Two pairs of eyes are better than one.
    -> search
* [Sorry, I am busy today.]
    # expr: kero normal
    Kero: I am sorry, Pip. I promised to help my uncle today.
    Pip: Oh... all right. Maybe I will find them myself.
    -> alone

=== search ===
# bg: bg.dusk [transition=fade 1.0]
# expr: kero normal
# wait: 0.5
The two friends search all afternoon.
# sfx: sfx.rustle
# shake: 0.3
Pip: Did you hear that? Something in the bushes!
# expr: pip surprised
# flash: white 0.2
Kero: Cherries! A whole trail of them, leading to... a sleepy hedgehog?
# expr: pip happy
Pip: He only took a few. There are plenty left for the festival!
# cg: cg.sunset
# music: music.sunset fade=2
Together they carry the basket home as the sun goes down.
# hidecg
# expr: kero happy
Kero: Same time tomorrow?
Pip: Same time tomorrow!
The End. Friends at sunset. #ending_together
-> END

=== alone ===
# hide: pip transition=fade
# bg: bg.night [transition=fade 1.5]
# stopmusic fade=2
# wait: 1.0
That evening, Kero sits alone by the pond.
Kero: I wonder if Pip ever found those cherries...
# sfx: sfx.pop
Far away, a sleepy hedgehog licks cherry juice from his paws.
The End. A quiet evening. #ending_alone
-> END
