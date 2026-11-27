// The cutscene the first time the knight enters the cellar (I-450), staged with kit-novel over the game world.
# show: knight left
Knight: So this is Grik's cellar. It smells of old cheese.
# sfx: sfx.rumble
# shake: 0.5
Something stirs in the dark.
# show: slime right transition=slide
Slime: Blub! Who comes down here to steal the old goblin's tonic?
* [I'm here to take it back.]
    Slime: Brave words. Fine, the chest is yours.
* [I'm just looking around.]
    Slime: A poor liar. Take the tonic and leave my cellar quiet.
- # flash: white 0.3
# hide: slime transition=fade
Knight: That was... odd.
-> END
